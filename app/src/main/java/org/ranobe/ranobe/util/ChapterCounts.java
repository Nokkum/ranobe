package org.ranobe.ranobe.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads a chapter count out of the text a site puts on a search result: "Chapter 1234 [END]",
// "Chapter 302 - Title (End)", "109 Chapters".
public final class ChapterCounts {
    // "Chapter 531", "Ch. 12", "Episode 5", "Vol 2 Chapter 30"
    private static final Pattern LABELLED = Pattern.compile(
            "\\b(?:chapter|chap|ch|episode|ep)\\.?\\s*#?\\s*(\\d[\\d,]*)", Pattern.CASE_INSENSITIVE);
    // "109 Chapters", "1,234 Chapters"
    private static final Pattern COUNTED = Pattern.compile(
            "(\\d[\\d,]*)\\s*chapters?\\b", Pattern.CASE_INSENSITIVE);

    private ChapterCounts() {
    }

    // The number, or 0 when the text holds none. A decimal ("Chapter 12.5") counts as the whole chapter before it.
    public static int fromLabel(String text) {
        if (text == null) return 0;
        Matcher labelled = LABELLED.matcher(text);
        if (labelled.find()) return toInt(labelled.group(1));
        Matcher counted = COUNTED.matcher(text);
        if (counted.find()) return toInt(counted.group(1));
        return 0;
    }

    private static int toInt(String digits) {
        String plain = digits.replace(",", "");
        // a figure too long for an int is not a chapter count
        if (plain.isEmpty() || plain.length() > 9) return 0;
        return Integer.parseInt(plain);
    }
}
