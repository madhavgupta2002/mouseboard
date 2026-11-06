package dev.airmouse;

/** Trackpad gestures in density-independent pixels; no Android dependencies. */
final class TouchpadGestures {
    interface Output {
        void move(double x, double y);
        void click(String button);
        void button(String button, boolean down);
        void scroll(double x, double y);
        default void zoom(int steps) { }
        default void swipe(boolean horizontal, int direction) { }
    }
    static final long HOLD_MS = 420;
    private static final long DOUBLE_TAP_MS = 300;
    private static final int UNDECIDED = 0, SCROLL = 1, PINCH = 2;
    private final Output output;
    private final double slop;
    private boolean active, moved, multi, dragging, dimButtons, pointerStarted, secondTap, swiped;
    private double startX, startY, lastX, lastY, travel, startSpan, zoomAnchor;
    private double scale = 1.8, scrollDirection = 1, velocityX, velocityY;
    private double inertiaX, inertiaY, tapX, tapY;
    private long started, lastMotionTime, lastScrollTime, animationTime, lastTap = -1;
    private String button;
    private int fingers, maxFingers, multiKind;

    TouchpadGestures(Output output, double slop) { this.output = output; this.slop = slop; }
    void configure(double pointerScale, boolean reverseScroll) {
        scale = Math.max(.3, Math.min(5, pointerScale));
        scrollDirection = reverseScroll ? -1 : 1;
    }
    void begin(double x, double y, long now, double width, boolean dimButtons) {
        if (dragging) output.button(button, false);
        clear();
        stopMomentum();
        secondTap = !dimButtons && lastTap >= 0 && now >= lastTap &&
            now-lastTap <= DOUBLE_TAP_MS && Math.hypot(x-tapX, y-tapY) <= 40;
        lastTap = -1;
        active = true; fingers = maxFingers = 1;
        this.dimButtons = dimButtons;
        startX = lastX = x; startY = lastY = y;
        started = lastMotionTime = now;
        button = x < width/2 ? "left" : "right";
    }
    void fingers(int count, double x, double y) { fingers(count, x, y, 0, lastMotionTime+1); }
    void fingers(int count, double x, double y, double span, long now) {
        if (!active) return;
        boolean added = count > fingers;
        if (count > 1) {
            if (!multi || (count >= 3 && maxFingers < 3)) {
                startX = x; startY = y; travel = 0;
                startSpan = zoomAnchor = span;
                multiKind = UNDECIDED;
            }
            multi = true; secondTap = false;
            if (dragging) { output.button(button, false); dragging = false; moved = true; }
        }
        maxFingers = Math.max(maxFingers, count);
        fingers = count; lastX = x; lastY = y; lastMotionTime = now;
        if (added) velocityX = velocityY = 0;
    }
    void move(double x, double y, int count) { move(x, y, count, 0, lastMotionTime+16); }
    void move(double x, double y, int count, double span, long now) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(span)) return;
        if (!active || count != fingers) { fingers(count, x, y, span, now); return; }
        double dx = x-lastX, dy = y-lastY;
        long elapsed = now-lastMotionTime;
        lastX = x; lastY = y; lastMotionTime = now;
        if (multi) {
            if (maxFingers >= 3) {
                if (count < 3 || swiped) return;
                double totalX = x-startX, totalY = y-startY;
                if (Math.hypot(totalX, totalY) < 48) return;
                boolean horizontal = Math.abs(totalX) > Math.abs(totalY);
                output.swipe(horizontal, (horizontal ? totalX : totalY) > 0 ? 1 : -1);
                swiped = moved = true;
                return;
            }
            if (count < 2) return; // Never turn a finger-lift into pointer motion.
            travel += Math.hypot(dx, dy);
            if (multiKind == UNDECIDED) {
                if (startSpan > 0 && span > 0 &&
                        Math.abs(span-startSpan) > Math.max(slop, startSpan*.12) &&
                        Math.abs(span-startSpan) > 1.5*Math.hypot(x-startX, y-startY)) {
                    multiKind = PINCH;
                } else if (travel > slop) {
                    multiKind = SCROLL;
                    dx = x-startX; dy = y-startY;
                } else return;
            }
            moved = true;
            if (multiKind == PINCH) {
                if (span <= 0 || zoomAnchor <= 0) return;
                int steps = (int)(Math.log(span/zoomAnchor)/Math.log(1.15));
                steps = Math.max(-3, Math.min(3, steps));
                if (steps != 0) { output.zoom(steps); zoomAnchor *= Math.pow(1.15, steps); }
            } else emitScroll(dx/22, dy/22, elapsed, now);
        } else {
            double distance = Math.hypot(x-startX, y-startY);
            if (distance > slop) moved = true;
            if (secondTap && !dragging) {
                if (!moved) return;
                button = "left"; output.button(button, true); dragging = true;
            }
            if (dimButtons && !dragging) {
                if (!moved) return;
                emitScroll(dx/22, dy/22, elapsed, now);
            } else if (!dimButtons) {
                if (!pointerStarted) {
                    if (distance < .5) return;
                    dx = x-startX; dy = y-startY; pointerStarted = true;
                }
                output.move(dx*scale, dy*scale);
            }
        }
    }
    private void emitScroll(double x, double y, long elapsed, long now) {
        output.scroll(x*scrollDirection, y*scrollDirection);
        if (elapsed > 0 && elapsed <= 80) {
            double alpha = 1-Math.exp(-elapsed/30.0);
            velocityX += alpha*(x/elapsed-velocityX);
            velocityY += alpha*(y/elapsed-velocityY);
        } else velocityX = velocityY = 0;
        lastScrollTime = now;
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
        else if (!moved && now-started < HOLD_MS && maxFingers <= 2) {
            output.click(multi ? "right" : dimButtons ? button : "left");
            if (!multi && !dimButtons && !secondTap) { lastTap = now; tapX = lastX; tapY = lastY; }
        }
        if (moved && !dragging && maxFingers <= 2 && multiKind != PINCH &&
                lastScrollTime > 0 && now-lastScrollTime <= 60 &&
                (multi || dimButtons)) {
            inertiaX = Math.max(-.04, Math.min(.04, velocityX))*scrollDirection;
            inertiaY = Math.max(-.04, Math.min(.04, velocityY))*scrollDirection;
            animationTime = now;
        }
        clear();
    }
    boolean animate(long now) {
        if (active || Math.hypot(inertiaX, inertiaY) < .001) { stopMomentum(); return false; }
        long elapsed = now-animationTime;
        if (elapsed <= 0) return true;
        if (elapsed > 80) { stopMomentum(); return false; } // Never catch up after a suspended frame.
        double decay = Math.exp(-elapsed/150.0);
        output.scroll(inertiaX*150*(1-decay), inertiaY*150*(1-decay));
        inertiaX *= decay; inertiaY *= decay; animationTime = now;
        return true;
    }
    void cancel() {
        if (dragging) output.button(button, false);
        lastTap = -1; clear(); stopMomentum();
    }
    private void stopMomentum() { inertiaX = inertiaY = 0; }
    private void clear() {
        active = moved = multi = dragging = pointerStarted = secondTap = swiped = false;
        fingers = maxFingers = multiKind = 0;
        travel = velocityX = velocityY = 0;
        lastScrollTime = 0;
    }
}
