package dev.airmouse;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BLACK = Color.BLACK, PANEL = Color.rgb(10, 10, 10);
    private static final int LINE = Color.rgb(38, 38, 38), TEXT = Color.rgb(245, 245, 245);
    private static final int MUTED = Color.rgb(145, 145, 145);
    private static final int BLUETOOTH_PERMISSION = 10, ENABLE_BLUETOOTH = 11;
    private MouseBoardService connection;
    private BluetoothAdapter adapter;
    private SharedPreferences preferences;
    private TextView status, keyboardHint;
    private Button connectButton, pauseButton, recenterButton, keyboardButton, modeButton, dimButton, backButton, forwardButton;
    private RemoteInput editor;
    private LinearLayout keyboardPanel;
    private LinearLayout pads;
    private FrameLayout screen;
    private LinearLayout mainPanel, dimPanel;
    private TouchpadView touchpadView, dimTouchpad;
    private ClickPad leftPad, rightPad;
    private boolean connected, connecting, paused, turnMode, keyboardVisible, touchpadMode, dim, bound;
    private long lastInputError;
    private double gain;
    private String stateText = "Connect to your Mac";
    private String selectedMac = "";
    private final MouseBoardService.Listener serviceListener = () -> {
        connected = connection.connected; connecting = connection.connecting;
        paused = connection.paused; stateText = connection.stateText; selectedMac = connection.deviceName;
        touchpadMode = connection.touchpad;
        if (!connected || paused) {
            releaseTouches();
            editor.resetContext();
            if (dim) setDim(false);
        }
        updateMode();
        refreshState();
    };
    private final ServiceConnection serviceBinding = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            connection = ((MouseBoardService.LocalBinder)binder).service();
            connection.setListener(serviceListener);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            connection = null; connected = connecting = paused = false;
            stateText = "Bluetooth service stopped"; refreshState();
        }
    };

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
        touchpadMode = preferences.getBoolean("touchpad", false);
        BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        buildUI();
        bound = bindService(new Intent(this, MouseBoardService.class), serviceBinding, BIND_AUTO_CREATE);
        if (adapter == null) { stateText = "Bluetooth is unavailable"; refreshState(); }
    }

    private void buildUI() {
        LinearLayout root = column();
        mainPanel = root;
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(12), dp(8), dp(12), dp(10));
        screen = new FrameLayout(this);
        screen.setBackgroundColor(BLACK);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);

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
            releaseTouches();
            connection.center();
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        });
        pauseButton = button("Pause", false);
        pauseButton.setOnClickListener(view -> {
            if (!connected) return;
            releaseTouches();
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

        LinearLayout navigation = new LinearLayout(this);
        backButton = button("‹ Back", false);
        backButton.setOnClickListener(view -> navigate(false));
        forwardButton = button("Forward ›", false);
        forwardButton.setOnClickListener(view -> navigate(true));
        modeButton = button("", false);
        modeButton.setContentDescription("Switch between mouse buttons and touchpad");
        modeButton.setOnClickListener(view -> {
            releaseTouches(); touchpadMode = !touchpadMode;
            if (connection != null) connection.setTouchpad(touchpadMode);
            updateMode(); resetMotion();
        });
        dimButton = button("Dim", false);
        dimButton.setOnClickListener(view -> setDim(true));
        addEqual(navigation, backButton, 40, false);
        addEqual(navigation, forwardButton, 40, true);
        addEqual(navigation, modeButton, 40, true);
        addEqual(navigation, dimButton, 40, true);
        LinearLayout.LayoutParams navArea = new LinearLayout.LayoutParams(-1, dp(40));
        navArea.topMargin = dp(6);
        root.addView(navigation, navArea);

        pads = new LinearLayout(this);
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
        FrameLayout pointerArea = new FrameLayout(this);
        pointerArea.addView(pads, new FrameLayout.LayoutParams(-1, -1));
        touchpadView = newTouchpad();
        pointerArea.addView(touchpadView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(pointerArea, padArea);

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
            public void activity() { if (connection != null) connection.freezeMotion(220); }
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
        buildDimPanel();
        updateMode();
        refreshState();
    }

    private TouchpadView newTouchpad() {
        TouchpadView view = new TouchpadView(this, new TouchpadGestures.Output() {
            public void move(double x, double y) { if (connection != null) connection.move(x, y); }
            public void click(String button) { if (connection != null) connection.click(button); }
            public void button(String button, boolean down) { if (connection != null) connection.button(button, down); }
            public void scroll(double x, double y) {
                if (connection == null) return;
                connection.scrollHorizontal(x);
                connection.scroll(y);
            }
            public void zoom(int steps) {
                if (connection != null) reportGesture(connection.zoom(steps));
            }
            public void swipe(boolean horizontal, int direction) {
                if (connection != null) reportGesture(connection.workspace(horizontal, direction));
            }
        }, active -> {
            if (connection != null) {
                if (active) editor.finishInput();
                // In dim button mode a held press still permits tilt-to-drag.
                connection.touchBusy(active && !(dim && !touchpadMode));
            }
        });
        configureTouchpad(view);
        return view;
    }
    private void configureTouchpad(TouchpadView view) {
        view.configure(1.8*(.5+preferences.getInt("touchpadSpeed", 50)/100.0),
            preferences.getBoolean("reverseScroll", false));
    }
    private void reportGesture(KeyboardReports.Result result) {
        if (result != KeyboardReports.Result.OK && SystemClock.elapsedRealtime()-lastInputError > 1500) {
            lastInputError = SystemClock.elapsedRealtime();
            toast("Typing queue is busy. Retry the gesture in a moment.");
        }
    }
    private void buildDimPanel() {
        dimPanel = column(); dimPanel.setBackgroundColor(BLACK);
        dimPanel.setPadding(dp(12), dp(48), dp(12), dp(24));
        LinearLayout actions = new LinearLayout(this);
        Button exit = button("Exit dim", false);
        exit.setTextColor(MUTED);
        exit.setOnClickListener(view -> setDim(false));
        Button center = button("Recenter", false);
        center.setTextColor(MUTED);
        center.setOnClickListener(view -> { releaseTouches(); if (connection != null) connection.center(); });
        addEqual(actions, exit, 48, false); addEqual(actions, center, 48, true);
        dimPanel.addView(actions);
        dimTouchpad = newTouchpad();
        dimTouchpad.setBackgroundColor(BLACK);
        dimPanel.addView(dimTouchpad, new LinearLayout.LayoutParams(-1, 0, 1));
        dimPanel.setVisibility(View.GONE);
        screen.addView(dimPanel, new FrameLayout.LayoutParams(-1, -1));
    }
    private void setDim(boolean value) {
        if (value && (!connected || paused)) return;
        releaseTouches(); hideKeyboard(); dim = value;
        mainPanel.setVisibility(value ? View.GONE : View.VISIBLE);
        dimPanel.setVisibility(value ? View.VISIBLE : View.GONE);
        dimTouchpad.setDim(true, !touchpadMode);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = value ? .01f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attributes);
        getWindow().getDecorView().setSystemUiVisibility(value ?
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN : View.SYSTEM_UI_FLAG_VISIBLE);
        resetMotion();
    }
    private void updateMode() {
        modeButton.setText(touchpadMode ? "Buttons" : "Touchpad");
        pads.setVisibility(touchpadMode ? View.GONE : View.VISIBLE);
        touchpadView.setVisibility(touchpadMode ? View.VISIBLE : View.GONE);
    }
    private void releaseTouches() {
        if (leftPad != null) { leftPad.release(); rightPad.release(); }
        if (touchpadView != null) touchpadView.cancel();
        if (dimTouchpad != null) dimTouchpad.cancel();
        if (connection != null) connection.touchBusy(false);
    }
    private void navigate(boolean forward) {
        if (connection == null || !connected || paused) return;
        releaseTouches(); editor.finishInput(); resetMotion();
        if (connection.navigate(forward) != KeyboardReports.Result.OK) toast("Wait for typing to finish and retry.");
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
        releaseTouches();
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
            if (!connected || paused || connection == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    connection.touchBusy(true); last[0] = event.getY();
                    strip.setBackground(outline(Color.rgb(24,24,24), 12));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    connection.scroll((event.getY()-last[0])/dp(22));
                    last[0] = event.getY();
                    return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    connection.touchBusy(false);
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
            if (held && connection != null) connection.button(mouseButton, false);
            held = false;
            setBackground(outline(PANEL, 12));
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled() || connection == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    editor.finishInput();
                    held = true;
                    connection.clearMotion();
                    connection.freezeMotion(80);
                    connection.button(mouseButton, true);
                    setBackground(outline(Color.rgb(28,28,28), 12));
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    return true;
                case MotionEvent.ACTION_UP:
                    release();
                    connection.freezeMotion(120);
                    touchClick = true; performClick(); touchClick = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    release(); resetMotion(); return true;
                default: return true;
            }
        }
        @Override public boolean performClick() {
            super.performClick();
            if (!touchClick && connection != null) { editor.finishInput(); connection.click(mouseButton); }
            return true;
        }
    }

    private void connectOrDisconnect() {
        if (connection == null) return;
        if (connected || connecting) {
            releaseTouches(); hideKeyboard();
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
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
        }
        List<BluetoothDevice> devices = new ArrayList<>(adapter.getBondedDevices());
        devices.sort((a,b) -> isComputer(a) != isComputer(b) ? (isComputer(a) ? -1 : 1)
            : HidMouseConnection.deviceName(a).compareToIgnoreCase(HidMouseConnection.deviceName(b)));
        if (devices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Pair your computer")
                .setMessage("Pair in Bluetooth settings, then return to MouseBoard.")
                .setPositiveButton("Bluetooth settings", (d,w) -> openBluetooth())
                .setNegativeButton("Cancel", null).show(); return;
        }
        String[] names = new String[devices.size()];
        for (int i=0; i<devices.size(); i++) names[i] = HidMouseConnection.deviceName(devices.get(i));
        new AlertDialog.Builder(this).setTitle("Choose your computer").setItems(names, (d,index) -> {
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
        connectButton.setEnabled(adapter != null && connection != null);
        pauseButton.setText(paused ? "Resume" : "Pause");
        pauseButton.setEnabled(connected);
        boolean ready = connected && !paused;
        recenterButton.setEnabled(ready);
        keyboardButton.setEnabled(ready);
        backButton.setEnabled(ready); forwardButton.setEnabled(ready);
        modeButton.setEnabled(connection != null); dimButton.setEnabled(ready);
        touchpadView.setEnabled(ready); dimTouchpad.setEnabled(ready);
        leftPad.setEnabled(ready); rightPad.setEnabled(ready);
        editor.setEnabled(ready);
        leftPad.setAlpha(ready ? 1 : .45f); rightPad.setAlpha(ready ? 1 : .45f);
    }

    private void showSettings() {
        releaseTouches();
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
        TextView touchSpeedLabel = label("Touchpad speed", 14, TEXT);
        touchSpeedLabel.setPadding(0, dp(12), 0, 0);
        panel.addView(touchSpeedLabel);
        SeekBar touchSpeed = new SeekBar(this);
        touchSpeed.setMax(150); touchSpeed.setProgress(preferences.getInt("touchpadSpeed", 50));
        touchSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                preferences.edit().putInt("touchpadSpeed", value).apply();
                configureTouchpad(touchpadView); configureTouchpad(dimTouchpad);
            }
            public void onStartTrackingTouch(SeekBar bar) { releaseTouches(); }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        panel.addView(touchSpeed);
        Switch reverse = new Switch(this);
        reverse.setText("Reverse touchpad scrolling");
        reverse.setTextColor(TEXT); reverse.setTextSize(13);
        reverse.setChecked(preferences.getBoolean("reverseScroll", false));
        reverse.setOnCheckedChangeListener((v,checked) -> {
            releaseTouches(); preferences.edit().putBoolean("reverseScroll", checked).apply();
            configureTouchpad(touchpadView); configureTouchpad(dimTouchpad);
        });
        panel.addView(reverse);
        Switch mode = new Switch(this);
        mode.setText("Turn left/right (default)");
        mode.setTextSize(13); mode.setTextColor(TEXT); mode.setChecked(turnMode);
        mode.setOnCheckedChangeListener((v,checked) -> {
            turnMode = checked; preferences.edit().putBoolean("turnMode", checked).apply(); resetMotion();
        });
        panel.addView(mode);
        Switch host = new Switch(this);
        host.setText("Mac gesture shortcuts");
        host.setTextSize(13); host.setTextColor(TEXT);
        host.setChecked(preferences.getBoolean("macShortcuts", true));
        host.setOnCheckedChangeListener((v,checked) ->
            preferences.edit().putBoolean("macShortcuts", checked).apply());
        panel.addView(host);
        TextView note = label("Typing: set your Mac input source to ABC / US.\n"+
            "Turn off Mac gesture shortcuts when connected to Windows.\n\n"+
            "Touchpad: tap to click, double-tap to double-click. "+
            "Tap, then touch again and move to drag; holding also drags.\n\n"+
            "Two fingers scroll with momentum or tap to right-click. "+
            "Pinch zooms in apps that support the shortcut. "+
            "Three fingers swipe sideways between desktops; swipe up for windows, down for app windows "+
            "(on Windows, down shows the desktop).\n\n"+
            "Background control stays active until you disconnect. "+
            "Dim keeps touch working with the display barely lit. "+
            "A locked/off screen cannot receive taps.\n\n"+
            "Match scrolling to your computer with Reverse touchpad scrolling. "+
            "Horizontal scrolling uses Shift + wheel where supported. "+
            "Force Click and native trackpad-only gestures are not available.\n\nBluetooth only · v0.5.1", 12, MUTED);
        note.setPadding(0, dp(20), 0, 0);
        panel.addView(note);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(panel);
        new AlertDialog.Builder(this).setTitle("Settings").setView(scroller)
            .setPositiveButton("Done", null)
            .setNeutralButton("Bluetooth", (d,w) -> openBluetooth()).show();
    }

    private void openBluetooth() { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }
    private void resetMotion() {
        if (connection != null) connection.resetMotion();
    }
    @Override protected void onResume() {
        super.onResume();
        resetMotion();
    }
    @Override protected void onPause() {
        super.onPause();
        releaseTouches();
        if (dim) setDim(false);
        editor.resetContext();
    }
    @Override protected void onDestroy() {
        releaseTouches();
        if (connection != null) connection.removeListener(serviceListener);
        if (bound) unbindService(serviceBinding);
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (dim) setDim(false);
        else super.onBackPressed();
    }
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
