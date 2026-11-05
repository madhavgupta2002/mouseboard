package dev.airmouse;

/** Replacement at an end-anchored caret, including IME autocorrection. */
final class TextEdit {
    final int backspaces;
    final String inserted;
    private TextEdit(int backspaces, String inserted) {
        this.backspaces = backspaces;
        this.inserted = inserted;
    }
    static TextEdit between(String previous, String next) {
        int prefix = 0;
        while (prefix < previous.length() && prefix < next.length()
                && previous.charAt(prefix) == next.charAt(prefix)) prefix++;
        return new TextEdit(previous.length()-prefix, next.substring(prefix));
    }
}
