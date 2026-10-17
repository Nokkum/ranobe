package org.ranobe.ranobe.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// The backup file format: a JSON object with a small header, then the entries.
//
//   { "app": "ranobe", "type": "library" | "history", "version": 1, "exportedAt": <ms>, "count": n,
//     "novels": [ ... ]  |  "entries": [ ... ] }
//
// Reading is forgiving about what an entry holds (missing fields get defaults, unknown ones are ignored) and
// strict about the header, so a file that isn't a backup is refused instead of half-imported.
public final class BackupCodec {
    public static final String APP = "ranobe";
    public static final String TYPE_LIBRARY = "library";
    public static final String TYPE_HISTORY = "history";
    // bumped only when an old reader couldn't make sense of a new file
    public static final int VERSION = 1;
    // a real backup is a few megabytes at most; anything bigger was picked by mistake
    public static final int MAX_BYTES = 32 * 1024 * 1024;

    private BackupCodec() {
    }

    // Why a file was refused.
    public static class BackupException extends Exception {
        public enum Reason {NOT_BACKUP, WRONG_TYPE, NEWER_VERSION, TOO_LARGE}

        public final Reason reason;
        // for WRONG_TYPE: what the file is (TYPE_LIBRARY or TYPE_HISTORY)
        public final String foundType;

        BackupException(Reason reason, String foundType) {
            super(reason.name());
            this.reason = reason;
            this.foundType = foundType;
        }
    }

    // The entries that could be read, and how many were too broken to use.
    public static final class Parsed<T> {
        public final List<T> items;
        public final int invalid;

        Parsed(List<T> items, int invalid) {
            this.items = items;
            this.invalid = invalid;
        }
    }

    // ---- reading the file's text ----

    // The whole stream as text, refusing anything over MAX_BYTES. A byte-order mark, which some editors add
    // and the JSON parser rejects, is dropped.
    public static String readText(InputStream in) throws IOException, BackupException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new BackupException(BackupException.Reason.TOO_LARGE, null);
            out.write(buffer, 0, read);
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    // ---- writing ----

    public static String libraryToJson(List<Novel> novels, long exportedAt) throws JSONException {
        JSONArray array = new JSONArray();
        for (Novel novel : novels) {
            if (novel.url == null || novel.url.isEmpty()) continue;
            array.put(novelToJson(novel));
        }
        JSONObject root = header(TYPE_LIBRARY, exportedAt, array.length());
        root.put("novels", array);
        return root.toString(2);
    }

    public static String historyToJson(List<ReadHistory> history, long exportedAt) throws JSONException {
        JSONArray array = new JSONArray();
        for (ReadHistory entry : history) {
            if (entry.url == null || entry.url.isEmpty()) continue;
            array.put(entryToJson(entry));
        }
        JSONObject root = header(TYPE_HISTORY, exportedAt, array.length());
        root.put("entries", array);
        return root.toString(2);
    }

    private static JSONObject header(String type, long exportedAt, int count) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", APP);
        root.put("type", type);
        root.put("version", VERSION);
        root.put("exportedAt", exportedAt);
        root.put("count", count);
        return root;
    }

    private static JSONObject novelToJson(Novel novel) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("url", novel.url);
        o.put("sourceId", novel.sourceId);
        putText(o, "name", novel.name);
        putText(o, "cover", novel.cover);
        putText(o, "status", novel.status);
        putText(o, "summary", novel.summary);
        o.put("alternateNames", strings(novel.alternateNames));
        o.put("authors", strings(novel.authors));
        o.put("genres", strings(novel.genres));
        o.put("rating", number(novel.rating));
        o.put("year", novel.year);
        o.put("lastKnownChapterCount", novel.lastKnownChapterCount);
        return o;
    }

    // The saved chapter text (ReadHistory.content) is left out: nothing reads it back and it can be huge.
    private static JSONObject entryToJson(ReadHistory entry) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("url", entry.url);
        putText(o, "novelUrl", entry.novelUrl);
        putText(o, "novelName", entry.novelName);
        putText(o, "cover", entry.cover);
        o.put("sourceId", entry.sourceId);
        putText(o, "name", entry.name);
        putText(o, "updated", entry.updated);
        o.put("id", number(entry.id));
        o.put("position", entry.position);
        o.put("readerOffset", entry.readerOffset);
        o.put("timestamp", entry.timestamp);
        return o;
    }

    private static void putText(JSONObject o, String key, String value) throws JSONException {
        if (value != null) o.put(key, value);
    }

    private static JSONArray strings(List<String> values) {
        JSONArray array = new JSONArray();
        if (values == null) return array;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) array.put(value);
        }
        return array;
    }

    // A float written as a double shows its binary noise (4.3f -> 4.300000190734863); go through its text
    // instead. JSON has no NaN or infinity, and the writer refuses them, so those become 0.
    private static double number(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0d;
        return Double.parseDouble(Float.toString(value));
    }

    // ---- reading ----

    public static Parsed<Novel> parseLibrary(String text) throws BackupException {
        JSONObject root = open(text, TYPE_LIBRARY);
        JSONArray array = root.optJSONArray("novels");
        if (array == null) throw new BackupException(BackupException.Reason.NOT_BACKUP, null);

        List<Novel> items = new ArrayList<>();
        int invalid = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            String url = o == null ? null : text(o, "url");
            if (url == null || url.trim().isEmpty()) {
                invalid++;
                continue;
            }
            // the id is derived from the url, never taken from the file
            Novel novel = new Novel(url.trim());
            novel.sourceId = o.optInt("sourceId", 0);
            novel.name = text(o, "name");
            novel.cover = text(o, "cover");
            novel.status = text(o, "status");
            novel.summary = text(o, "summary");
            novel.alternateNames = strings(o, "alternateNames");
            novel.authors = strings(o, "authors");
            novel.genres = strings(o, "genres");
            novel.rating = (float) finite(o.optDouble("rating", 0d));
            novel.year = o.optInt("year", 0);
            novel.lastKnownChapterCount = o.optInt("lastKnownChapterCount", 0);
            items.add(novel);
        }
        return new Parsed<>(items, invalid);
    }

    public static Parsed<ReadHistory> parseHistory(String text) throws BackupException {
        JSONObject root = open(text, TYPE_HISTORY);
        JSONArray array = root.optJSONArray("entries");
        if (array == null) throw new BackupException(BackupException.Reason.NOT_BACKUP, null);

        List<ReadHistory> items = new ArrayList<>();
        int invalid = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            String url = o == null ? null : text(o, "url");
            String novelUrl = o == null ? null : text(o, "novelUrl");
            // an entry needs the chapter and the novel it belongs to, or the History screen can't show it
            if (url == null || url.trim().isEmpty() || novelUrl == null || novelUrl.trim().isEmpty()) {
                invalid++;
                continue;
            }
            ReadHistory entry = new ReadHistory();
            entry.url = url.trim();
            entry.novelUrl = novelUrl.trim();
            entry.novelName = text(o, "novelName");
            entry.cover = text(o, "cover");
            entry.sourceId = o.optInt("sourceId", 0);
            entry.name = text(o, "name");
            entry.updated = text(o, "updated");
            entry.id = (float) finite(o.optDouble("id", 0d));
            entry.position = o.optInt("position", 0);
            entry.readerOffset = o.optInt("readerOffset", 0);
            entry.timestamp = o.optLong("timestamp", 0L);
            items.add(entry);
        }
        return new Parsed<>(items, invalid);
    }

    // Checks the header: that this is a Ranobe backup, a version this app can read, and the kind wanted.
    private static JSONObject open(String text, String expectedType) throws BackupException {
        JSONObject root;
        try {
            root = new JSONObject(text == null ? "" : text);
        } catch (JSONException e) {
            throw new BackupException(BackupException.Reason.NOT_BACKUP, null);
        }
        if (!APP.equals(text(root, "app"))) {
            throw new BackupException(BackupException.Reason.NOT_BACKUP, null);
        }
        int version = root.optInt("version", 0);
        if (version < 1) throw new BackupException(BackupException.Reason.NOT_BACKUP, null);
        if (version > VERSION) throw new BackupException(BackupException.Reason.NEWER_VERSION, null);

        String type = text(root, "type");
        if (!expectedType.equals(type)) {
            boolean known = TYPE_LIBRARY.equals(type) || TYPE_HISTORY.equals(type);
            throw new BackupException(known ? BackupException.Reason.WRONG_TYPE : BackupException.Reason.NOT_BACKUP,
                    known ? type : null);
        }
        return root;
    }

    // A string value; null for a missing key or a JSON null (which org.json would otherwise turn into "null").
    private static String text(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        Object value = o.opt(key);
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    // A list of strings without blanks. The database keeps lists as tab-separated text, so a tab inside a
    // name would split it in two; it becomes a space.
    private static List<String> strings(JSONObject o, String key) {
        List<String> out = new ArrayList<>();
        JSONArray array = o.optJSONArray(key);
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            if (array.isNull(i)) continue;
            Object value = array.opt(i);
            String item = (value instanceof String ? (String) value : String.valueOf(value)).replace('\t', ' ').trim();
            if (!item.isEmpty()) out.add(item);
        }
        return out;
    }

    private static double finite(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? 0d : value;
    }
}
