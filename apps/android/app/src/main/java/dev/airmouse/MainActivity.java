package dev.airmouse;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements SensorEventListener {
    private static final int BLACK = Color.BLACK, PANEL = Color.rgb(10, 10, 10);
    private static final int LINE = Color.rgb(38, 38, 38), TEXT = Color.rgb(245, 245, 245);
    private static final int MUTED = Color.rgb(145, 145, 145);
    private static final int BLUETOOTH_PERMISSION = 10, ENABLE_BLUETOOTH = 11;
    private SensorManager sensors;
    private Sensor rotation;
    private final MotionMath motion = new MotionMath();
    private final float[] quaternion = new float[4];
    private HidMouseConnection connection;
    private BluetoothAdapter adapter;
    private SharedPreferences preferences;
    private TextView status, keyboardHint;
    private Button connectButton, pauseButton, recenterButton, keyboardButton;
    private RemoteInput editor;
    private LinearLayout keyboardPanel;
    private ClickPad leftPad, rightPad;
    private boolean connected, connecting, paused, turnMode, scrolling, keyboardVisible;
    private long freezeUntil, lastInputError;
    private double gain;
    private String stateText = "Connect to your Mac";
    private String selectedMac = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        preferences = getSharedPreferences("airmouse", MODE_PRIVATE);
        gain = 1550*(.5 + preferences.getInt("speed", 50)/100.0);
        // The user-confirmed working mapping becomes the default for new and
        // existing installations once. Later explicit changes remain saved.
        if (preferences.getInt("motionModeDefaultsVersion", 0) < 1) {
            preferences.edit().putBoolean("turnMode", true)
                .putInt("motionModeDefaultsVersion", 1).apply();
        }
        turnMode = preferences.getBoolean("turnMode", true);
        sensors = (SensorManager)getSystemService(SENSOR_SERVICE);
        rotation = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotation == null) rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        connection = new HidMouseConnection(this, adapter, (message, paired, busy) -> {
            connected = paired; connecting = busy; stateText = message;
            if (!paired) {
                paused = false; scrolling = false;
                if (leftPad != null) { leftPad.release(); rightPad.release(); }
                if (editor != null) editor.resetContext();
            }
            resetMotion();
            refreshState();
        });
        buildUI();
        if (adapter == null) { stateText = "Bluetooth is unavailable"; refreshState(); }
    }

    private void buildUI() {
        LinearLayout root = column();
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(12), dp(8), dp(12), dp(10));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("MOUSEBOARD", 12, TEXT);
        brand.setLetterSpacing(.18f);
        brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(40), 1));
        connectButton = button("Connect", false);
        connectButton.setTextSize(12);
        connectButton.setOnClickListener(view -> connectOrDisconnect());
        header.addView(connectButton, new LinearLayout.LayoutParams(dp(96), dp(40)));
        Button settings = button("⋯", false);
        settings.setTextSize(24);
        settings.setContentDescription("Connection, sensitivity and motion settings");
        settings.setOnClickListener(view -> showSettings());
        LinearLayout.LayoutParams gear = new LinearLayout.LayoutParams(dp(44), dp(40));
        gear.leftMargin = dp(6);
        header.addView(settings, gear);
        root.addView(header);
        status = label(stateText, 11, MUTED);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(34)));

        LinearLayout controls = new LinearLayout(this);
        recenterButton = button("Recenter", true);
        recenterButton.setOnClickListener(view -> {
            editor.finishInput();
            resetMotion();
            leftPad.release(); rightPad.release();
            connection.center();
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        });
        pauseButton = button("Pause", false);
        pauseButton.setOnClickListener(view -> {
            if (!connected) return;
            leftPad.release(); rightPad.release();
            paused = !paused;
            connection.setActive(!paused);
            if (paused) hideKeyboard();
            resetMotion();
            refreshState();
        });
        keyboardButton = button("Keyboard", false);
        keyboardButton.setOnClickListener(view -> { if (keyboardVisible) hideKeyboard(); else showKeyboard(); });
        addEqual(controls, recenterButton, 44, false);
        addEqual(controls, pauseButton, 44, true);
        addEqual(controls, keyboardButton, 44, true);
        root.addView(controls);

        LinearLayout pads = new LinearLayout(this);
        pads.setMotionEventSplittingEnabled(true);
        leftPad = new ClickPad("LEFT", "left");
        rightPad = new ClickPad("RIGHT", "right");
        pads.addView(leftPad, new LinearLayout.LayoutParams(0, -1, 1));
        View wheel = scrollStrip();
        LinearLayout.LayoutParams strip = new LinearLayout.LayoutParams(dp(52), -1);
        strip.leftMargin = strip.rightMargin = dp(6);
        pads.addView(wheel, strip);
        pads.addView(rightPad, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams padArea = new LinearLayout.LayoutParams(-1, 0, 1);
        padArea.topMargin = dp(10);
        root.addView(pads, padArea);

        keyboardPanel = column();
        keyboardPanel.setBackground(outline(PANEL, 12));
        keyboardPanel.setPadding(dp(14), dp(8), dp(14), dp(8));
        editor = new RemoteInput(this, new RemoteInput.Sender() {
            public boolean edit(int backspaces, String text) {
                if (!connected || paused) return false;
                KeyboardReports.Result result = connection.edit(backspaces, text);
                if (result != KeyboardReports.Result.OK && SystemClock.elapsedRealtime()-lastInputError > 1500) {
                    lastInputError = SystemClock.elapsedRealtime();
                    toast(result == KeyboardReports.Result.UNSUPPORTED
                        ? "Use English text and punctuation. Emoji and other scripts are not supported yet."
                        : "Typing queue is full. Wait a moment and retry.");
                }
                return result == KeyboardReports.Result.OK;
            }
            public void activity() { freezeUntil = SystemClock.elapsedRealtime()+220; connection.clearMotion(); }
        });
        editor.setTextColor(TEXT);
        editor.setHintTextColor(MUTED);
        editor.setTextSize(14);
        editor.setHint("Type on your Mac…");
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setPadding(0, 0, 0, 0);
        editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        keyboardPanel.addView(editor, new LinearLayout.LayoutParams(-1, dp(40)));
        keyboardHint = label("Tap to open your phone keyboard\n\nClick a text field on your Mac first", 13, MUTED);
        keyboardHint.setGravity(Gravity.CENTER);
        keyboardHint.setLineSpacing(dp(4), 1);
        keyboardHint.setOnClickListener(view -> showKeyboard());
        keyboardPanel.addView(keyboardHint, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout.LayoutParams typingArea = new LinearLayout.LayoutParams(-1, 0, 1);
        typingArea.topMargin = dp(10);
        root.addView(keyboardPanel, typingArea);

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                root.setPadding(dp(12)+bars.left, dp(8)+bars.top, dp(12)+bars.right,
                    dp(10)+Math.max(bars.bottom, ime));
                keyboardVisibility(insets.isVisible(WindowInsets.Type.ime()));
                return insets;
            });
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Rect visible = new Rect();
                root.getWindowVisibleDisplayFrame(visible);
                keyboardVisibility(root.getRootView().getHeight()-visible.bottom > dp(160));
            });
        }
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        refreshState();
    }

    private void keyboardVisibility(boolean visible) {
        if (keyboardVisible == visible) return;
        keyboardVisible = visible;
        keyboardHint.setVisibility(visible ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams area = (LinearLayout.LayoutParams)keyboardPanel.getLayoutParams();
        area.height = visible ? dp(56) : 0;
        area.weight = visible ? 0 : 1;
        keyboardPanel.setLayoutParams(area);
        keyboardButton.setText(visible ? "Hide keys" : "Keyboard");
        resetMotion();
    }

    private void showKeyboard() {
        if (!connected || paused) { toast("Connect and resume MouseBoard first"); return; }
        editor.requestFocus();
        editor.post(() -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
            .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT));
    }
    private void hideKeyboard() {
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(editor.getWindowToken(), 0);
        editor.clearFocus();
    }

    private View scrollStrip() {
        TextView strip = label("↑\n\nSCROLL\n\n↓", 10, MUTED);
        strip.setGravity(Gravity.CENTER);
        strip.setBackground(outline(PANEL, 12));
        strip.setContentDescription("Scroll. Slide one finger up or down.");
        final float[] last = {0};
        strip.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scrolling = true; last[0] = event.getY(); resetMotion();
                    strip.setBackground(outline(Color.rgb(24,24,24), 12));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    connection.scroll((event.getY()-last[0])/dp(22));
                    last[0] = event.getY();
                    return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    scrolling = false; resetMotion();
                    strip.setBackground(outline(PANEL, 12));
                    return true;
                default: return true;
            }
        });
        return strip;
    }

    private final class ClickPad extends TextView {
        private final String mouseButton;
        private boolean held, touchClick;
        ClickPad(String title, String button) {
            super(MainActivity.this);
            mouseButton = button;
            setText(title+"\n\nHold + tilt to drag");
            setTextSize(14);
            setTextColor(TEXT);
            setGravity(Gravity.CENTER);
            setLineSpacing(dp(2), 1);
            setBackground(outline(PANEL, 12));
            setContentDescription(title+" mouse button. Tap to click; hold and tilt to drag.");
            setClickable(true);
            setFocusable(false);
        }
        void release() {
            if (held) connection.button(mouseButton, false);
            held = false;
            setBackground(outline(PANEL, 12));
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    editor.finishInput();
                    held = true;
                    connection.clearMotion();
                    freezeUntil = SystemClock.elapsedRealtime()+80;
                    connection.button(mouseButton, true);
                    setBackground(outline(Color.rgb(28,28,28), 12));
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    return true;
                case MotionEvent.ACTION_UP:
                    release();
                    freezeUntil = SystemClock.elapsedRealtime()+120;
                    motion.reset();
                    touchClick = true; performClick(); touchClick = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    release(); resetMotion(); return true;
                default: return true;
            }
        }
        @Override public boolean performClick() {
            super.performClick();
            if (!touchClick) { editor.finishInput(); connection.click(mouseButton); }
            return true;
        }
    }

    private void connectOrDisconnect() {
        if (connected || connecting) {
            leftPad.release(); rightPad.release(); hideKeyboard();
            connection.stop(); connected = connecting = paused = false;
            stateText = "Disconnected"; editor.resetContext(); refreshState(); return;
        }
        if (adapter == null) return;
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION); return;
        }
        if (!adapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH); return;
        }
        List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
        devices.sort((a,b) -> isComputer(a) != isComputer(b) ? (isComputer(a) ? -1 : 1)
            : HidMouseConnection.deviceName(a).compareToIgnoreCase(HidMouseConnection.deviceName(b)));
        if (devices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Pair your Mac")
                .setMessage("Pair in Bluetooth settings, then return to MouseBoard.")
                .setPositiveButton("Bluetooth settings", (d,w) -> openBluetooth())
                .setNegativeButton("Cancel", null).show(); return;
        }
        String[] names = new String[devices.size()];
        for (int i=0; i<devices.size(); i++) names[i] = HidMouseConnection.deviceName(devices.get(i));
        new AlertDialog.Builder(this).setTitle("Choose your Mac").setItems(names, (d,index) -> {
            selectedMac = names[index]; editor.resetContext(); paused = false; connecting = true;
            stateText = "Connecting…"; connection.start(devices.get(index)); refreshState();
        }).setNegativeButton("Cancel", null).show();
    }

    private boolean isComputer(BluetoothDevice device) {
        BluetoothClass kind = device.getBluetoothClass();
        return kind != null && kind.getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER;
    }
    private void refreshState() {
        if (status == null) return;
        status.setText(connected ? (paused ? "Paused" : "Connected · "+selectedMac) : stateText);
        connectButton.setText(connected ? "Disconnect" : connecting ? "Cancel" : "Connect");
        connectButton.setEnabled(adapter != null);
        pauseButton.setText(paused ? "Resume" : "Pause");
        pauseButton.setEnabled(connected);
        boolean ready = connected && !paused;
        recenterButton.setEnabled(ready);
        keyboardButton.setEnabled(ready);
        leftPad.setEnabled(ready); rightPad.setEnabled(ready);
        editor.setEnabled(ready);
        leftPad.setAlpha(ready ? 1 : .45f); rightPad.setAlpha(ready ? 1 : .45f);
    }

    private void showSettings() {
        LinearLayout panel = column();
        panel.setPadding(dp(24), dp(12), dp(24), dp(8));
        TextView speedLabel = label("", 14, TEXT);
        panel.addView(speedLabel);
        SeekBar speed = new SeekBar(this);
        speed.setMax(150); speed.setProgress(preferences.getInt("speed", 50));
        speedLabel.setText(String.format(Locale.US, "Sensitivity  %.1f×", gain/1550));
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                gain = 1550*(.5+value/100.0);
                preferences.edit().putInt("speed", value).apply();
                speedLabel.setText(String.format(Locale.US, "Sensitivity  %.1f×", gain/1550));
            }
            public void onStartTrackingTouch(SeekBar bar) { resetMotion(); }
            public void onStopTrackingTouch(SeekBar bar) { resetMotion(); }
        });
        panel.addView(speed);
        Switch mode = new Switch(this);
        mode.setText("Turn left/right (default)");
        mode.setTextSize(13); mode.setTextColor(TEXT); mode.setChecked(turnMode);
        mode.setOnCheckedChangeListener((v,checked) -> {
            turnMode = checked; preferences.edit().putBoolean("turnMode", checked).apply(); resetMotion();
        });
        panel.addView(mode);
        TextView note = label("Typing: set your Mac input source to ABC / US.\n"+
            "English text and punctuation; no emoji yet.\n\n"+
            "If the updated controls do not work, forget the phone on the computer, "+
            "then pair again with MouseBoard open.\n\nBluetooth only · v0.4.1", 12, MUTED);
        note.setPadding(0, dp(20), 0, 0);
        panel.addView(note);
        new AlertDialog.Builder(this).setTitle("Settings").setView(panel)
            .setPositiveButton("Done", null)
            .setNeutralButton("Bluetooth", (d,w) -> openBluetooth()).show();
    }

    private void openBluetooth() { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    private void resetMotion() {
        motion.reset(); connection.clearMotion(); freezeUntil = SystemClock.elapsedRealtime()+160;
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!connected || paused) return;
        if (scrolling || SystemClock.elapsedRealtime() < freezeUntil) { motion.reset(); return; }
        SensorManager.getQuaternionFromVector(quaternion, event.values);
        double[] delta = motion.update(quaternion, event.timestamp/1e9, turnMode, gain);
        connection.move(delta[0], delta[1]);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override protected void onResume() {
        super.onResume();
        if (rotation != null) sensors.registerListener(this, rotation, 16667);
        resetMotion();
    }
    @Override protected void onPause() {
        super.onPause();
        sensors.unregisterListener(this);
        leftPad.release(); rightPad.release(); connection.stop();
        connected = connecting = paused = scrolling = false;
        editor.resetContext();
        stateText = "Disconnected while app was in background"; refreshState();
    }
    @Override protected void onDestroy() { connection.destroy(); super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int request, String[] names, int[] result) {
        super.onRequestPermissionsResult(request, names, result);
        if (request != BLUETOOTH_PERMISSION) return;
        if (result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) connectOrDisconnect();
        else { stateText = "Allow Nearby devices in app permissions"; refreshState(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_BLUETOOTH && result == RESULT_OK) connectOrDisconnect();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private Button button(String text, boolean primary) {
        Button view = new Button(this);
        view.setText(text); view.setAllCaps(false); view.setTextSize(13);
        view.setTextColor(primary ? BLACK : TEXT);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(0); view.setMinimumHeight(0);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(outline(primary ? TEXT : PANEL, 10));
        return view;
    }
    private GradientDrawable outline(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color); background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), LINE);
        return background;
    }
    private void addEqual(LinearLayout parent, View child, int height, boolean margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1);
        if (margin) params.leftMargin = dp(6);
        parent.addView(child, params);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
