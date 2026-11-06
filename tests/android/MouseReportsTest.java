package dev.airmouse;

import java.util.Arrays;

public final class MouseReportsTest {
    private static int checks;
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
        System.out.println("PASS: " + name);
    }
    private static void report(MouseReports queue, int buttons, int x, int y, String name) {
        check(Arrays.equals(queue.next(), new byte[]{(byte)buttons, (byte)x, (byte)y, 0}), name);
    }
    private static int axis(byte[] report, int offset) {
        return (report[offset] & 255) | ((report[offset + 1] & 255) << 8);
    }
    private static final class AbsoluteHost {
        // Like IOHIDElementPrivate::processReport, ignore unchanged absolute values.
        // Relative motion changes the cursor but leaves this absolute cache intact.
        int absoluteX = 16384, absoluteY = 16384, cursorX = 16384, cursorY = 16384;
        boolean receive(byte[] report) {
            int x = axis(report, 0), y = axis(report, 2);
            if (absoluteX == x && absoluteY == y) return false;
            absoluteX = cursorX = x;
            absoluteY = cursorY = y;
            return true;
        }
    }
    public static void main(String[] args) throws java.io.IOException {
        MouseReports q = new MouseReports();
        check(q.next() == null, "idle mouse sends no movement");
        q.move(.4, -.4); check(q.next() == null, "fractional movement is retained");
        q.move(.7, -.7); report(q, 0, 1, -1, "fractions accumulate without losing motion");
        q.clear(); q.move(200, -200);
        report(q, 0, 127, -127, "large movement is split into signed-byte reports");
        report(q, 0, 73, -73, "large movement remainder is preserved");
        check(q.next() == null, "movement drains exactly");
        q.click("left");
        report(q, 1, 0, 0, "left button presses");
        check(q.current()[0] == 1, "GET_REPORT reflects held button");
        report(q, 0, 0, 0, "left button releases on the next frame");
        q.click("right");
        report(q, 2, 0, 0, "right button presses");
        report(q, 0, 0, 0, "right button releases");
        q.click("left"); q.click("left");
        report(q, 1, 0, 0, "double-click first press");
        report(q, 0, 0, 0, "double-click first release");
        report(q, 1, 0, 0, "double-click second press");
        report(q, 0, 0, 0, "double-click second release");
        q.move(10, 20); q.click("right"); q.clearMotion();
        report(q, 2, 0, 0, "clearing motion preserves pending click");
        q.clear();
        check(q.next() == null && q.current()[0] == 0, "stop clears motion and button state");
        q.move(Double.NaN, 2); q.move(2, Double.POSITIVE_INFINITY);
        check(q.next() == null, "nonfinite movement is ignored");
        for (int i = 0; i < 100; i++) q.click("left");
        int count = 0;
        byte[] last = null, value;
        while ((value = q.next()) != null) { last = value; count++; }
        check(count <= 64 && last != null && last[0] == 0, "overflow remains bounded and ends with release");
        q.button("left", true);
        report(q, 1, 0, 0, "holding a pad presses the mouse button immediately");
        q.move(30, -20);
        report(q, 1, 30, -20, "movement retains the held button for dragging");
        q.move(10, 15);
        report(q, 1, 10, 15, "dragging stays held across multiple reports");
        q.button("left", false);
        report(q, 0, 0, 0, "lifting the finger releases a drag");
        q.button("left", true); q.button("right", true);
        report(q, 1, 0, 0, "left and right presses preserve transition order");
        report(q, 3, 0, 0, "both pads can be held together");
        q.button("left", false);
        report(q, 2, 0, 0, "releasing left preserves held right");
        q.button("right", false);
        report(q, 0, 0, 0, "releasing right releases all buttons");
        q.scroll(.4); check(q.next() == null, "small wheel movement accumulates");
        q.scroll(1.8);
        check(Arrays.equals(q.next(), new byte[]{0,0,0,2}), "scroll strip sends vertical wheel steps");
        q.scroll(-2.4);
        check(Arrays.equals(q.next(), new byte[]{0,0,0,-2}), "wheel reverses direction");
        q.clearMotion(); q.button("left", true); q.scroll(2);
        check(Arrays.equals(q.next(), new byte[]{1,0,0,2}), "scrolling preserves held buttons");
        q.clear();
        check(q.next() == null && Arrays.equals(q.current(), new byte[4]), "cancel clears movement, wheel and held buttons");
        int size = 0, reportCount = 0, page = 0, collectionDepth = 0, id = 0;
        int[] input = new int[4], output = new int[4];
        boolean mouse = false, relativeXY = false, absoluteXY = false, keyboard = false;
        byte[] d = HidDescriptor.BYTES;
        for (int i = 0; i < d.length;) {
            int prefix = d[i++] & 255;
            int length = prefix & 3;
            if (length == 3) length = 4;
            int data = 0;
            for (int n = 0; n < length; n++) data |= (d[i++] & 255) << (8*n);
            if (prefix == 0x05) { page = data; if (page == 7) keyboard = true; }
            if (prefix == 0x09 && page == 1 && data == 2) mouse = true;
            if (prefix == 0x75) size = data;
            if (prefix == 0x95) reportCount = data;
            if (prefix == 0x85) id = data;
            if (prefix == 0x81) {
                input[id] += size * reportCount;
                if (id == HidDescriptor.MOUSE && data == 6 && size == 8 && reportCount == 3) relativeXY = true;
                if (id == HidDescriptor.CENTER && data == 2 && size == 16 && reportCount == 2) absoluteXY = true;
            }
            if (prefix == 0x91) output[id] += size * reportCount;
            if (prefix == 0xa1) collectionDepth++;
            if (prefix == 0xc0) collectionDepth--;
        }
        check(mouse && keyboard, "HID descriptor advertises mouse and keyboard");
        check(input[1] == 32 && input[2] == 64 && input[3] == 32 && collectionDepth == 0,
            "descriptor matches the three report formats and balances collections");
        check(output[2] == 8, "keyboard descriptor accepts one-byte host LED state");
        check(relativeXY && absoluteXY, "motion and recenter use independent relative and absolute axes");
        byte[] center = HidDescriptor.CENTER_REPORT;
        check(((center[0]&255) | ((center[1]&255)<<8)) == 16384 &&
            ((center[2]&255) | ((center[3]&255)<<8)) == 16384,
            "recenter targets the midpoint of both absolute axes");
        AbsoluteHost host = new AbsoluteHost();
        host.cursorX += 400; host.cursorY -= 250;
        check(!host.receive(center) && host.cursorX != 16384 && host.cursorY != 16384,
            "regression: a repeated absolute midpoint is ignored after relative movement");
        for (int press = 1; press <= 5; press++) {
            byte[][] sequence = HidDescriptor.centerReports();
            check(sequence.length == 2 && axis(sequence[0], 0) == 16383 &&
                axis(sequence[0], 2) == 16383, "recenter " + press + " changes both cached absolute axes");
            check(host.receive(sequence[0]) && host.receive(sequence[1]),
                "recenter " + press + " produces two fresh absolute events");
            check(Arrays.equals(sequence[1], center) && host.cursorX == 16384 && host.cursorY == 16384,
                "recenter " + press + " finishes at the exact midpoint");
            host.cursorX += press * 100; host.cursorY -= press * 70;
        }
        byte[][] modified = HidDescriptor.centerReports();
        modified[0][0] = 0; modified[1][0] = 1;
        check(axis(HidDescriptor.centerReports()[0], 0) == 16383 && axis(center, 0) == 16384,
            "a send buffer cannot corrupt future recenter sequences");
        System.out.println(checks + " Bluetooth report checks passed.");
        if (args.length == 1) {
            java.nio.file.Files.write(java.nio.file.Paths.get(args[0]), HidDescriptor.BYTES);
        }
    }
}
