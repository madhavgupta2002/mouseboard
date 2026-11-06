package dev.airmouse;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Android is the Bluetooth HID mouse; macOS is the standard HID host. */
final class HidMouseConnection {
    interface Listener { void changed(String message, boolean connected, boolean connecting); }
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MouseReports reports = new MouseReports();
    private final KeyboardReports keyboard = new KeyboardReports();
    private final Object outputLock = new Object();
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor();
    private volatile BluetoothHidDevice hid;
    private volatile BluetoothDevice target;
    private volatile boolean connected, active = true;
    private boolean centerPending;
    private double horizontalWheel;
    private volatile long generation;

    HidMouseConnection(Context context, BluetoothAdapter adapter, Listener listener) {
        this.context = context.getApplicationContext();
        this.adapter = adapter;
        this.listener = listener;
        sender.scheduleAtFixedRate(this::sendNext, 0, 16, TimeUnit.MILLISECONDS);
    }

    void start(BluetoothDevice device) {
        stop();
        final long id = generation;
        target = device;
        active = true;
        publish(id, "Starting Bluetooth mouse…", false, true);
        try {
            if (adapter == null || !adapter.isEnabled()) {
                fail(id, "Turn on Bluetooth, then select your Mac."); return;
            }
            boolean requested = adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    main.post(() -> {
                        if (id != generation) {
                            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy); return;
                        }
                        hid = (BluetoothHidDevice) proxy;
                        register(id);
                    });
                }
                @Override public void onServiceDisconnected(int profile) {
                    fail(id, "Bluetooth mouse service disconnected. Tap Connect to retry.");
                }
            }, BluetoothProfile.HID_DEVICE);
            if (!requested) {
                fail(id, "This phone did not expose the Bluetooth HID device profile."); return;
            }
            main.postDelayed(() -> {
                if (id == generation && !connected) {
                    fail(id, "Mac did not accept the mouse connection. Keep this app open, remove the phone from Mac Bluetooth settings, pair again, then retry.");
                }
            }, 20000);
        } catch (SecurityException denied) {
            fail(id, "Allow Nearby devices permission for MouseBoard.");
        }
    }

    private void register(final long id) {
        BluetoothHidDevice service = hid;
        if (service == null || id != generation) return;
        BluetoothHidDeviceAppSdpSettings settings = new BluetoothHidDeviceAppSdpSettings(
            "MouseBoard", "Motion mouse and keyboard", "MouseBoard",
            BluetoothHidDevice.SUBCLASS1_COMBO, HidDescriptor.BYTES.clone());
        try {
            boolean accepted = service.registerApp(settings, null, null, main::post, new BluetoothHidDevice.Callback() {
                @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                    if (id != generation) return;
                    if (!registered) {
                        fail(id, "The Bluetooth mouse app stopped. Close other Bluetooth keyboard/mouse apps and retry."); return;
                    }
                    BluetoothDevice device = target;
                    BluetoothHidDevice proxy = hid;
                    if (device == null || proxy == null) return;
                    try {
                        publish(id, "Connecting to " + deviceName(device) + "…", false, true);
                        int state = proxy.getConnectionState(device);
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            becameConnected(id, device);
                        } else if (state != BluetoothProfile.STATE_CONNECTING && !proxy.connect(device)) {
                            fail(id, "Mac refused the mouse connection. Keep MouseBoard open and pair the phone again in Mac Bluetooth settings.");
                        }
                    } catch (SecurityException denied) { fail(id, "Allow Nearby devices permission for MouseBoard."); }
                }
                @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
                    if (id != generation || !isTarget(device)) return;
                    if (state == BluetoothProfile.STATE_CONNECTED) becameConnected(id, device);
                    else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        fail(id, "Mac disconnected. Tap Connect to reconnect.");
                    } else if (state == BluetoothProfile.STATE_CONNECTING) {
                        publish(id, "Connecting to " + deviceName(device) + "…", false, true);
                    }
                }
                @Override public void onGetReport(BluetoothDevice device, byte type, byte reportId, int size) {
                    if (id != generation || !isTarget(device) || hid == null) return;
                    try {
                        byte[] value = reportId == HidDescriptor.MOUSE ? reports.current()
                            : reportId == HidDescriptor.KEYBOARD ? keyboard.current()
                            : reportId == HidDescriptor.CENTER ? HidDescriptor.CENTER_REPORT.clone() : null;
                        if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && value != null) {
                            hid.replyReport(device, type, reportId, value);
                        } else hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
                    } catch (SecurityException denied) { fail(id, "Bluetooth permission was revoked."); }
                }
                @Override public void onSetReport(BluetoothDevice device, byte type, byte reportId, byte[] data) {
                    if (id != generation || !isTarget(device) || hid == null) return;
                    try {
                        boolean led = type == BluetoothHidDevice.REPORT_TYPE_OUTPUT &&
                            reportId == HidDescriptor.KEYBOARD && data != null && data.length == 1;
                        if (led) keyboard.setCapsLock((data[0] & 2) != 0);
                        hid.reportError(device, led ? BluetoothHidDevice.ERROR_RSP_SUCCESS :
                            BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
                    }
                    catch (SecurityException denied) { fail(id, "Bluetooth permission was revoked."); }
                }
                @Override public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
                    if (id == generation && isTarget(device) && reportId == HidDescriptor.KEYBOARD &&
                            data != null && data.length == 1) keyboard.setCapsLock((data[0] & 2) != 0);
                }
                @Override public void onSetProtocol(BluetoothDevice device, byte protocol) {
                    // The composite descriptor uses report protocol (modern macOS).
                    if (id == generation && isTarget(device) &&
                            protocol != BluetoothHidDevice.PROTOCOL_REPORT_MODE && hid != null) {
                        try { hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ); }
                        catch (SecurityException denied) { fail(id, "Bluetooth permission was revoked."); }
                    }
                }
                @Override public void onVirtualCableUnplug(BluetoothDevice device) {
                    if (isTarget(device)) fail(id, "Mac removed the mouse. Pair the phone again before reconnecting.");
                }
            });
            if (!accepted) fail(id, "Could not register a Bluetooth mouse. Close other phone keyboard/mouse apps and retry.");
        } catch (SecurityException denied) { fail(id, "Allow Nearby devices permission for MouseBoard."); }
    }

    private void becameConnected(long id, BluetoothDevice device) {
        if (id != generation || !isTarget(device)) return;
        connected = true;
        reports.clear();
        keyboard.clear();
        publish(id, "Bluetooth connected · " + deviceName(device), true, false);
    }

    private boolean isTarget(BluetoothDevice device) {
        BluetoothDevice current = target;
        return current != null && current.equals(device);
    }

    void move(double x, double y) { if (connected && active) reports.move(x, y); }
    void click(String button) { if (connected && active) reports.click(button); }
    void button(String button, boolean down) { if (connected && active) reports.button(button, down); }
    void scroll(double steps) { if (connected && active) reports.scroll(steps); }
    void scrollHorizontal(double steps) {
        synchronized (outputLock) {
            if (connected && active && Double.isFinite(steps))
                horizontalWheel = Math.max(-64, Math.min(64, horizontalWheel+steps));
        }
    }
    void clearMotion() {
        synchronized (outputLock) { reports.clearMotion(); horizontalWheel = 0; }
    }
    KeyboardReports.Result navigate(boolean forward, boolean mac) {
        synchronized (outputLock) {
            return connected && active ? keyboard.navigate(forward, mac) : KeyboardReports.Result.FULL;
        }
    }
    KeyboardReports.Result edit(int backspaces, String text) {
        synchronized (outputLock) {
            return connected && active ? keyboard.edit(backspaces, text) : KeyboardReports.Result.FULL;
        }
    }
    KeyboardReports.Result key(int usage, int modifiers) {
        synchronized (outputLock) {
            return connected && active ? keyboard.key(usage, modifiers) : KeyboardReports.Result.FULL;
        }
    }
    void center() {
        synchronized (outputLock) {
            if (!connected || !active) return;
            reports.clear();
            horizontalWheel = 0;
            centerPending = true;
        }
    }

    void setActive(boolean value) {
        synchronized (outputLock) {
            active = value;
            reports.clear();
            keyboard.clear();
            horizontalWheel = 0;
            centerPending = false;
            releaseButtons();
        }
    }

    private void sendNext() {
        synchronized (outputLock) {
            BluetoothHidDevice proxy = hid;
            BluetoothDevice device = target;
            long id = generation;
            if (!connected || !active || proxy == null || device == null) return;
            try {
                if (centerPending) {
                    centerPending = false;
                    if (!proxy.sendReport(device, HidDescriptor.MOUSE, new byte[4])) {
                        fail(id, "Could not center the pointer. Reconnect and try again."); return;
                    }
                    for (byte[] position : HidDescriptor.centerReports()) {
                        if (!proxy.sendReport(device, HidDescriptor.CENTER, position)) {
                            fail(id, "Could not center the pointer. Reconnect and try again."); return;
                        }
                    }
                }
                byte[] report = reports.next();
                if (report != null && !proxy.sendReport(device, HidDescriptor.MOUSE, report)) {
                    fail(id, "Bluetooth could not send mouse input. Tap Connect to retry."); return;
                }
                byte[] keys = keyboard.next();
                if (keys != null && !proxy.sendReport(device, HidDescriptor.KEYBOARD, keys)) {
                    fail(id, "Bluetooth could not send keyboard input. Tap Connect to retry."); return;
                }
                // Shift+wheel is understood as horizontal scrolling by many desktop
                // applications. Serialize it only between complete keyboard edits.
                int horizontal = (int)horizontalWheel;
                if (horizontal != 0 && keyboard.isIdle()) {
                    byte[] shift = new byte[8]; shift[0] = 2;
                    byte[] wheel = reports.current(); wheel[3] = (byte)horizontal;
                    if (!proxy.sendReport(device, HidDescriptor.KEYBOARD, shift) ||
                        !proxy.sendReport(device, HidDescriptor.MOUSE, wheel) ||
                        !proxy.sendReport(device, HidDescriptor.KEYBOARD, new byte[8])) {
                        fail(id, "Bluetooth could not send scrolling. Tap Connect to retry."); return;
                    }
                    horizontalWheel -= horizontal;
                }
            } catch (RuntimeException error) { fail(id, "Bluetooth connection lost. Tap Connect to retry."); }
        }
    }

    private void releaseButtons() {
        BluetoothHidDevice proxy = hid;
        BluetoothDevice device = target;
        if (connected && proxy != null && device != null) {
            try {
                proxy.sendReport(device, HidDescriptor.MOUSE, new byte[4]);
                proxy.sendReport(device, HidDescriptor.KEYBOARD, new byte[8]);
            }
            catch (RuntimeException ignored) { }
        }
    }

    void stop() {
        BluetoothHidDevice proxy;
        BluetoothDevice device;
        synchronized (outputLock) {
            generation++;
            releaseButtons();
            connected = false;
            reports.clear();
            keyboard.clear();
            horizontalWheel = 0;
            centerPending = false;
            proxy = hid;
            device = target;
            hid = null;
            target = null;
        }
        if (proxy != null) {
            try {
                if (device != null) proxy.disconnect(device);
                proxy.unregisterApp();
            } catch (RuntimeException ignored) { }
            try { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy); }
            catch (RuntimeException ignored) { }
        }
    }

    void destroy() { stop(); sender.shutdownNow(); }

    private void fail(long id, String message) {
        main.post(() -> {
            if (id != generation) return;
            stop();
            listener.changed(message, false, false);
        });
    }

    private void publish(long id, String message, boolean paired, boolean connecting) {
        main.post(() -> { if (id == generation) listener.changed(message, paired, connecting); });
    }

    static String deviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "paired computer" : name;
        } catch (SecurityException denied) { return "paired computer"; }
    }
}
