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
    }
    public static void main(String[] args) {
        Output o = new Output();
        TouchpadGestures g = new TouchpadGestures(o, 8);
        g.begin(90, 50, 0, 100, false);
        g.move(94, 52, 1);
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
        System.out.println(checks+" touchpad gesture checks passed.");
    }
}
