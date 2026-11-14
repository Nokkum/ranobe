package org.ranobe.ranobe.util;

import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Novel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListUtils {
    public static List<Chapter> searchByName(String keyword, List<Chapter> items) {
        List<Chapter> result = new ArrayList<>();
        for (Chapter item : items) {
            if (containsIgnoreCase(item.name, keyword)) {
                result.add(item);
            }
        }
        return result;
    }

    // Whether the text holds the keyword, upper or lower case alike. It looks at the text where it is instead of
    // lowercasing a copy: a novel can have thousands of chapters, and a copy of every name on every search made
    // a lot of garbage for a weak phone to collect.
    static boolean containsIgnoreCase(String text, String keyword) {
        if (text == null) return false;
        int length = keyword.length();
        for (int start = 0, last = text.length() - length; start <= last; start++) {
            if (text.regionMatches(true, start, keyword, 0, length)) return true;
        }
        return false;
    }

    public static List<Chapter> sortById(List<Chapter> items) {
        List<Chapter> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Float.compare(a.id, b.id));
        return sorted;
    }

    public static List<Novel> sortByName(List<Novel> items, boolean asc) {
        List<Novel> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> {
            if (a.name == null && b.name == null) return 0;
            if (a.name == null) return asc ? 1 : -1;
            if (b.name == null) return asc ? -1 : 1;
            return asc ? a.name.compareToIgnoreCase(b.name) : b.name.compareToIgnoreCase(a.name);
        });
        return sorted;
    }
}
