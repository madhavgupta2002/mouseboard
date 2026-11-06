package dev.airmouse;

import java.util.ArrayDeque;

/** US/ABC-layout keyboard reports, queued atomically as press/release pairs. */
final class KeyboardReports {
    static final int ENTER = 0x28, BACKSPACE = 0x2a, TAB = 0x2b, ESCAPE = 0x29;
    enum Result { OK, UNSUPPORTED, FULL }
    private static final int LIMIT = 1024;
    private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
    private byte[] current = new byte[8];
    private boolean capsLock;

    synchronized Result edit(int backspaces, String text) {
        if (backspaces < 0 || text == null) return Result.UNSUPPORTED;
        for (int i = 0; i < text.length(); i++) if (usage(text.charAt(i)) == 0) return Result.UNSUPPORTED;
        long needed = 2L*(backspaces + (long)text.length());
        if (needed > LIMIT-queue.size()) return Result.FULL;
        for (int i = 0; i < backspaces; i++) enqueue(BACKSPACE, 0);
        for (int i = 0; i < text.length(); i++) {
            int key = usage(text.charAt(i));
            enqueue(key & 255, key >>> 8);
        }
        return Result.OK;
    }
    synchronized Result key(int usage, int modifiers) {
        if (usage <= 0 || usage > 0x65 || modifiers < 0 || modifiers > 255) return Result.UNSUPPORTED;
        if (queue.size() > LIMIT-2) return Result.FULL;
        enqueue(usage, modifiers);
        return Result.OK;
    }
    synchronized Result navigate(boolean forward, boolean mac) {
        return key(mac ? (forward ? 0x30 : 0x2f) : (forward ? 0x4f : 0x50), mac ? 8 : 4);
    }
    synchronized boolean isIdle() {
        if (!queue.isEmpty()) return false;
        for (byte value : current) if (value != 0) return false;
        return true;
    }
    private void enqueue(int usage, int modifiers) {
        if (capsLock && usage >= 4 && usage <= 29) modifiers ^= 2;
        byte[] press = new byte[8];
        press[0] = (byte)modifiers; press[2] = (byte)usage;
        queue.add(press); queue.add(new byte[8]);
    }
    synchronized byte[] next() {
        byte[] value = queue.pollFirst();
        if (value != null) current = value;
        return value;
    }
    synchronized byte[] current() { return current.clone(); }
    synchronized void clear() { queue.clear(); current = new byte[8]; }
    synchronized void setCapsLock(boolean enabled) { capsLock = enabled; }
    static int usage(char c) {
        if (c >= 'a' && c <= 'z') return 4+c-'a';
        if (c >= 'A' && c <= 'Z') return 0x0200 | (4+c-'A');
        if (c >= '1' && c <= '9') return 0x1e+c-'1';
        if (c == '0') return 0x27;
        switch (c) {
            case '\n': case '\r': return ENTER;
            case '\t': return TAB;
            case ' ': return 0x2c;
            case '-': return 0x2d; case '_': return 0x022d;
            case '=': return 0x2e; case '+': return 0x022e;
            case '[': return 0x2f; case '{': return 0x022f;
            case ']': return 0x30; case '}': return 0x0230;
            case '\\': return 0x31; case '|': return 0x0231;
            case ';': return 0x33; case ':': return 0x0233;
            case '\'': return 0x34; case '"': return 0x0234;
            case '`': return 0x35; case '~': return 0x0235;
            case ',': return 0x36; case '<': return 0x0236;
            case '.': return 0x37; case '>': return 0x0237;
            case '/': return 0x38; case '?': return 0x0238;
            case '!': return 0x021e; case '@': return 0x021f;
            case '#': return 0x0220; case '$': return 0x0221;
            case '%': return 0x0222; case '^': return 0x0223;
            case '&': return 0x0224; case '*': return 0x0225;
            case '(': return 0x0226; case ')': return 0x0227;
            default: return 0;
        }
    }
}
