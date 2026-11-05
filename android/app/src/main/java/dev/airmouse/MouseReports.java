package dev.airmouse;

import java.util.ArrayDeque;

/** Standard three-byte Bluetooth mouse reports: buttons, relative X, relative Y. */
final class MouseReports {
    // No report ID, keyboard usages, absolute coordinates, or vendor protocol.
    // This format also matches the three-byte boot mouse protocol.
    static final byte[] DESCRIPTOR = {
        0x05, 0x01,       // Generic Desktop
        0x09, 0x02,       // Mouse
        (byte) 0xa1, 0x01,
        0x09, 0x01,       // Pointer
        (byte) 0xa1, 0x00,
        0x05, 0x09,       // Buttons
        0x19, 0x01,
        0x29, 0x03,
        0x15, 0x00,
        0x25, 0x01,
        (byte) 0x95, 0x03,
        0x75, 0x01,
        (byte) 0x81, 0x02,
        (byte) 0x95, 0x01,
        0x75, 0x05,
        (byte) 0x81, 0x03, // Padding
        0x05, 0x01,
        0x09, 0x30,       // X
        0x09, 0x31,       // Y
        0x15, (byte) 0x81,
        0x25, 0x7f,
        0x75, 0x08,
        (byte) 0x95, 0x02,
        (byte) 0x81, 0x06, // Data, variable, relative
        (byte) 0xc0,
        (byte) 0xc0
    };

    private double x, y;
    private int lastButtons;
    private final ArrayDeque<Integer> buttons = new ArrayDeque<>();

    synchronized void move(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        x = Math.max(-512, Math.min(512, x + dx));
        y = Math.max(-512, Math.min(512, y + dy));
    }

    synchronized void click(String button) {
        int mask;
        if ("left".equals(button)) mask = 1;
        else if ("right".equals(button)) mask = 2;
        else return;
        if (buttons.size() > 28) return;
        // Always queue a matching release. Do not merge consecutive clicks.
        buttons.add(mask);
        buttons.add(0);
    }

    synchronized byte[] next() {
        int dx = Math.max(-127, Math.min(127, (int) x));
        int dy = Math.max(-127, Math.min(127, (int) y));
        if (buttons.isEmpty() && dx == 0 && dy == 0) return null;
        int mask = buttons.isEmpty() ? 0 : buttons.removeFirst();
        x -= dx;
        y -= dy;
        lastButtons = mask;
        return new byte[]{(byte) mask, (byte) dx, (byte) dy};
    }

    synchronized byte[] current() { return new byte[]{(byte) lastButtons, 0, 0}; }
    synchronized void clearMotion() { x = y = 0; }
    synchronized void clear() { clearMotion(); buttons.clear(); lastButtons = 0; }
}
