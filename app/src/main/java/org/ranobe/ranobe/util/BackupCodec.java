package org.ranobe.ranobe.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// The backup file format: a JSON object with a small header, then the entries.
//
//   { "app": "ranobe", "type": "library" | "history", "version": 1, "exportedAt": <ms>, "count": n,
//     "novels": [ ... ]  |  "entries": [ ... ], "signature": "<hex>" }
//
// Every export is signed (see signatureOf); a file without a valid signature is refused, whether it came from
// another app, was written by hand or was edited after export. The text must also be plain, shallow JSON.
// Past those checks, entries are read leniently: missing fields get defaults, unknown ones are ignored.
public final class BackupCodec {
    public static final String APP = "ranobe";
    public static final String TYPE_LIBRARY = "library";
    public static final String TYPE_HISTORY = "history";
    // bumped only when an old reader couldn't make sense of a new file
    public static final int VERSION = 1;
    // a real backup is a few megabytes at most; anything bigger was picked by mistake
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    // org.json parses nesting recursively, so extreme depth can overflow the stack. Exports are 4 deep.
    private static final int MAX_DEPTH = 16;
    private static final String SIGNATURE = "signature";
    private static final String JSON_EXTENSION = ".json";
    // The key ships in the app: it shows a file came from Export unchanged, not that nobody could forge it.
    private static final byte[] SIGNING_KEY =
            "ranobe/backup/v1/6d1f0c3a-9be2-4a57-8c41-d3e0a7f25b98".getBytes(StandardCharsets.UTF_8);

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
        return signed(root).toString(2);
    }

    public static String historyToJson(List<ReadHistory> history, long exportedAt) throws JSONException {
        JSONArray array = new JSONArray();
        for (ReadHistory entry : history) {
            if (entry.url == null || entry.url.isEmpty()) continue;
            array.put(entryToJson(entry));
        }
        JSONObject root = header(TYPE_HISTORY, exportedAt, array.length());
        root.put("entries", array);
        return signed(root).toString(2);
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

    private static JSONObject signed(JSONObject root) throws JSONException {
        root.put(SIGNATURE, signatureOf(root));
        return root;
    }

    // ---- pre-parse check ----

    // True if the text is strict JSON nested at most MAX_DEPTH deep. Loose syntax that org.json tolerates
    // (single quotes, comments, unquoted words) is refused: it could hide brackets from this count.
    // Package-private for tests.
    static boolean isPlainJsonWithinDepth(String text) {
        int depth = 0;
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            switch (c) {
                case ' ':
                case '\t':
                case '\n':
                case '\r':
                case ',':
                case ':':
                    i++;
                    break;
                case '{':
                case '[':
                    if (++depth > MAX_DEPTH) return false;
                    i++;
                    break;
                case '}':
                case ']':
                    if (--depth < 0) return false;
                    i++;
                    break;
                case '"':
                    i++;
                    while (true) {
                        if (i >= n) return false; // unterminated string
                        char s = text.charAt(i++);
                        if (s == '"') break;
                        if (s == '\\') i++; // skip the escaped char
                    }
                    break;
                default:
                    i = endOfBareToken(text, i);
                    if (i < 0) return false;
            }
        }
        return depth == 0;
    }

    // End of the number/true/false/null at 'from', or -1 if it isn't one or no separator follows it.
    private static int endOfBareToken(String text, int from) {
        int n = text.length();
        int i = from;
        char c = text.charAt(i);
        if (c == '-' || (c >= '0' && c <= '9')) {
            while (i < n && "0123456789+-.eE".indexOf(text.charAt(i)) >= 0) i++;
        } else if (text.startsWith("true", i)) {
            i += 4;
        } else if (text.startsWith("false", i)) {
            i += 5;
        } else if (text.startsWith("null", i)) {
            i += 4;
        } else {
            return -1;
        }
        if (i < n) {
            char d = text.charAt(i);
            if (d != ' ' && d != '\t' && d != '\n' && d != '\r' && d != ',' && d != ']' && d != '}') return -1;
        }
        return i;
    }

    // ---- the signature ----

    // True for a name ending in .json (any case) with something before it.
    public static boolean hasJsonExtension(String name) {
        if (name == null) return false;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.length() > JSON_EXTENSION.length() && lower.endsWith(JSON_EXTENSION);
    }

    // HMAC-SHA256 (hex) over the file minus its signature, in a canonical form (sorted keys, normalised
    // numbers, length-prefixed strings) so layout and key order don't matter. Package-private for tests.
    static String signatureOf(JSONObject root) throws JSONException {
        StringBuilder canonical = new StringBuilder();
        canonicalObject(root, canonical, true);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_KEY, "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return hex.toString();
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is always available
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasValidSignature(JSONObject root) {
        if (!root.has(SIGNATURE) || root.isNull(SIGNATURE)) return false;
        Object given = root.opt(SIGNATURE);
        if (!(given instanceof String)) return false;
        try {
            byte[] expected = signatureOf(root).getBytes(StandardCharsets.UTF_8);
            byte[] actual = ((String) given).trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, actual);
        } catch (JSONException | RuntimeException e) {
            // content that can't be canonicalised, e.g. NaN
            return false;
        }
    }

    private static void canonicalObject(JSONObject o, StringBuilder out, boolean isRoot) throws JSONException {
        TreeSet<String> keys = new TreeSet<>();
        Iterator<String> it = o.keys();
        while (it.hasNext()) keys.add(it.next());
        out.append('{');
        for (String key : keys) {
            if (isRoot && SIGNATURE.equals(key)) continue;
            canonicalString(key, out);
            out.append(':');
            canonicalValue(o.get(key), out);
            out.append(',');
        }
        out.append('}');
    }

    private static void canonicalValue(Object value, StringBuilder out) throws JSONException {
        if (value == null || JSONObject.NULL.equals(value)) {
            out.append('z');
        } else if (value instanceof JSONObject) {
            canonicalObject((JSONObject) value, out, false);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            out.append('[');
            for (int i = 0; i < array.length(); i++) {
                canonicalValue(array.get(i), out);
                out.append(',');
            }
            out.append(']');
        } else if (value instanceof Number) {
            // 4, 4.0 and 4.00 match; NaN or infinity throws NumberFormatException
            BigDecimal number = new BigDecimal(value.toString()).stripTrailingZeros();
            out.append('n').append(number.signum() == 0 ? "0" : number.toPlainString()).append(';');
        } else if (value instanceof Boolean) {
            out.append((Boolean) value ? 't' : 'f');
        } else {
            canonicalString(String.valueOf(value), out);
        }
    }

    // the length keeps neighbouring strings from running together
    private static void canonicalString(String value, StringBuilder out) {
        out.append('s').append(value.length()).append(':').append(value);
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

    // Checks the header: a Ranobe backup, a readable version, correctly signed, and the kind wanted.
    private static JSONObject open(String text, String expectedType) throws BackupException {
        String source = text == null ? "" : text;
        if (!isPlainJsonWithinDepth(source)) throw new BackupException(BackupException.Reason.NOT_BACKUP, null);
        JSONObject root;
        try {
            root = new JSONObject(source);
        } catch (JSONException | StackOverflowError e) {
            // backstop for the depth check
            throw new BackupException(BackupException.Reason.NOT_BACKUP, null);
        }
        if (!APP.equals(text(root, "app"))) {
            throw new BackupException(BackupException.Reason.NOT_BACKUP, null);
        }
        int version = root.optInt("version", 0);
        if (version < 1) throw new BackupException(BackupException.Reason.NOT_BACKUP, null);
        if (version > VERSION) throw new BackupException(BackupException.Reason.NEWER_VERSION, null);
        // trusted only if Export wrote it and nothing changed
        if (!hasValidSignature(root)) throw new BackupException(BackupException.Reason.NOT_BACKUP, null);

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
