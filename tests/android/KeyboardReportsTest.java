package dev.airmouse;

import java.util.Arrays;

public final class KeyboardReportsTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
        System.out.println("PASS: "+message);
    }
    private static void key(KeyboardReports queue, int usage, int modifiers, String message) {
        byte[] expected = new byte[8];
        expected[0] = (byte)modifiers;
        expected[2] = (byte)usage;
        check(Arrays.equals(queue.next(), expected) && Arrays.equals(queue.next(), new byte[8]), message);
    }
    public static void main(String[] args) {
        KeyboardReports q = new KeyboardReports();
        check(q.edit(0, "aA1!?\n").equals(KeyboardReports.Result.OK), "text is queued atomically");
        key(q, 4, 0, "lowercase key");
        key(q, 4, 2, "uppercase key uses Shift");
        key(q, 0x1e, 0, "number key");
        key(q, 0x1e, 2, "shifted number punctuation");
        key(q, 0x38, 2, "shifted punctuation");
        key(q, 0x28, 0, "newline presses Enter");
        check(q.next() == null, "every character has exactly one release");
        check(q.edit(2, "ok") == KeyboardReports.Result.OK, "backspace and replacement form one edit");
        key(q, 0x2a, 0, "replacement first backspace");
        key(q, 0x2a, 0, "replacement second backspace");
        key(q, 18, 0, "replacement first character");
        key(q, 14, 0, "replacement second character");
        q.setCapsLock(true);
        q.edit(0, "aA1");
        key(q, 4, 2, "Mac Caps Lock is compensated for lowercase text");
        key(q, 4, 0, "Mac Caps Lock is compensated for uppercase text");
        key(q, 0x1e, 0, "Caps Lock does not shift numbers");
        q.setCapsLock(false);
        check(q.edit(3, "hello🙂") == KeyboardReports.Result.UNSUPPORTED && q.next() == null,
            "unsupported Unicode cannot partially erase or type text");
        check(q.edit(-1, "a") == KeyboardReports.Result.UNSUPPORTED, "negative deletion is rejected");
        StringBuilder large = new StringBuilder();
        for (int i=0; i<513; i++) large.append('a');
        check(q.edit(0, large.toString()) == KeyboardReports.Result.FULL && q.next() == null,
            "oversized paste is rejected without partial typing");
        q.edit(0, "a");
        check(q.edit(0, large.toString()) == KeyboardReports.Result.FULL, "queue overflow preserves earlier accepted input");
        key(q, 4, 0, "input before rejected overflow remains intact");
        q.edit(0, "held");
        q.next();
        q.clear();
        check(q.next() == null && Arrays.equals(q.current(), new byte[8]), "stop clears pending typing and held keys");
        StringBuilder printable = new StringBuilder();
        for (char c=32; c<=126; c++) printable.append(c);
        check(q.edit(0, printable.toString()) == KeyboardReports.Result.OK, "all printable US ASCII characters are supported");
        int count = 0;
        while (q.next() != null) count++;
        check(count == 190, "printable ASCII has balanced key-down and key-up reports");
        check(q.isIdle(), "keyboard is idle only after every key is released");
        q.navigate(false, true);
        check(!q.isIdle(), "navigation waits in the keyboard queue");
        key(q, 0x2f, 8, "Mac Back uses Command-left bracket with balanced release");
        q.navigate(true, true);
        key(q, 0x30, 8, "Mac Forward uses Command-right bracket with balanced release");
        q.navigate(false, false);
        key(q, 0x50, 4, "Windows Back uses Alt-Left with balanced release");
        q.navigate(true, false);
        check(q.next()[0] == 4 && !q.isIdle(), "held modifiers keep the keyboard busy between press and release");
        check(Arrays.equals(q.next(), new byte[8]) && q.isIdle(), "navigation releases modifiers before scrolling can run");
        TextEdit correction = TextEdit.between("teh ", "the ");
        check(correction.backspaces == 3 && correction.inserted.equals("he "), "IME correction only replaces the changed suffix");
        TextEdit deletion = TextEdit.between("hello", "hell");
        check(deletion.backspaces == 1 && deletion.inserted.isEmpty(), "IME deletion produces a single backspace");
        TextEdit append = TextEdit.between("hello", "hello world");
        check(append.backspaces == 0 && append.inserted.equals(" world"), "IME committed text appends without retyping context");
        System.out.println(checks+" keyboard and IME edit checks passed.");
    }
}
