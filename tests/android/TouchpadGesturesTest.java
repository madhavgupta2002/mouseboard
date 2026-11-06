package dev.airmouse;

import java.util.ArrayList;
import java.util.List;

public final class TouchpadGesturesTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
        System.out.println("PASS: "+message);
    }
    private static final class Output implements TouchpadGestures.Output {
        final List<String> events = new ArrayList<>();
        double x, y, sx, sy;
        public void move(double dx, double dy) { x += dx; y += dy; events.add("move"); }
        public void click(String button) { events.add("click:"+button); }
        public void button(String button, boolean down) { events.add(button+":"+down); }
        public void scroll(double dx, double dy) { sx += dx; sy += dy; events.add("scroll"); }
        public void zoom(int steps) { events.add("zoom:"+steps); }
        public void swipe(boolean horizontal, int direction) { events.add("swipe:"+horizontal+":"+direction); }
    }
    public static void main(String[] args) {
        Output o = new Output();
        TouchpadGestures g = new TouchpadGestures(o, 8);
        g.begin(90, 50, 0, 100, false);
        g.move(90.2, 50.1, 1);
        g.end(100);
        check(o.events.toString().equals("[click:left]"), "touchpad tap is left-click on either half; jitter stays still");
        o.events.clear();
        g.begin(10, 10, 1000, 100, false);
        g.move(30, 40, 1);
        g.end(1100);
        check(o.x == 36 && o.y == 54 && o.events.toString().equals("[move]"),
            "one finger moves both axes without a trailing click");
        o.events.clear();
        g.begin(10, 10, 2000, 100, false);
        check(!g.hold(2419) && g.hold(2420) && !g.hold(2500), "hold starts dragging exactly once after the delay");
        g.move(30, 10, 1); g.end(2600);
        check(o.events.toString().equals("[left:true, move, left:false]"), "drag has balanced press and release around movement");
        o.events.clear();
        g.begin(10, 10, 3000, 100, false);
        g.hold(3420); g.cancel(); g.cancel(); g.end(3500);
        check(o.events.toString().equals("[left:true, left:false]"), "cancellation releases a held button only once");
        o.events.clear();
        g.begin(10, 10, 4000, 100, false);
        g.move(30, 10, 1);
        check(!g.hold(4420), "pointer movement prevents delayed drag activation");
        g.cancel();
        o.events.clear();
        double beforeX = o.x, beforeY = o.y;
        g.begin(10, 10, 5000, 100, false);
        g.fingers(2, 30, 30);
        check(!g.hold(5420), "second finger cancels long-press recognition");
        g.move(52, 74, 2);
        g.fingers(1, 200, 300);
        g.move(400, 500, 1);
        g.end(5500);
        check(o.sx == 1 && o.sy == 2 && o.x == beforeX && o.y == beforeY,
            "two fingers scroll both axes without pointer jumps when a finger lifts");
        check(o.events.toString().equals("[scroll]"), "scroll gesture never becomes a click");
        o.events.clear();
        g.begin(10, 10, 6000, 100, false);
        g.fingers(2, 30, 30); g.move(32, 32, 2); g.fingers(1, 10, 10); g.end(6150);
        check(o.events.toString().equals("[click:right]"), "two-finger tap produces exactly one right-click");
        o.events.clear();
        g.begin(10, 10, 7000, 100, false); g.hold(7420);
        g.fingers(2, 30, 30); g.end(7500);
        check(o.events.toString().equals("[left:true, left:false]"), "adding a finger to a held drag releases it safely");
        o.events.clear();
        g.begin(90, 10, 8000, 100, true); g.end(8100);
        g.begin(10, 10, 8200, 100, true); g.end(8300);
        check(o.events.toString().equals("[click:right, click:left]"), "dim button surface chooses left/right by screen half");
        o.events.clear();
        g.begin(90, 10, 9000, 100, true); g.move(112, 54, 1); g.end(9100);
        check(o.events.toString().equals("[scroll]"), "dim button surface swipes scroll instead of clicking");
        o.events.clear();
        g.begin(90, 10, 10000, 100, true); g.hold(10420); g.cancel();
        check(o.events.toString().equals("[right:true, right:false]"), "dim mode supports holding the right button");
        o.events.clear();
        g.begin(10, 10, 11000, 100, false); g.hold(11420);
        g.begin(90, 10, 12000, 100, false); g.end(12100);
        check(o.events.toString().equals("[left:true, left:false, click:left]"), "new gesture releases any interrupted drag");
        o.events.clear();
        g.begin(10, 10, 13000, 100, false); g.cancel(); g.move(40, 50, 1); g.end(13100);
        check(o.events.isEmpty(), "stale events after cancellation cannot send input");
        o.events.clear();
        g.begin(10, 10, 14000, 100, false); g.end(14050);
        g.begin(10, 10, 14120, 100, false); g.end(14170);
        check(o.events.toString().equals("[click:left, click:left]"), "double tap emits exactly two balanced clicks");
        o.events.clear();
        g.begin(10, 10, 15000, 100, false); g.end(15050);
        g.begin(10, 10, 15120, 100, false); g.move(35, 20, 1); g.end(15200);
        check(o.events.toString().equals("[click:left, left:true, move, left:false]"),
            "tap then move on the second touch drags immediately without a long-press delay");
        o.events.clear();
        g.begin(10, 10, 16000, 100, false); g.end(16050);
        g.begin(10, 10, 16500, 100, false); g.move(35, 20, 1); g.end(16600);
        check(o.events.toString().equals("[click:left, move]"), "expired double-tap window cannot start a drag");
        o.events.clear();
        beforeX = o.x;
        g.begin(10, 10, 17000, 100, false); g.move(11, 10, 1); g.cancel();
        check(Math.abs(o.x-beforeX-1.8) < .001, "fine pointer aiming responds below Android's scrolling slop");
        o.events.clear();
        g.begin(10, 10, 18000, 200, false);
        g.fingers(2, 50, 50, 100, 18005);
        g.move(50, 50, 2, 140, 18025);
        g.move(50, 50, 2, 140, 18040);
        g.end(18060);
        check(o.events.toString().equals("[zoom:2]"), "pinch zoom emits steps without scrolling or repeating at rest");
        check(!g.animate(18076), "pinch never produces scroll momentum");
        o.events.clear();
        g.begin(10, 10, 19000, 200, false);
        g.fingers(2, 50, 50, 100, 19005);
        g.move(50, 50, 2, 70, 19025); g.end(19060);
        check(o.events.toString().equals("[zoom:-2]"), "pinching inward zooms out");
        o.events.clear();
        g.begin(10, 10, 20000, 200, false);
        g.fingers(2, 50, 50, 100, 20005);
        g.move(50, 72, 2, 101, 20021);
        g.move(50, 94, 2, 140, 20037); g.end(20050);
        check(o.events.toString().equals("[scroll, scroll]"), "finger spacing changes during scrolling do not unexpectedly zoom");
        o.events.clear();
        g.begin(10, 10, 21000, 200, false);
        g.fingers(3, 50, 50, 0, 21005);
        g.move(-10, 50, 3, 0, 21025);
        g.move(-80, 50, 3, 0, 21040);
        g.fingers(2, 5, 5, 50, 21050);
        g.move(5, 80, 2, 50, 21065); g.end(21080);
        check(o.events.toString().equals("[swipe:true:-1]"),
            "three-finger desktop swipe fires once without scrolling after a finger lifts");
        o.events.clear();
        g.begin(10, 10, 22000, 200, false); g.fingers(3, 50, 50, 0, 22005);
        g.move(50, -10, 3, 0, 22025); g.end(22050);
        check(o.events.toString().equals("[swipe:false:-1]"), "three-finger upward gesture opens the window overview");
        o.events.clear();
        g.begin(10, 10, 23000, 200, false); g.fingers(3, 50, 50, 0, 23005); g.end(23050);
        check(o.events.isEmpty(), "three-finger tap cannot trigger a stray left or right click");
        o.events.clear();
        g.begin(10, 10, 24000, 200, false); g.fingers(2, 50, 50, 100, 24005);
        g.move(50, 72, 2, 100, 24021); g.move(50, 94, 2, 100, 24037);
        g.fingers(1, 50, 94, 0, 24040); g.end(24042);
        double beforeScroll = o.sy;
        check(g.animate(24058) && o.sy > beforeScroll, "lifting scrolling fingers preserves velocity and starts momentum");
        double firstMomentum = o.sy-beforeScroll;
        beforeScroll = o.sy; g.animate(24074);
        check(o.sy-beforeScroll < firstMomentum, "momentum decays instead of accelerating");
        g.begin(10, 10, 24080, 200, false);
        check(!g.animate(24090), "new touch stops momentum immediately");
        g.cancel();
        g.configure(3.6, true);
        beforeX = o.x; beforeScroll = o.sy;
        g.begin(10, 10, 25000, 200, false); g.move(20, 10, 1); g.cancel();
        check(Math.abs(o.x-beforeX-36) < .001, "pointer sensitivity setting scales movement");
        g.begin(10, 10, 26000, 200, false); g.fingers(2, 50, 50, 100, 26005);
        g.move(50, 72, 2, 100, 26021); g.end(26040);
        check(o.sy < beforeScroll, "reverse-scrolling setting reverses finger scroll");
        beforeScroll = o.sy; g.animate(26056);
        check(o.sy < beforeScroll, "reverse-scrolling setting also applies to momentum");
        g.cancel();
        check(!g.animate(26072), "cancellation removes residual momentum");
        g.configure(1.8, false);
        g.begin(10, 10, 27000, 200, false); g.fingers(2, 50, 50, 100, 27005);
        g.move(50, 72, 2, 100, 27021); g.end(27040);
        beforeScroll = o.sy;
        check(!g.animate(28000) && o.sy == beforeScroll, "suspended animation cannot cause a delayed scroll jump");
        System.out.println(checks+" touchpad gesture checks passed.");
    }
}
