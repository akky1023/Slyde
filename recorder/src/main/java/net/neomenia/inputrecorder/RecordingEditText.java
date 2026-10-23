package net.neomenia.inputrecorder;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.*;
import android.widget.EditText;
import org.json.JSONObject;
import java.util.function.Supplier;

final class RecordingEditText extends EditText {
    interface Listener { void record(String type, JSONObject data); }
    Listener listener;

    RecordingEditText(Context context) {
        super(context);
        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                emit("textChanged", SessionLog.object("start", start, "removed", before,
                    "inserted", count, "snapshot", snapshot()));
            }
            public void afterTextChanged(Editable e) {}
        });
    }

    JSONObject snapshot() {
        Editable e = getText();
        return SessionLog.object("text", e == null ? "" : e.toString(),
            "selectionStart", getSelectionStart(), "selectionEnd", getSelectionEnd(),
            "composingStart", e == null ? -1 : BaseInputConnection.getComposingSpanStart(e),
            "composingEnd", e == null ? -1 : BaseInputConnection.getComposingSpanEnd(e));
    }

    private void emit(String type, JSONObject data) { if (listener != null) listener.record(type, data); }

    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        emit("selectionChanged", SessionLog.object("snapshot", snapshot()));
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection target = super.onCreateInputConnection(info);
        if (target == null) return null;
        emit("connectionCreated", SessionLog.object("inputType", info.inputType, "imeOptions", info.imeOptions));
        return new InputConnectionWrapper(target, false) {
            private boolean call(String method, JSONObject args, Supplier<Boolean> action) {
                emit("imeCall", SessionLog.object("method", method, "args", args, "before", snapshot()));
                boolean result = action.get();
                emit("imeResult", SessionLog.object("method", method, "result", result, "after", snapshot()));
                return result;
            }
            private JSONObject textArgs(CharSequence text, int cursor) {
                return SessionLog.object("text", text == null ? null : text.toString(), "newCursorPosition", cursor);
            }
            @Override public boolean commitText(CharSequence text, int cursor) {
                return call("commitText", textArgs(text, cursor), () -> super.commitText(text, cursor));
            }
            @Override public boolean commitText(CharSequence text, int cursor, TextAttribute attr) {
                return call("commitTextWithAttribute", textArgs(text, cursor), () -> super.commitText(text, cursor, attr));
            }
            @Override public boolean setComposingText(CharSequence text, int cursor) {
                return call("setComposingText", textArgs(text, cursor), () -> super.setComposingText(text, cursor));
            }
            @Override public boolean setComposingText(CharSequence text, int cursor, TextAttribute attr) {
                return call("setComposingTextWithAttribute", textArgs(text, cursor), () -> super.setComposingText(text, cursor, attr));
            }
            @Override public boolean setComposingRegion(int start, int end) {
                return call("setComposingRegion", SessionLog.object("start", start, "end", end), () -> super.setComposingRegion(start, end));
            }
            @Override public boolean setComposingRegion(int start, int end, TextAttribute attr) {
                return call("setComposingRegionWithAttribute", SessionLog.object("start", start, "end", end), () -> super.setComposingRegion(start, end, attr));
            }
            @Override public boolean finishComposingText() {
                return call("finishComposingText", SessionLog.object(), () -> super.finishComposingText());
            }
            @Override public boolean deleteSurroundingText(int before, int after) {
                return call("deleteSurroundingText", SessionLog.object("beforeLength", before, "afterLength", after), () -> super.deleteSurroundingText(before, after));
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                return call("deleteSurroundingTextInCodePoints", SessionLog.object("beforeLength", before, "afterLength", after), () -> super.deleteSurroundingTextInCodePoints(before, after));
            }
            @Override public boolean setSelection(int start, int end) {
                return call("setSelection", SessionLog.object("start", start, "end", end), () -> super.setSelection(start, end));
            }
            @Override public boolean beginBatchEdit() {
                return call("beginBatchEdit", SessionLog.object(), () -> super.beginBatchEdit());
            }
            @Override public boolean endBatchEdit() {
                return call("endBatchEdit", SessionLog.object(), () -> super.endBatchEdit());
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                return call("sendKeyEvent", SessionLog.object("action", event.getAction(), "keyCode", event.getKeyCode(), "unicodeChar", event.getUnicodeChar()), () -> super.sendKeyEvent(event));
            }
            @Override public boolean performEditorAction(int action) {
                return call("performEditorAction", SessionLog.object("action", action), () -> super.performEditorAction(action));
            }
            @Override public boolean performContextMenuAction(int id) {
                return call("performContextMenuAction", SessionLog.object("id", id), () -> super.performContextMenuAction(id));
            }
            @Override public boolean performPrivateCommand(String action, Bundle data) {
                return call("performPrivateCommand", SessionLog.object("action", action), () -> super.performPrivateCommand(action, data));
            }
            @Override public boolean replaceText(int start, int end, CharSequence text, int cursor, TextAttribute attr) {
                return call("replaceText", SessionLog.object("start", start, "end", end, "text", text == null ? null : text.toString(), "newCursorPosition", cursor), () -> super.replaceText(start, end, text, cursor, attr));
            }
        };
    }
}
