package dev.airmouse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Full touch surface; a two-finger gesture always wins over pointer movement. */
final class TouchpadView extends View {
    interface Activity { void touching(boolean active); }
    private final TouchpadGestures gestures;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Activity activity;
    private final float density;
    private boolean dim, dimButtons;
    private final Runnable hold = this::hold;
    private void hold() {
        if (gestures.hold(android.os.SystemClock.uptimeMillis())) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }
    TouchpadView(Context context, TouchpadGestures.Output output, Activity activity) {
        super(context);
        this.activity = activity;
        density = getResources().getDisplayMetrics().density;
        gestures = new TouchpadGestures(output, ViewConfiguration.get(context).getScaledTouchSlop()/density);
        setClickable(true); setFocusable(true);
        setContentDescription("Touchpad. Tap to click, hold to drag. Two fingers scroll or tap to right-click.");
        setBackgroundColor(Color.rgb(10, 10, 10));
    }
    void setDim(boolean value, boolean buttons) { dim = value; dimButtons = value && buttons; invalidate(); }
    void cancel() { removeCallbacks(hold); gestures.cancel(); activity.touching(false); }
    @Override public void setEnabled(boolean enabled) { if (!enabled) cancel(); super.setEnabled(enabled); invalidate(); }
    @Override protected void onDetachedFromWindow() { cancel(); super.onDetachedFromWindow(); }
    private double coordinate(MotionEvent event, boolean x, int skipped) {
        double total = 0; int count = 0;
        for (int i=0; i<event.getPointerCount(); i++) {
            if (i == skipped) continue;
            total += x ? event.getX(i) : event.getY(i); count++;
        }
        return count == 0 ? 0 : total/count/density;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int count = event.getPointerCount();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                activity.touching(true);
                gestures.begin(event.getX()/density, event.getY()/density,
                    event.getEventTime(), getWidth()/density, dimButtons);
                postDelayed(hold, TouchpadGestures.HOLD_MS);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(hold);
                gestures.fingers(count, coordinate(event, true, -1), coordinate(event, false, -1));
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                gestures.fingers(count-1, coordinate(event, true, event.getActionIndex()),
                    coordinate(event, false, event.getActionIndex()));
                return true;
            case MotionEvent.ACTION_MOVE:
                gestures.move(coordinate(event, true, -1), coordinate(event, false, -1), count);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold); gestures.end(event.getEventTime()); activity.touching(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancel(); return true;
            default: return true;
        }
    }
    @Override public boolean performClick() {
        super.performClick();
        if (isEnabled()) { // Accessibility click, separate from the gesture recognizer.
            gestures.begin(0, 0, 0, 1, false); gestures.end(1);
        }
        return true;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(isEnabled() ? Color.rgb(dim ? 62 : 145, dim ? 62 : 145, dim ? 62 : 145) : Color.DKGRAY);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(13*density);
        float y = getHeight()/2f;
        if (dimButtons) {
            canvas.drawLine(getWidth()/2f, 32*density, getWidth()/2f, getHeight()-32*density, paint);
            canvas.drawText("LEFT", getWidth()/4f, y, paint);
            canvas.drawText("RIGHT", getWidth()*3/4f, y, paint);
            canvas.drawText("Swipe to scroll · hold + tilt to drag", getWidth()/2f, y+32*density, paint);
        } else {
            canvas.drawText("TOUCHPAD", getWidth()/2f, y-28*density, paint);
            canvas.drawText("Tap to click · hold to drag", getWidth()/2f, y+4*density, paint);
            canvas.drawText("Two fingers: scroll / tap for right-click", getWidth()/2f, y+32*density, paint);
        }
    }
}
