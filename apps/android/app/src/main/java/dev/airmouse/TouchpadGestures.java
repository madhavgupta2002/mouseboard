package dev.airmouse;

/** Gesture state independent of Android events, in density-independent pixels. */
final class TouchpadGestures {
    interface Output {
        void move(double x, double y);
        void click(String button);
        void button(String button, boolean down);
        void scroll(double x, double y);
    }
    static final long HOLD_MS = 420;
    private final Output output;
    private final double slop;
    private boolean active, moved, multi, dragging, dimButtons;
    private double startX, startY, lastX, lastY, travel;
    private long started;
    private String button;
    private int fingers;

    TouchpadGestures(Output output, double slop) { this.output = output; this.slop = slop; }
    void begin(double x, double y, long now, double width, boolean dimButtons) {
        cancel();
        active = true; fingers = 1;
        this.dimButtons = dimButtons;
        startX = lastX = x; startY = lastY = y;
        started = now; travel = 0;
        button = x < width/2 ? "left" : "right";
    }
    void fingers(int count, double x, double y) {
        if (!active) return;
        if (count > 1) {
            multi = true;
            if (dragging) { output.button(button, false); dragging = false; }
        }
        fingers = count; lastX = x; lastY = y;
    }
    void move(double x, double y, int count) {
        if (!active || count != fingers) { fingers(count, x, y); return; }
        double dx = x-lastX, dy = y-lastY;
        lastX = x; lastY = y;
        if (multi) {
            if (count < 2) return; // Do not jump after the first finger is lifted.
            travel += Math.hypot(dx, dy);
            if (!moved && travel <= slop) return;
            moved = true;
            output.scroll(dx/22, dy/22);
        } else {
            if (!moved && Math.hypot(x-startX, y-startY) <= slop) return;
            if (!moved) { dx = x-startX; dy = y-startY; }
            moved = true;
            if (dimButtons && !dragging) output.scroll(dx/22, dy/22);
            else if (!dimButtons) output.move(dx*1.8, dy*1.8);
        }
    }
    boolean hold(long now) {
        if (!active || multi || moved || dragging || now-started < HOLD_MS) return false;
        button = dimButtons ? button : "left";
        output.button(button, true); dragging = true;
        return true;
    }
    void end(long now) {
        if (!active) return;
        if (dragging) output.button(button, false);
        else if (!moved && now-started < HOLD_MS) output.click(multi ? "right" : dimButtons ? button : "left");
        clear();
    }
    void cancel() {
        if (dragging) output.button(button, false);
        clear();
    }
    private void clear() { active = moved = multi = dragging = false; fingers = 0; }
}
