package dev.airmouse;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Bounded event log the user can copy or share from Settings → Diagnostics. */
final class DiagnosticLog {
    interface DeviceState { String describe(BluetoothDevice device); }
    private static final int MAX_LINES = 600;
    private static final long MAX_FILE = 256 * 1024;
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static File file;

    private DiagnosticLog() { }

    static synchronized void init(Context context) {
        if (file != null) return;
        file = new File(context.getFilesDir(), "mouseboard-log.txt");
        // Keep recent history across app restarts so a failed attempt can be reported later.
        try {
            if (file.isFile()) {
                for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    if (!line.isEmpty()) append(line);
                }
            }
        } catch (IOException ignored) { }
    }

    static void log(String message) {
        String line = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + "  " + message;
        Log.i("MouseBoard", message);
        synchronized (DiagnosticLog.class) {
            append(line);
            if (file == null) return;
            try {
                if (file.length() > MAX_FILE) rewrite();
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) { }
        }
    }

    static synchronized void clear() {
        lines.clear();
        if (file != null) file.delete();
    }

    private static void append(String line) {
        lines.addLast(line);
        while (lines.size() > MAX_LINES) lines.removeFirst();
    }

    private static void rewrite() throws IOException {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            for (String line : lines) out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    static String report(Context context, BluetoothAdapter adapter, String serviceState, DeviceState hid) {
        StringBuilder text = new StringBuilder("MouseBoard diagnostics\n");
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            text.append("App: ").append(version).append('\n');
        } catch (PackageManager.NameNotFoundException ignored) { }
        text.append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        text.append("Permissions: connect=").append(granted(context, Manifest.permission.BLUETOOTH_CONNECT))
            .append(" advertise=").append(granted(context, Manifest.permission.BLUETOOTH_ADVERTISE))
            .append(" notifications=").append(granted(context, Manifest.permission.POST_NOTIFICATIONS)).append('\n');
        text.append("Service: ").append(serviceState).append('\n');
        if (adapter == null) text.append("Bluetooth adapter: none\n");
        else {
            try {
                text.append("Bluetooth: enabled=").append(adapter.isEnabled())
                    .append(" scanMode=").append(scanMode(adapter.getScanMode())).append('\n');
                text.append("Paired devices:\n");
                for (BluetoothDevice device : adapter.getBondedDevices()) {
                    BluetoothClass kind = device.getBluetoothClass();
                    text.append("  • ").append(HidMouseConnection.deviceName(device))
                        .append(" [").append(device.getAddress()).append("] class=")
                        .append(kind == null ? "?" : majorClass(kind.getMajorDeviceClass()))
                        .append(" mouseLink=").append(hid == null ? "?" : hid.describe(device)).append('\n');
                }
            } catch (SecurityException denied) {
                text.append("Bluetooth details hidden: Nearby devices permission is not granted\n");
            }
        }
        text.append("\nRecent events (oldest first):\n");
        synchronized (DiagnosticLog.class) { for (String line : lines) text.append(line).append('\n'); }
        return text.toString();
    }

    private static String granted(Context context, String permission) {
        if (Build.VERSION.SDK_INT < 31 && !permission.equals(Manifest.permission.POST_NOTIFICATIONS)) return "n/a";
        if (Build.VERSION.SDK_INT < 33 && permission.equals(Manifest.permission.POST_NOTIFICATIONS)) return "n/a";
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "yes" : "NO";
    }

    static String state(int state) {
        switch (state) {
            case 0: return "disconnected";
            case 1: return "connecting";
            case 2: return "connected";
            case 3: return "disconnecting";
            default: return "unknown(" + state + ")";
        }
    }

    static String scanMode(int mode) {
        switch (mode) {
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE: return "discoverable";
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE: return "connectable (not discoverable)";
            case BluetoothAdapter.SCAN_MODE_NONE: return "not connectable";
            default: return "unknown(" + mode + ")";
        }
    }

    static String majorClass(int major) {
        switch (major) {
            case BluetoothClass.Device.Major.COMPUTER: return "computer";
            case BluetoothClass.Device.Major.PHONE: return "phone";
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "audio";
            case BluetoothClass.Device.Major.PERIPHERAL: return "peripheral";
            case BluetoothClass.Device.Major.WEARABLE: return "wearable";
            default: return "other(" + major + ")";
        }
    }
}
