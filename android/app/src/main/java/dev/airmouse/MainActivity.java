package dev.airmouse;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity implements SensorEventListener {
    private static final int BG = Color.rgb(12, 19, 28), CARD = Color.rgb(22, 33, 47);
    private static final int MINT = Color.rgb(136, 234, 196), TEXT = Color.rgb(243, 247, 251);
    private static final int MUTED = Color.rgb(164, 181, 199);
    private SensorManager sensors;
    private Sensor rotation;
    private final MotionMath motion = new MotionMath();
    private final float[] quaternion = new float[4];
    private HidMouseConnection connection;
    private BluetoothAdapter adapter;
    private SharedPreferences preferences;
    private TextView status, instructions, speedLabel, title, pairingHint, pairingHeading;
    private Button connectButton, pauseButton;
    private LinearLayout pairing;
    private AimView aim;
    private boolean connected, connecting, paused, turnMode;
    private int fingersDown;
    private long freezeUntil, lastDraw;
    private double gain = 1550;
    private String stateText = "Choose your paired Mac to begin";
    private String selectedMac = "";
    private static final int BLUETOOTH_PERMISSION = 10, ENABLE_BLUETOOTH = 11;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences("airmouse", MODE_PRIVATE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotation = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotation == null) rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        connection = new HidMouseConnection(this, adapter, (message, paired, busy) -> {
            connected = paired;
            connecting = busy;
            stateText = message;
            if (!paired) { paused = false; fingersDown = 0; }
            recenter(false);
            refreshState();
        });
        buildUI();
        if (rotation == null) {
            status.setText("No rotation sensor found on this phone.");
            connectButton.setEnabled(false);
        } else if (adapter == null) {
            status.setText("Bluetooth is not available on this phone.");
            connectButton.setEnabled(false);
        }
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(24));
        scroll.addView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(dp(24) + bars.left, dp(18) + bars.top, dp(24) + bars.right, dp(20) + bars.bottom);
                return insets;
            });
        }
        setContentView(scroll);
        TextView brand = label("↗  AIR MOUSE", 12, MINT);
        brand.setLetterSpacing(0.17f); brand.setTypeface(null, Typeface.BOLD);
        add(root, brand, 0);
        title = label("A little tilt.\nA lot of control.", 35, TEXT);
        title.setTypeface(null, Typeface.BOLD);
        title.setLineSpacing(0, 0.95f);
        add(root, title, 20);
        status = label(stateText, 13, MUTED);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(root, status, 12);

        pairing = new LinearLayout(this);
        pairing.setOrientation(LinearLayout.VERTICAL);
        pairing.setPadding(dp(18), dp(16), dp(18), dp(16));
        pairing.setBackground(background(CARD, 20));
        add(root, pairing, 22);
        pairingHeading = label("BLUETOOTH MOUSE", 11, MINT);
        add(pairing, pairingHeading, 0);
        connectButton = button("Connect over Bluetooth", true);
        connectButton.setOnClickListener(view -> connectOrDisconnect());
        add(pairing, connectButton, 14);
        pairingHint = label("Select your already-paired Mac. AirMouse acts\nas a standard Bluetooth mouse. No Wi-Fi needed.", 12, MUTED);
        add(pairing, pairingHint, 10);
        Button settings = button("Bluetooth settings", false);
        settings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        add(pairing, settings, 12);

        aim = new AimView();
        LinearLayout.LayoutParams aimLayout = new LinearLayout.LayoutParams(-1, dp(112));
        aimLayout.topMargin = dp(18);
        root.addView(aim, aimLayout);
        instructions = label("Hold screen-up near horizontal.\nTilt up/down and side to side.", 13, MUTED);
        instructions.setGravity(Gravity.CENTER);
        add(root, instructions, 0);
        LinearLayout controls = new LinearLayout(this);
        Button recenter = button("◎  Recenter", false);
        recenter.setOnClickListener(view -> recenter(true));
        pauseButton = button("Ⅱ  Pause", false);
        pauseButton.setOnClickListener(view -> {
            if (!connected) return;
            paused = !paused;
            fingersDown = 0;
            connection.setActive(!paused);
            recenter(false); refreshState();
        });
        controls.addView(recenter, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(52), 1);
        right.leftMargin = dp(10);
        controls.addView(pauseButton, right);
        add(root, controls, 18);

        LinearLayout pads = new LinearLayout(this);
        pads.addView(clickPad("LEFT", "Tap to click", "left"), new LinearLayout.LayoutParams(0, dp(128), 1));
        LinearLayout.LayoutParams rightPad = new LinearLayout.LayoutParams(0, dp(128), 1);
        rightPad.leftMargin = dp(12);
        pads.addView(clickPad("RIGHT", "Tap for menu", "right"), rightPad);
        add(root, pads, 14);
        speedLabel = label("Sensitivity · 1.0×", 12, MUTED);
        add(root, speedLabel, 22);
        SeekBar speed = new SeekBar(this);
        speed.setMax(150);
        int savedSpeed = preferences.getInt("speed", 50);
        speed.setProgress(savedSpeed);
        updateSpeed(savedSpeed);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                updateSpeed(value);
                preferences.edit().putInt("speed", value).apply();
            }
            public void onStartTrackingTouch(SeekBar bar) { fingersDown++; connection.clearMotion(); }
            public void onStopTrackingTouch(SeekBar bar) { fingersDown = Math.max(0, fingersDown-1); recenter(false); }
        });
        add(root, speed, 2);
        Switch mode = new Switch(this);
        mode.setText("Turn left/right instead of tilting");
        mode.setTextSize(12); mode.setTextColor(MUTED);
        turnMode = preferences.getBoolean("turnMode", false);
        mode.setChecked(turnMode);
        mode.setOnCheckedChangeListener((view, checked) -> {
            turnMode = checked;
            preferences.edit().putBoolean("turnMode", checked).apply();
            recenter(false);
            instructions.setText(checked ? "Hold screen-up. Tilt up/down.\nTurn left/right like a remote." :
                "Hold screen-up near horizontal.\nTilt up/down and side to side.");
        });
        add(root, mode, 4);
        instructions.setText(turnMode ? "Hold screen-up. Tilt up/down.\nTurn left/right like a remote." :
            "Hold screen-up near horizontal.\nTilt up/down and side to side.");
        add(root, label("BLUETOOTH ONLY  ·  NO ACCOUNT  ·  KEEP APP OPEN", 9, MUTED), 24);
        refreshState();
    }

    private void connectOrDisconnect() {
        if (connected || connecting) {
            connection.stop();
            connected = false; connecting = false; paused = false; fingersDown = 0;
            stateText = "Disconnected"; refreshState();
            return;
        }
        if (rotation == null || adapter == null) return;
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION);
            return;
        }
        if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH);
            return;
        }
        final List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
        devices.sort((a, b) -> {
            boolean computerA = isComputer(a), computerB = isComputer(b);
            if (computerA != computerB) return computerA ? -1 : 1;
            return HidMouseConnection.deviceName(a).compareToIgnoreCase(HidMouseConnection.deviceName(b));
        });
        if (devices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Pair your Mac first")
                .setMessage("Pair this phone with your Mac in Bluetooth settings, then return here and tap Connect.")
                .setPositiveButton("Bluetooth settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                .setNegativeButton("Cancel", null).show();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) names[i] = HidMouseConnection.deviceName(devices.get(i));
        new AlertDialog.Builder(this).setTitle("Choose your paired Mac")
            .setItems(names, (dialog, which) -> connectTo(devices.get(which)))
            .setNegativeButton("Cancel", null).show();
    }

    private boolean isComputer(BluetoothDevice device) {
        BluetoothClass kind = device.getBluetoothClass();
        return kind != null && kind.getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER;
    }

    private void connectTo(BluetoothDevice device) {
        selectedMac = HidMouseConnection.deviceName(device);
        connection.setActive(true);
        connecting = true;
        connection.start(device);
        stateText = "Starting Bluetooth mouse…";
        refreshState();
    }

    private void refreshState() {
        status.setText(paused && connected ? "Paused · tap Resume when ready" : stateText);
        status.setTextColor(connected && !paused ? MINT : MUTED);
        connectButton.setText(connected ? "Disconnect" : connecting ? "Cancel" : "Connect over Bluetooth");
        pauseButton.setText(paused ? "▶  Resume" : "Ⅱ  Pause");
        pauseButton.setEnabled(connected);
        pairingHint.setVisibility(connected ? View.GONE : View.VISIBLE);
        pairingHeading.setText(connected ? "CONNECTED TO " + selectedMac : "BLUETOOTH MOUSE");
        title.setText(connected ? "You're in control." : "A little tilt.\nA lot of control.");
        title.setTextSize(connected ? 28 : 35);
        aim.getLayoutParams().height = dp(connected ? 80 : 112);
        aim.requestLayout();
        aim.invalidate();
    }

    private void updateSpeed(int value) {
        double multiplier = 0.5 + value / 100.0;
        gain = 1550 * multiplier;
        speedLabel.setText(String.format(Locale.US, "Sensitivity · %.1f×", multiplier));
    }

    private void recenter(boolean feedback) {
        motion.reset();
        connection.clearMotion();
        freezeUntil = SystemClock.elapsedRealtime() + 160;
        if (aim != null) { aim.dx = aim.dy = 0; aim.invalidate(); }
        if (feedback) android.widget.Toast.makeText(this, "Neutral position reset", android.widget.Toast.LENGTH_SHORT).show();
    }

    private TextView clickPad(String title, String subtitle, String button) {
        TextView pad = label(title + "\n" + subtitle, 15, TEXT);
        pad.setGravity(Gravity.CENTER);
        pad.setLineSpacing(dp(8), 1);
        pad.setBackground(background(CARD, 20));
        pad.setContentDescription(button.equals("left") ? "Left mouse click" : "Right mouse click");
        pad.setClickable(true);
        pad.setFocusable(true);
        pad.setOnClickListener(view -> connection.click(button));
        pad.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fingersDown++;
                    connection.clearMotion();
                    pad.setBackground(background(Color.rgb(35, 65, 67), 20));
                    return true;
                case MotionEvent.ACTION_UP:
                    fingersDown = Math.max(0, fingersDown-1);
                    freezeUntil = SystemClock.elapsedRealtime() + 180;
                    motion.reset();
                    connection.clearMotion();
                    pad.setBackground(background(CARD, 20));
                    if (event.getX() >= 0 && event.getX() <= view.getWidth() && event.getY() >= 0 && event.getY() <= view.getHeight()) view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    fingersDown = Math.max(0, fingersDown-1);
                    recenter(false);
                    pad.setBackground(background(CARD, 20));
                    return true;
                default: return true;
            }
        });
        return pad;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!connected || paused) return;
        SensorManager.getQuaternionFromVector(quaternion, event.values);
        if (fingersDown > 0 || SystemClock.elapsedRealtime() < freezeUntil) { motion.reset(); return; }
        double[] delta = motion.update(quaternion, event.timestamp / 1e9, turnMode, gain);
        connection.move(delta[0], delta[1]);
        if (SystemClock.elapsedRealtime() - lastDraw > 33) {
            aim.dx = (float) delta[0]; aim.dy = (float) delta[1];
            aim.invalidate(); lastDraw = SystemClock.elapsedRealtime();
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override protected void onResume() {
        super.onResume();
        if (rotation != null) sensors.registerListener(this, rotation, 16667);
        recenter(false);
    }
    @Override protected void onPause() {
        super.onPause();
        sensors.unregisterListener(this);
        connection.stop();
        connected = false; connecting = false; paused = false; fingersDown = 0;
        stateText = "Disconnected while app was in background";
        refreshState();
    }
    @Override protected void onDestroy() { connection.destroy(); super.onDestroy(); }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != BLUETOOTH_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) connectOrDisconnect();
        else {
            stateText = "Allow Nearby devices in AirMouse app permissions to connect.";
            refreshState();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_BLUETOOTH && result == RESULT_OK) connectOrDisconnect();
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        return view;
    }
    private Button button(String title, boolean primary) {
        Button button = new Button(this);
        button.setText(title); button.setAllCaps(false); button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(primary ? BG : TEXT);
        button.setBackground(background(primary ? MINT : CARD, 13));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        return button;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private void add(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin); parent.addView(view, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class AimView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float dx, dy;
        AimView() { super(MainActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth()/2f, cy = getHeight()/2f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(Color.rgb(38, 56, 71));
            canvas.drawCircle(cx, cy, dp(40), paint); canvas.drawCircle(cx, cy, dp(23), paint);
            canvas.drawLine(cx-dp(53), cy, cx+dp(53), cy, paint);
            canvas.drawLine(cx, cy-dp(49), cx, cy+dp(49), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(connected && !paused ? MINT : MUTED);
            canvas.drawCircle(cx + Math.max(-dp(33), Math.min(dp(33), dx*2)),
                cy + Math.max(-dp(33), Math.min(dp(33), dy*2)), dp(6), paint);
        }
    }
}
