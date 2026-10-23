package net.neomenia.inputrecorder;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Append-only local journal. Each event is flushed before returning. */
final class SessionLog implements AutoCloseable {
    final String id = UUID.randomUUID().toString();
    private final BufferedWriter writer;
    private long sequence;

    SessionLog(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "sessions");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("保存先を作成できません");
        writer = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(new File(directory, id + ".jsonl")), StandardCharsets.UTF_8));
    }

    static JSONObject object(Object... pairs) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2)
                result.put(pairs[i].toString(), pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
        } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); }
        return result;
    }

    synchronized void write(String type, JSONObject data) throws IOException {
        JSONObject event = object("schema", 1, "sessionId", id, "seq", ++sequence,
            "type", type, "wallTimeMs", System.currentTimeMillis(),
            "uptimeMs", SystemClock.uptimeMillis(), "elapsedRealtimeNs", SystemClock.elapsedRealtimeNanos(),
            "data", data);
        writer.write(event.toString());
        writer.newLine();
        writer.flush();
    }

    @Override public synchronized void close() throws IOException { writer.close(); }
}
