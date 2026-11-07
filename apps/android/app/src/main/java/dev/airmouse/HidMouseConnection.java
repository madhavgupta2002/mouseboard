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

/** Android is the Bluetooth HID mouse; the computer (Mac or Windows) is the standard HID host. */
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
    private volatile boolean pairingMode;

    HidMouseConnection(Context context, BluetoothAdapter adapter, Listener listener) {
        this.context = context.getApplicationContext();
        this.adapter = adapter;
        this.listener = listener;
        sender.scheduleAtFixedRate(this::sendNext, 0, 16, TimeUnit.MILLISECONDS);
    }

    /** Starts the HID mouse. A null device waits for any computer to pair and connect. */
    void start(BluetoothDevice device) {
        stop();
        final long id = generation;
        target = device;
        active = true;
        final boolean pairing = device == null;
        pairingMode = pairing;
        DiagnosticLog.log(pairing ? "START pairing mode: registering mouse, waiting for a computer"
            : "START connect to " + describe(device));
        publish(id, pairing ? "Preparing pairing mode…" : "Starting Bluetooth mouse…", false, true);
        try {
            if (adapter == null || !adapter.isEnabled()) {
                fail(id, "Bluetooth is off. Turn on Bluetooth, then tap Connect."); return;
            }
            boolean requested = adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    main.post(() -> {
                        DiagnosticLog.log("HID profile proxy ready (attempt " + id + ")");
                        if (id != generation) {
                            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy); return;
                        }
                        hid = (BluetoothHidDevice) proxy;
                        register(id);
                    });
                }
                @Override public void onServiceDisconnected(int profile) {
                    DiagnosticLog.log("HID profile proxy disconnected by Android");
                    fail(id, "Android stopped the Bluetooth mouse service. Tap Connect to retry. [E1]");
                }
            }, BluetoothProfile.HID_DEVICE);
            DiagnosticLog.log("getProfileProxy(HID_DEVICE) returned " + requested);
            if (!requested) {
                fail(id, "This phone does not offer the Bluetooth HID device profile. [E2]"); return;
            }
            main.postDelayed(() -> {
                if (id != generation || connected) return;
                BluetoothDevice current = target;
                String detail = current == null ? "no computer connected" : "state=" + stateOf(current);
                DiagnosticLog.log("TIMEOUT waiting for computer (" + detail + ")");
                fail(id, pairing
                    ? "No computer connected. On the PC open Bluetooth → Add device and pick this phone while MouseBoard is in pairing mode. [E3]"
                    : "The computer did not accept the mouse link (" + detail + "). Remove this phone from the PC's Bluetooth list, then use ⋯ → Pair new computer. [E4]");
            }, pairing ? 180000 : 25000);
        } catch (SecurityException denied) {
            DiagnosticLog.log("SecurityException in start: " + denied.getMessage());
            fail(id, "Allow Nearby devices permission for MouseBoard. [E5]");
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
                    DiagnosticLog.log("onAppStatusChanged registered=" + registered +
                        " plugged=" + (pluggedDevice == null ? "none" : describe(pluggedDevice)));
                    if (id != generation) return;
                    if (!registered) {
                        fail(id, "Android unregistered the Bluetooth mouse. Close other Bluetooth keyboard/mouse apps and retry. [E6]"); return;
                    }
                    BluetoothHidDevice proxy = hid;
                    if (proxy == null) return;
                    BluetoothDevice device = target;
                    try {
                        logConnectedHosts(proxy);
                        if (device == null) {
                            // Pairing mode: the computer initiates. Adopt one that is already linked.
                            java.util.List<BluetoothDevice> linked = proxy.getConnectedDevices();
                            if (!linked.isEmpty()) { target = linked.get(0); becameConnected(id, target); return; }
                            publish(id, "Pairing mode · on the PC choose Bluetooth → Add device → " + adapterName(), false, true);
                            return;
                        }
                        publish(id, "Connecting to " + deviceName(device) + "…", false, true);
                        int state = proxy.getConnectionState(device);
                        DiagnosticLog.log("Mouse link state before connect: " + DiagnosticLog.state(state));
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            becameConnected(id, device);
                        } else if (state != BluetoothProfile.STATE_CONNECTING) {
                            boolean started = proxy.connect(device);
                            DiagnosticLog.log("connect(" + describe(device) + ") returned " + started);
                            if (!started) fail(id, "Android refused to start the mouse link to " + deviceName(device) +
                                ". Remove the phone on the PC, then use ⋯ → Pair new computer. [E7]");
                        }
                    } catch (SecurityException denied) { fail(id, "Allow Nearby devices permission for MouseBoard. [E5]"); }
                }
                @Override public void onConnectionStateChanged(BluetoothDevice device, int state) {
                    DiagnosticLog.log("onConnectionStateChanged " + describe(device) + " → " + DiagnosticLog.state(state) +
                        (isTarget(device) ? "" : " (not selected computer)"));
                    if (id != generation) return;
                    if (!isTarget(device)) {
                        // The computer connected on its own (common on Windows after pairing).
                        // Accept it unless we are already in use with another computer.
                        if (connected || state != BluetoothProfile.STATE_CONNECTED &&
                                state != BluetoothProfile.STATE_CONNECTING) return;
                        DiagnosticLog.log("Accepting mouse link started by " + describe(device));
                        target = device;
                    }
                    if (state == BluetoothProfile.STATE_CONNECTED) becameConnected(id, device);
                    else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        if (pairingMode && !connected) {
                            // Windows often opens and closes the link while it finishes pairing.
                            DiagnosticLog.log("Pairing mode: link closed before use; still waiting");
                            target = null;
                            publish(id, "Pairing mode · waiting for the PC to finish connecting…", false, true);
                            return;
                        }
                        fail(id, connected ? deviceName(device) + " disconnected. Tap Connect to reconnect. [E8]"
                            : deviceName(device) + " closed the mouse link right away. On the PC remove this phone from Bluetooth, then use ⋯ → Pair new computer. [E9]");
                    } else if (state == BluetoothProfile.STATE_CONNECTING) {
                        publish(id, "Connecting to " + deviceName(device) + "…", false, true);
                    }
                }
                @Override public void onGetReport(BluetoothDevice device, byte type, byte reportId, int size) {
                    DiagnosticLog.log("onGetReport type=" + type + " id=" + reportId);
                    if (id != generation || !isTarget(device) || hid == null) return;
                    try {
                        byte[] value = reportId == HidDescriptor.MOUSE ? reports.current()
                            : reportId == HidDescriptor.KEYBOARD ? keyboard.current()
                            : reportId == HidDescriptor.CENTER ? HidDescriptor.CENTER_REPORT.clone() : null;
                        if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && value != null) {
                            hid.replyReport(device, type, reportId, value);
                        } else hid.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
                    } catch (SecurityException denied) { fail(id, "Bluetooth permission was revoked. [E5]"); }
                }
                @Override public void onSetReport(BluetoothDevice device, byte type, byte reportId, byte[] data) {
                    DiagnosticLog.log("onSetReport type=" + type + " id=" + reportId);
                    if (id != generation || !isTarget(device) || hid == null) return;
                    try {
                        boolean led = type == BluetoothHidDevice.REPORT_TYPE_OUTPUT &&
                            reportId == HidDescriptor.KEYBOARD && data != null && data.length == 1;
                        if (led) keyboard.setCapsLock((data[0] & 2) != 0);
                        hid.reportError(device, led ? BluetoothHidDevice.ERROR_RSP_SUCCESS :
                            BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
                    }
                    catch (SecurityException denied) { fail(id, "Bluetooth permission was revoked. [E5]"); }
                }
                @Override public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
                    if (id == generation && isTarget(device) && reportId == HidDescriptor.KEYBOARD &&
                            data != null && data.length == 1) keyboard.setCapsLock((data[0] & 2) != 0);
                }
                @Override public void onSetProtocol(BluetoothDevice device, byte protocol) {
                    DiagnosticLog.log("onSetProtocol " + (protocol == BluetoothHidDevice.PROTOCOL_REPORT_MODE ? "report" : "boot"));
                    // The composite descriptor uses report protocol.
                    if (id == generation && isTarget(device) &&
                            protocol != BluetoothHidDevice.PROTOCOL_REPORT_MODE && hid != null) {
                        try { hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ); }
                        catch (SecurityException denied) { fail(id, "Bluetooth permission was revoked. [E5]"); }
                    }
                }
                @Override public void onVirtualCableUnplug(BluetoothDevice device) {
                    DiagnosticLog.log("onVirtualCableUnplug " + describe(device));
                    if (isTarget(device)) fail(id, deviceName(device) + " removed the mouse. Use ⋯ → Pair new computer to pair again. [E10]");
                }
            });
            DiagnosticLog.log("registerApp returned " + accepted);
            if (!accepted) fail(id, "Could not register the Bluetooth mouse. Close other phone keyboard/mouse apps, toggle Bluetooth off/on and retry. [E11]");
        } catch (SecurityException denied) { fail(id, "Allow Nearby devices permission for MouseBoard. [E5]"); }
    }

    private void becameConnected(long id, BluetoothDevice device) {
        if (id != generation || !isTarget(device)) return;
        connected = true;
        reports.clear();
        keyboard.clear();
        DiagnosticLog.log("CONNECTED as mouse+keyboard to " + describe(device));
        publish(id, "Bluetooth connected · " + deviceName(device), true, false);
    }

    private boolean isTarget(BluetoothDevice device) {
        BluetoothDevice current = target;
        return current != null && current.equals(device);
    }

    String targetName() { BluetoothDevice device = target; return device == null ? "" : deviceName(device); }
    String targetAddress() { BluetoothDevice device = target; return device == null ? null : device.getAddress(); }

    /** Mouse-link state for diagnostics; "?" when the HID proxy is not open. */
    String stateOf(BluetoothDevice device) {
        BluetoothHidDevice proxy = hid;
        if (proxy == null) return "inactive";
        try { return DiagnosticLog.state(proxy.getConnectionState(device)); }
        catch (RuntimeException error) { return "error " + error.getClass().getSimpleName(); }
    }

    private void logConnectedHosts(BluetoothHidDevice proxy) {
        try {
            StringBuilder names = new StringBuilder();
            for (BluetoothDevice device : proxy.getConnectedDevices()) names.append(describe(device)).append(' ');
            DiagnosticLog.log("Mouse links already open: " + (names.length() == 0 ? "none" : names.toString().trim()));
        } catch (RuntimeException ignored) { }
    }

    private String adapterName() {
        try { String name = adapter.getName(); return name == null ? "this phone" : name; }
        catch (SecurityException denied) { return "this phone"; }
    }

    static String describe(BluetoothDevice device) {
        if (device == null) return "none";
        String address = device.getAddress();
        // Keep the vendor prefix and last byte; enough to tell devices apart in a shared log.
        String shortAddress = address == null || address.length() < 17 ? String.valueOf(address)
            : address.substring(0, 8) + ":…:" + address.substring(15);
        return "\"" + deviceName(device) + "\" " + shortAddress;
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
    KeyboardReports.Result zoom(int steps, boolean mac) {
        synchronized (outputLock) {
            return connected && active ? keyboard.zoom(steps, mac) : KeyboardReports.Result.FULL;
        }
    }
    KeyboardReports.Result workspace(boolean horizontal, int direction, boolean mac) {
        synchronized (outputLock) {
            return connected && active ? keyboard.workspace(horizontal, direction, mac) : KeyboardReports.Result.FULL;
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
                    fail(id, "Bluetooth could not send mouse input; the computer may have dropped the link. Tap Connect to retry. [E13]"); return;
                }
                byte[] keys = keyboard.next();
                if (keys != null && !proxy.sendReport(device, HidDescriptor.KEYBOARD, keys)) {
                    fail(id, "Bluetooth could not send keyboard input. Tap Connect to retry. [E13]"); return;
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
                        fail(id, "Bluetooth could not send scrolling. Tap Connect to retry. [E13]"); return;
                    }
                    horizontalWheel -= horizontal;
                }
            } catch (RuntimeException error) {
                DiagnosticLog.log("sendReport threw " + error);
                fail(id, "Bluetooth connection lost (" + error.getClass().getSimpleName() + "). Tap Connect to retry. [E12]");
            }
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
        if (hid != null || target != null) DiagnosticLog.log("STOP mouse link" + (connected ? " (was connected)" : ""));
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
            DiagnosticLog.log("FAIL " + message);
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
