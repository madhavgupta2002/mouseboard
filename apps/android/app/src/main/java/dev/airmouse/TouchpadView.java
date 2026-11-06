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
    private final Runnable momentum = this::momentum;
    private void momentum() {
        if (isEnabled() && gestures.animate(android.os.SystemClock.uptimeMillis()))
            postOnAnimation(momentum);
    }
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
        setContentDescription("Touchpad. Tap to click, tap then drag or hold to drag. "+
            "Two fingers scroll, pinch to zoom, or tap to right-click. Three fingers switch desktops or show windows.");
        setBackgroundColor(Color.rgb(10, 10, 10));
    }
    void setDim(boolean value, boolean buttons) { dim = value; dimButtons = value && buttons; invalidate(); }
    void configure(double speed, boolean reverseScroll) { gestures.configure(speed, reverseScroll); }
    void cancel() { removeCallbacks(hold); removeCallbacks(momentum); gestures.cancel(); activity.touching(false); }
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
    private double span(MotionEvent event) {
        if (event.getPointerCount() != 2) return 0;
        return Math.hypot(event.getX(1)-event.getX(0), event.getY(1)-event.getY(0))/density;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int count = event.getPointerCount();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(momentum);
                getParent().requestDisallowInterceptTouchEvent(true);
                activity.touching(true);
                gestures.begin(event.getX()/density, event.getY()/density,
                    event.getEventTime(), getWidth()/density, dimButtons);
                postDelayed(hold, TouchpadGestures.HOLD_MS);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(hold);
                gestures.fingers(count, coordinate(event, true, -1), coordinate(event, false, -1),
                    span(event), event.getEventTime());
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                gestures.fingers(count-1, coordinate(event, true, event.getActionIndex()),
                    coordinate(event, false, event.getActionIndex()), 0, event.getEventTime());
                return true;
            case MotionEvent.ACTION_MOVE:
                // Batched samples preserve slow motion and scroll velocity.
                for (int h=0; h<event.getHistorySize(); h++) {
                    double x = 0, y = 0;
                    for (int p=0; p<count; p++) {
                        x += event.getHistoricalX(p, h); y += event.getHistoricalY(p, h);
                    }
                    double historicalSpan = count == 2 ? Math.hypot(
                        event.getHistoricalX(1,h)-event.getHistoricalX(0,h),
                        event.getHistoricalY(1,h)-event.getHistoricalY(0,h))/density : 0;
                    gestures.move(x/count/density, y/count/density, count,
                        historicalSpan, event.getHistoricalEventTime(h));
                }
                gestures.move(coordinate(event, true, -1), coordinate(event, false, -1), count,
                    span(event), event.getEventTime());
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);
                gestures.move(event.getX()/density, event.getY()/density, 1, 0, event.getEventTime());
                gestures.end(event.getEventTime()); activity.touching(false);
                postOnAnimation(momentum);
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
            canvas.drawText("TOUCHPAD", getWidth()/2f, y-42*density, paint);
            paint.setTextSize(12*density);
            canvas.drawText("Tap to click · tap then drag to move items", getWidth()/2f, y-12*density, paint);
            canvas.drawText("Two fingers: scroll · pinch · right-click", getWidth()/2f, y+14*density, paint);
            canvas.drawText("Three fingers: desktops / windows", getWidth()/2f, y+40*density, paint);
        }
    }
}
