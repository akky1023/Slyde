package net.neomenia.inputrecorder;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class RecorderActivity extends Activity {
    private static final String[][] EXAMPLES = {
        {"き", "日本語：K → I"}, {"きょ", "日本語：K → Y → O"},
        {"きょう", "日本語：K → Y → O → U"}, {"こう", "日本語：K → O → U"},
        {"てい", "日本語：T → E → I"}, {"てん", "日本語：T → E → N"},
        {"ぎょう", "日本語：濁音のフリックから続けて入力"},
        {"がっこう", "日本語：促音を含む入力"},
        {"test", "英小文字：アルテ配列のまま入力"},
        {"Android", "英字：大文字・小文字を切り替えて入力"},
        {"QWERTY", "英大文字：アルテ配列のまま入力"},
        {"a1@example.com", "英数字・記号：アルテ配列から入力"}
    };
    private SessionLog journal;
    private RecordingEditText editor;
    private TextView status, prompt;
    private Button recordButton;
    private int exampleIndex = -1, caseNumber;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable clockDisplay = new Runnable() {
        public void run() {
            if (journal != null) status.setText("記録中 " + journal.id.substring(0, 8) + " · #" + caseNumber + " · t=" + SystemClock.uptimeMillis() + "ms");
            ticker.postDelayed(this, 100);
        }
    };
    private boolean caseActive;
    private String expected = "", caseNote = "";
    private int previousHeight = -1, previousImeBottom = -1;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.rgb(25, 38, 51)); return v;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setMinHeight(dp(44));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void row(LinearLayout root, Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        for (Button b : buttons) row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(row);
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8)); root.setBackgroundColor(Color.rgb(245, 248, 251));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
            root.setPadding(dp(16) + bars.left, dp(8) + bars.top, dp(16) + bars.right, dp(8) + bars.bottom);
            root.setTranslationY(0);
            // With edge-to-edge, reserve the IME area so controls remain visible.
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), dp(8) + Math.max(bars.bottom, keyboard.bottom));
            if (keyboard.bottom != previousImeBottom) {
                previousImeBottom = keyboard.bottom;
                log("imeGeometry", SessionLog.object("imeVisible", insets.isVisible(WindowInsets.Type.ime()), "imeBottomPx", keyboard.bottom,
                    "systemBottomPx", bars.bottom, "rootWidthPx", root.getWidth(), "rootHeightPx", root.getHeight()));
            }
            return insets;
        });
        root.addView(label("アルテ入力記録", 22));
        status = label("記録停止中 · データは端末内に保存", 12); root.addView(status);
        prompt = label("例題を選ぶか、自由入力を開始してください", 16); root.addView(prompt);
        row(root, button("例題", this::chooseExample), button("自由入力", this::customCase),
            button("IME選択", () -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker()));
        editor = new RecordingEditText(this); editor.setTextSize(23); editor.setGravity(Gravity.TOP);
        editor.setHint("ここでアルテを使って入力");
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setMinLines(2);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        editor.listener = this::log;
        recordButton = button("記録開始", this::toggleRecording);
        row(root, recordButton, button("次の例題", this::nextExample), button("書き出し", this::export));
        TextView footer = label("USB記録中はPC側で録画・軌跡も保存します", 11); root.addView(footer);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visible = new Rect(); root.getWindowVisibleDisplayFrame(visible);
            if (visible.height() != previousHeight) {
                previousHeight = visible.height();
                log("layout", SessionLog.object("visibleLeft", visible.left, "visibleTop", visible.top,
                    "visibleRight", visible.right, "visibleBottom", visible.bottom,
                    "rootWidthPx", root.getWidth(), "rootHeightPx", root.getHeight()));
            }
        });
        ticker.post(clockDisplay);
    }

    private void log(String type, JSONObject data) {
        if (journal == null) return;
        try { journal.write(type, data); }
        catch (IOException e) {
            SessionLog failed = journal; journal = null; caseActive = false;
            try { failed.close(); } catch (IOException ignored) {}
            status.setText("保存エラー：" + e.getMessage()); recordButton.setText("記録開始");
            new AlertDialog.Builder(this).setMessage("記録を停止しました。保存先を確認してください。\n" + e.getMessage()).setPositiveButton("閉じる", null).show();
        }
    }
    private void showKeyboard() {
        editor.requestFocus(); editor.post(() -> ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT));
    }
    private void toggleRecording() {
        if (journal != null) { stopRecording(); return; }
        try { journal = new SessionLog(this); }
        catch (IOException e) { status.setText("開始できません：" + e.getMessage()); return; }
        log("sessionStart", SessionLog.object("manufacturer", Build.MANUFACTURER, "model", Build.MODEL,
            "androidRelease", Build.VERSION.RELEASE, "sdk", Build.VERSION.SDK_INT,
            "density", getResources().getDisplayMetrics().density, "rotation", getWindowManager().getDefaultDisplay().getRotation(),
            "displayWidthPx", getResources().getDisplayMetrics().widthPixels, "displayHeightPx", getResources().getDisplayMetrics().heightPixels,
            "defaultIme", Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD),
            "rawTouchAvailableInApp", false));
        if (journal == null) return;
        previousImeBottom = -1;
        getWindow().getDecorView().requestApplyInsets();
        recordButton.setText("記録停止"); status.setText("記録中 · " + journal.id.substring(0, 8));
        startCase();
    }
    private void startCase() {
        editor.clearComposingText(); editor.setText("");
        if (journal == null) { showKeyboard(); return; }
        caseActive = true;
        log("caseStart", SessionLog.object("caseId", ++caseNumber, "expected", expected, "note", caseNote,
            "defaultIme", Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD)));
        showKeyboard();
    }
    private void endCase() {
        if (!caseActive) return;
        caseActive = false;
        log("caseEnd", SessionLog.object("caseId", caseNumber, "expected", expected, "snapshot", editor.snapshot(),
            "matchesExpected", expected.isEmpty() ? JSONObject.NULL : expected.equals(editor.getText().toString()),
            "evaluation", "text_only_not_gesture_validation"));
    }
    private void selectExample(int index) {
        endCase(); exampleIndex = index; expected = EXAMPLES[index][0]; caseNote = EXAMPLES[index][1];
        prompt.setText("「" + expected + "」  " + caseNote); startCase();
    }
    private void chooseExample() {
        String[] options = new String[EXAMPLES.length];
        for (int i = 0; i < options.length; i++) options[i] = EXAMPLES[i][0] + " — " + EXAMPLES[i][1];
        new AlertDialog.Builder(this).setTitle("記録する例題").setItems(options, (d, which) -> selectExample(which)).show();
    }
    private void nextExample() { selectExample((exampleIndex + 1) % EXAMPLES.length); }
    private void customCase() {
        EditText input = new EditText(this); input.setHint("入力したい文字（空欄でも可）");
        new AlertDialog.Builder(this).setTitle("自由入力の目標文字").setView(input)
            .setPositiveButton("開始", (d, which) -> {
                endCase(); expected = input.getText().toString(); caseNote = "自由入力";
                prompt.setText(expected.isEmpty() ? "自由入力" : "目標：「" + expected + "」"); startCase();
            }).setNegativeButton("キャンセル", null).show();
    }
    private void stopRecording() {
        endCase(); log("sessionEnd", SessionLog.object("snapshot", editor.snapshot()));
        SessionLog closing = journal; journal = null;
        if (closing != null) try { closing.close(); } catch (IOException e) { status.setText("保存エラー：" + e.getMessage()); return; }
        status.setText("保存しました · 書き出しまたはUSBで回収できます"); recordButton.setText("記録開始");
    }
    private void export() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-ndjson")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "arte-input-sessions.jsonl");
        startActivityForResult(intent, 10);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IOException("書き出し先を開けません");
            File[] files = new File(getFilesDir(), "sessions").listFiles((dir, name) -> name.endsWith(".jsonl"));
            if (files != null) for (File file : files) {
                try (InputStream in = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192]; int size;
                    while ((size = in.read(buffer)) != -1) output.write(buffer, 0, size);
                }
            }
            Toast.makeText(this, "記録を書き出しました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) { new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("閉じる", null).show(); }
    }
    @Override protected void onPause() {
        log("activityPaused", SessionLog.object("snapshot", editor.snapshot())); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); if (editor != null) log("activityResumed", SessionLog.object("snapshot", editor.snapshot()));
    }
    @Override protected void onDestroy() {
        ticker.removeCallbacks(clockDisplay);
        if (journal != null) stopRecording(); super.onDestroy();
    }
}
