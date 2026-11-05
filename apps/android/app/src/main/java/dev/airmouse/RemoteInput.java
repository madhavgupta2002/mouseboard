package dev.airmouse;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

/** Native phone IME with live ASCII typing and suffix edits for autocorrection. */
final class RemoteInput extends EditText {
    interface Sender {
        boolean edit(int backspaces, String text);
        void activity();
    }
    private final Sender sender;
    private String sent = "";
    private int batching;
    private boolean replacing;

    RemoteInput(Context context, Sender sender) {
        super(context);
        this.sender = sender;
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        setMaxLines(1);
        setSelectAllOnFocus(false);
        setLongClickable(false);
        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            public void afterTextChanged(Editable value) {
                if (!replacing && batching == 0) flush();
            }
        });
    }

    void resetContext() {
        replacing = true;
        batching = 0;
        sent = "";
        setText("");
        replacing = false;
    }

    void finishInput() {
        Editable value = getText();
        if (value != null) BaseInputConnection.removeComposingSpans(value);
        flush();
        resetContext();
    }

    private void flush() {
        sender.activity();
        Editable value = getText();
        if (value == null) return;
        boolean composing = BaseInputConnection.getComposingSpanStart(value) >= 0;
        String next = value.toString();
        if (composing) {
            // A non-ASCII composition may still become supported text on commit.
            for (int i=0; i<next.length(); i++) if (KeyboardReports.usage(next.charAt(i)) == 0) return;
        }
        if (next.equals(sent)) return;
        TextEdit edit = TextEdit.between(sent, next);
        if (sender.edit(edit.backspaces, edit.inserted)) {
            sent = next;
            // Keep a small in-memory context for backspace/autocorrection.
            if (!composing && sent.length() > 256) replaceLocally(sent.substring(sent.length()-128));
        } else replaceLocally(sent);
        if (!composing && getText() != null) setSelection(getText().length());
    }

    private void replaceLocally(String text) {
        replacing = true;
        sent = text;
        setText(text);
        setSelection(text.length());
        replacing = false;
    }

    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        Editable text = getText();
        if (sender != null && !replacing && batching == 0 && text != null
                && BaseInputConnection.getComposingSpanStart(text) < 0
                && (start != text.length() || end != text.length())) {
            // The phone is an input surface, not a mirror of the Mac document.
            post(() -> {
                Editable current = getText();
                if (current != null && !replacing && batching == 0
                        && BaseInputConnection.getComposingSpanStart(current) < 0)
                    setSelection(current.length());
            });
        }
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection base = super.onCreateInputConnection(info);
        if (base == null) return null;
        return new InputConnectionWrapper(base, false) {
            private boolean changed(Action action) {
                batching++;
                try { return action.run(); }
                finally { batching--; if (batching == 0) flush(); }
            }
            @Override public boolean beginBatchEdit() {
                batching++;
                return super.beginBatchEdit();
            }
            @Override public boolean endBatchEdit() {
                boolean result = super.endBatchEdit();
                batching = Math.max(0, batching-1);
                if (batching == 0) flush();
                return result;
            }
            @Override public boolean commitText(CharSequence text, int position) {
                return changed(() -> super.commitText(text, position));
            }
            @Override public boolean setComposingText(CharSequence text, int position) {
                return changed(() -> super.setComposingText(text, position));
            }
            @Override public boolean finishComposingText() {
                return changed(() -> super.finishComposingText());
            }
            @Override public boolean deleteSurroundingText(int before, int after) {
                int length = getText() == null ? 0 : getText().length();
                boolean composing = getText() != null && BaseInputConnection.getComposingSpanStart(getText()) >= 0;
                boolean result = changed(() -> super.deleteSurroundingText(before, after));
                if (!composing && before > length) sender.edit(Math.min(64, before-length), "");
                return result;
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                return deleteSurroundingText(before, after);
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_DEL && getText().length() == 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) sender.edit(1, "");
                    return true;
                }
                return changed(() -> super.sendKeyEvent(event));
            }
            @Override public boolean performEditorAction(int action) {
                finishComposingText();
                return commitText("\n", 1);
            }
        };
    }

    private interface Action { boolean run(); }
}
