package org.ranobe.ranobe.util;

// How long a search waits, with nothing to show, before it says "No Results Found". The person picks one of
// four values in Settings.
public final class SearchTimeouts {
    public static final int[] OPTIONS = {15, 20, 25, 30};
    public static final int DEFAULT = 20;

    private SearchTimeouts() {
    }

    // Anything that isn't one of the options (an old or damaged saved value) becomes the default.
    public static int normalize(int seconds) {
        for (int option : OPTIONS) {
            if (option == seconds) return seconds;
        }
        return DEFAULT;
    }

    public static long millis(int seconds) {
        return normalize(seconds) * 1000L;
    }

    // Position of the value in OPTIONS (the default's, for an unknown value).
    public static int indexOf(int seconds) {
        int wanted = normalize(seconds);
        for (int i = 0; i < OPTIONS.length; i++) {
            if (OPTIONS[i] == wanted) return i;
        }
        return 0;
    }
}
