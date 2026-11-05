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
        check(Arrays.equals(queue.next(), new byte[]{(byte)buttons, (byte)x, (byte)y}), name);
    }
    public static void main(String[] args) {
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
        check(count <= 30 && last != null && last[0] == 0, "overflow remains bounded and ends with release");
        int size = 0, reportCount = 0, bits = 0, page = 0, collectionDepth = 0;
        boolean mouse = false, relativeXY = false, reportId = false, keyboard = false;
        byte[] d = MouseReports.DESCRIPTOR;
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
            if (prefix == 0x85) reportId = true;
            if (prefix == 0x81) { bits += size * reportCount; if (data == 6 && size == 8 && reportCount == 2) relativeXY = true; }
            if (prefix == 0xa1) collectionDepth++;
            if (prefix == 0xc0) collectionDepth--;
        }
        check(mouse && !keyboard, "HID descriptor advertises only a mouse");
        check(bits == 24 && !reportId && collectionDepth == 0, "descriptor matches three-byte boot-compatible reports");
        check(relativeXY, "descriptor uses relative X/Y axes");
        System.out.println(checks + " Bluetooth report checks passed.");
    }
}
