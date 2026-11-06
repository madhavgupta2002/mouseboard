package dev.airmouse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

/** Owns the HID connection and sensors independently of the visible activity. */
public final class MouseBoardService extends Service implements SensorEventListener {
    private static final String CONNECT = "dev.airmouse.CONNECT";
    private static final String STOP = "dev.airmouse.STOP";
    private static final String PAUSE = "dev.airmouse.PAUSE";
    private static final String CHANNEL = "mouseboard_connection";
    private static final int NOTIFICATION = 1;
    final class LocalBinder extends Binder { MouseBoardService service() { return MouseBoardService.this; } }
    interface Listener { void changed(); }
    private final LocalBinder binder = new LocalBinder();
    private final MotionMath motion = new MotionMath();
    private final float[] quaternion = new float[4];
    private HidMouseConnection connection;
    private SensorManager sensors;
    private Sensor rotation;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences preferences;
    private Listener listener;
    private boolean foreground, sensing;
    boolean connected, connecting, paused, touchpad;
    String stateText = "Connect to your computer", deviceName = "";
    private long freezeUntil;
    private boolean touchBusy;

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("airmouse", MODE_PRIVATE);
        touchpad = preferences.getBoolean("touchpad", false);
        sensors = (SensorManager)getSystemService(SENSOR_SERVICE);
        rotation = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotation == null) rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MouseBoard:motion");
        BluetoothManager bluetooth = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        connection = new HidMouseConnection(this, bluetooth == null ? null : bluetooth.getAdapter(),
            (message, paired, busy) -> {
                stateText = message; connected = paired; connecting = busy;
                if (!paired) paused = false;
                updateSensors();
                if (!paired && !busy) endForeground();
                else updateNotification();
                notifyUi();
            });
        NotificationManager notifications = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
            "Bluetooth connection", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public IBinder onBind(Intent intent) { return binder; }
    void setListener(Listener value) { listener = value; if (value != null) value.changed(); }
    void removeListener(Listener value) { if (listener == value) listener = null; }
    private void notifyUi() { if (listener != null) listener.changed(); }

    void start(BluetoothDevice device) {
        // Called while the activity is visible; Android permits starting the FGS here.
        startForegroundService(new Intent(this, MouseBoardService.class).setAction(CONNECT)
            .putExtra("address", device.getAddress()));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (CONNECT.equals(intent.getAction())) {
            foreground = true;
            startForeground(NOTIFICATION, notification());
            try {
                BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
                BluetoothDevice device = manager.getAdapter().getRemoteDevice(intent.getStringExtra("address"));
                deviceName = HidMouseConnection.deviceName(device);
                paused = false; connected = false; connecting = true;
                stateText = "Connecting…";
                updateSensors(); notifyUi(); updateNotification();
                connection.start(device);
            } catch (RuntimeException error) {
                stop();
                stateText = "Could not start Bluetooth. Check Nearby devices permission.";
                notifyUi();
            }
        } else if (STOP.equals(intent.getAction())) stop();
        else if (PAUSE.equals(intent.getAction())) setActive(paused);
        return START_NOT_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, MouseBoardService.class).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 2,
            new Intent(this, MouseBoardService.class).setAction(PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.icon).setContentTitle("MouseBoard")
            .setContentText(connecting ? "Connecting to "+deviceName :
                (paused ? "Paused · " : "Connected · ")+deviceName)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, paused ? "Resume" : "Pause", pause).build())
            .addAction(new Notification.Action.Builder(null, "Disconnect", stop).build()).build();
    }
    private void updateNotification() {
        if (foreground) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
            .notify(NOTIFICATION, notification());
    }
    private void endForeground() {
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
        stopSelf();
    }
    void stop() {
        connection.stop();
        connected = connecting = paused = false;
        stateText = "Disconnected";
        updateSensors(); endForeground(); notifyUi();
    }
    void setActive(boolean value) {
        paused = !value;
        connection.setActive(value);
        touchBusy = false;
        resetMotion();
        updateSensors(); updateNotification(); notifyUi();
    }
    void setTouchpad(boolean value) {
        touchpad = value;
        preferences.edit().putBoolean("touchpad", value).apply();
        resetMotion(); updateSensors();
    }
    private void updateSensors() {
        boolean needed = connected && !paused && !touchpad && rotation != null;
        if (needed && !sensing) {
            wakeLock.acquire();
            sensing = sensors.registerListener(this, rotation, 16667);
            if (!sensing) wakeLock.release();
        } else if (!needed && sensing) {
            sensors.unregisterListener(this); sensing = false;
            if (wakeLock.isHeld()) wakeLock.release();
        }
        resetMotion();
    }
    void resetMotion() { motion.reset(); clearMotion(); freezeUntil = SystemClock.elapsedRealtime()+160; }
    void freezeMotion(long millis) { motion.reset(); clearMotion(); freezeUntil = SystemClock.elapsedRealtime()+millis; }
    void touchBusy(boolean value) {
        touchBusy = value;
        motion.reset();
        freezeUntil = SystemClock.elapsedRealtime()+160;
        // A finger-up must not discard the gesture's final queued movement.
        if (value) clearMotion();
    }
    void clearMotion() { connection.clearMotion(); }
    void move(double x, double y) { connection.move(x, y); }
    void button(String button, boolean down) { connection.button(button, down); }
    void click(String button) { connection.click(button); }
    void scroll(double steps) { connection.scroll(steps); }
    void scrollHorizontal(double steps) { connection.scrollHorizontal(steps); }
    void center() { resetMotion(); connection.center(); }
    KeyboardReports.Result edit(int backspaces, String text) { return connection.edit(backspaces, text); }
    KeyboardReports.Result navigate(boolean forward) {
        return connection.navigate(forward, preferences.getBoolean("macShortcuts", true));
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!connected || paused || touchpad) return;
        if (touchBusy || SystemClock.elapsedRealtime() < freezeUntil) { motion.reset(); return; }
        SensorManager.getQuaternionFromVector(quaternion, event.values);
        double gain = 1550*(.5+preferences.getInt("speed", 50)/100.0);
        double[] delta = motion.update(quaternion, event.timestamp/1e9,
            preferences.getBoolean("turnMode", true), gain);
        connection.move(delta[0], delta[1]);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public void onDestroy() {
        sensors.unregisterListener(this);
        if (wakeLock.isHeld()) wakeLock.release();
        connection.destroy(); listener = null;
        super.onDestroy();
    }
}
