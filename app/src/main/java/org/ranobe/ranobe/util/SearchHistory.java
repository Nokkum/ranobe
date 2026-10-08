package org.ranobe.ranobe.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The last few searches, newest first, kept on the device.
public final class SearchHistory {
    public static final int MAX_ENTRIES = 10;
    private static final String PREFS = "search_history";
    private static final String KEY = "recent";

    private SearchHistory() {
    }

    public static List<String> load(Context context) {
        return fromJson(prefs(context).getString(KEY, null));
    }

    public static List<String> add(Context context, String keyword) {
        List<String> updated = with(load(context), keyword, MAX_ENTRIES);
        save(context, updated);
        return updated;
    }

    public static List<String> remove(Context context, String keyword) {
        List<String> updated = without(load(context), keyword);
        save(context, updated);
        return updated;
    }

    private static void save(Context context, List<String> entries) {
        prefs(context).edit().putString(KEY, toJson(entries)).apply();
    }

    // {@code keyword} moved to the front (a repeat in other letter case is replaced), the list capped.
    static List<String> with(List<String> current, String keyword, int cap) {
        String text = keyword == null ? "" : keyword.trim();
        List<String> updated = new ArrayList<>();
        if (text.isEmpty()) {
            updated.addAll(current);
            return updated;
        }
        updated.add(text);
        for (String entry : current) {
            if (!same(entry, text) && updated.size() < cap) updated.add(entry);
        }
        return updated;
    }

    static List<String> without(List<String> current, String keyword) {
        List<String> updated = new ArrayList<>();
        for (String entry : current) {
            if (!same(entry, keyword)) updated.add(entry);
        }
        return updated;
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }

    static String toJson(List<String> entries) {
        return new JSONArray(entries).toString();
    }

    // Never throws: unreadable input gives an empty list, and blank entries are skipped.
    static List<String> fromJson(String json) {
        List<String> entries = new ArrayList<>();
        if (json == null || json.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length() && entries.size() < MAX_ENTRIES; i++) {
                String entry = array.optString(i, "").trim();
                if (!entry.isEmpty()) entries.add(entry);
            }
        } catch (JSONException e) {
            entries.clear();
        }
        return entries;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
