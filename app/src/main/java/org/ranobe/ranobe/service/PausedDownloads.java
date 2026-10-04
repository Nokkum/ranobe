package org.ranobe.ranobe.service;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ranobe.ranobe.models.Chapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Chapters that a batch download could not fetch because the source asked for a human check. They are
 * kept on disk, so the batch can continue after the check even if the app process was killed meanwhile.
 */
final class PausedDownloads {
    private static final String PREFS = "paused_downloads";
    private static final String KEY = "items";

    private PausedDownloads() {
    }

    static final class Entry {
        final String url;
        final String novelUrl;
        final String name;
        final float id;
        final int sourceId;

        Entry(String url, String novelUrl, String name, float id, int sourceId) {
            this.url = url;
            this.novelUrl = novelUrl == null ? "" : novelUrl;
            this.name = name == null ? "" : name;
            this.id = id;
            this.sourceId = sourceId;
        }

        static Entry of(DownloadService.DownloadItem item) {
            Chapter chapter = item.chapter;
            return new Entry(chapter.url, chapter.novelUrl, chapter.name, chapter.id, item.sourceId);
        }

        Chapter toChapter() {
            Chapter chapter = new Chapter(novelUrl.isEmpty() ? null : novelUrl);
            chapter.url = url;
            chapter.name = name;
            chapter.id = id;
            return chapter;
        }
    }

    static String toJson(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            try {
                array.put(new JSONObject()
                        .put("u", entry.url)
                        .put("n", entry.novelUrl)
                        .put("t", entry.name)
                        .put("i", (double) entry.id)
                        .put("s", entry.sourceId));
            } catch (JSONException ignored) {
                // skip an entry that cannot be written
            }
        }
        return array.toString();
    }

    /** Never throws: unreadable input gives an empty list, and entries without a URL are skipped. */
    static List<Entry> fromJson(String json) {
        List<Entry> entries = new ArrayList<>();
        if (json == null || json.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String url = object.optString("u", "");
                if (url.isEmpty()) continue;
                entries.add(new Entry(
                        url,
                        object.optString("n", ""),
                        object.optString("t", ""),
                        (float) object.optDouble("i", 0),
                        object.optInt("s", 0)
                ));
            }
        } catch (JSONException e) {
            entries.clear();
        }
        return entries;
    }

    static void save(Context context, List<Entry> entries) {
        prefs(context).edit().putString(KEY, toJson(entries)).apply();
    }

    /** Returns the saved chapters and forgets them. */
    static List<Entry> take(Context context) {
        SharedPreferences prefs = prefs(context);
        List<Entry> entries = fromJson(prefs.getString(KEY, null));
        prefs.edit().remove(KEY).apply();
        return entries;
    }

    static void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
