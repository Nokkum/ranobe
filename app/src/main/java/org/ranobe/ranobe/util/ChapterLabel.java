package org.ranobe.ranobe.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Splits a chapter into the "number" and "title" lines the reader menu shows. Sources name chapters
// differently: most already put "Chapter 12: Title" in the name, some only give a title, some nothing.
public final class ChapterLabel {
    // "Chapter 12", "Chapter 12: Title", "Ch. 12 - Title", "chapter 12.5 Title"
    private static final Pattern NUMBERED = Pattern.compile(
            "^\\s*(?:chapter|ch\\.?)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:[:.\\-\\u2013\\u2014]\\s*|\\s+)?(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // "12", "12.5"; empty when the chapter has no usable number
    public final String number;
    // the name without its "Chapter N" prefix; empty when there is nothing left
    public final String title;

    private ChapterLabel(String number, String title) {
        this.number = number;
        this.title = title;
    }

    // id is the source's chapter number (0 when it gave none); index is the position in the chapter
    // list, or -1 while the list is still loading
    public static ChapterLabel of(float id, int index, String name) {
        String trimmed = name == null ? "" : name.trim();
        Matcher matcher = NUMBERED.matcher(trimmed);
        if (matcher.matches()) {
            return new ChapterLabel(matcher.group(1), matcher.group(2).trim());
        }
        String number = id > 0 ? clean(id) : index >= 0 ? String.valueOf(index + 1) : "";
        return new ChapterLabel(number, trimmed);
    }

    // "1.0" reads as "1", "2.5" stays "2.5"
    private static String clean(float value) {
        return value == (long) value ? String.valueOf((long) value) : String.valueOf(value);
    }
}
