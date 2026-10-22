package org.ranobe.ranobe.util;

import android.content.Context;
import android.content.SharedPreferences;

// Search screen settings kept on the device.
public final class SearchPreferences {
    private static final String PREFS = "search_prefs";
    private static final String KEY_LIVE = "live_search";
    private static final String KEY_TIMEOUT = "search_timeout_seconds";

    private SearchPreferences() {
    }

    // Whether results update while typing. On by default.
    public static boolean isLiveSearch(Context context) {
        return prefs(context).getBoolean(KEY_LIVE, true);
    }

    public static void setLiveSearch(Context context, boolean live) {
        prefs(context).edit().putBoolean(KEY_LIVE, live).apply();
    }

    // Seconds a search waits, with nothing to show, before saying "No Results Found" (see SearchTimeouts).
    public static int getTimeoutSeconds(Context context) {
        return SearchTimeouts.normalize(prefs(context).getInt(KEY_TIMEOUT, SearchTimeouts.DEFAULT));
    }

    public static void setTimeoutSeconds(Context context, int seconds) {
        prefs(context).edit().putInt(KEY_TIMEOUT, SearchTimeouts.normalize(seconds)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
