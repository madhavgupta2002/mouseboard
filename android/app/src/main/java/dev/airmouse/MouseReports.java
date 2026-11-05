package dev.airmouse;

import java.util.ArrayDeque;

/** Relative pointer reports, including held buttons and a vertical wheel. */
final class MouseReports {
    private double x, y, wheel;
    private int requestedButtons, lastButtons;
    private final ArrayDeque<Integer> buttons = new ArrayDeque<>();

    synchronized void move(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        x = Math.max(-512, Math.min(512, x + dx));
        y = Math.max(-512, Math.min(512, y + dy));
    }
    synchronized void scroll(double steps) {
        if (Double.isFinite(steps)) wheel = Math.max(-64, Math.min(64, wheel + steps));
    }
    synchronized void button(String button, boolean down) {
        int mask = mask(button);
        if (mask == 0) return;
        int next = down ? requestedButtons | mask : requestedButtons & ~mask;
        if (next == requestedButtons) return;
        requestedButtons = next;
        if (buttons.size() >= 64) buttons.clear();
        buttons.add(next);
    }
    synchronized void click(String button) {
        if (buttons.size() > 60 || (requestedButtons & mask(button)) != 0) return;
        button(button, true);
        button(button, false);
    }
    private int mask(String button) { return "left".equals(button) ? 1 : "right".equals(button) ? 2 : 0; }

    synchronized byte[] next() {
        int dx = Math.max(-127, Math.min(127, (int)x));
        int dy = Math.max(-127, Math.min(127, (int)y));
        int dz = Math.max(-127, Math.min(127, (int)wheel));
        if (buttons.isEmpty() && dx == 0 && dy == 0 && dz == 0) return null;
        lastButtons = buttons.isEmpty() ? requestedButtons : buttons.removeFirst();
        x -= dx; y -= dy; wheel -= dz;
        return new byte[]{(byte)lastButtons, (byte)dx, (byte)dy, (byte)dz};
    }
    synchronized byte[] current() { return new byte[]{(byte)lastButtons, 0, 0, 0}; }
    synchronized void clearMotion() { x = y = wheel = 0; }
    synchronized void clear() { clearMotion(); buttons.clear(); requestedButtons = lastButtons = 0; }
}
