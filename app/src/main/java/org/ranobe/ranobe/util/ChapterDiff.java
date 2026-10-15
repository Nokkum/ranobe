package org.ranobe.ranobe.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Compares a freshly fetched chapter list with the one that was saved.
public final class ChapterDiff {
    private ChapterDiff() {
    }

    // How many of the fetched chapters weren't saved before. Chapters are matched by url, so a source that
    // renumbers or reorders its list doesn't look like new chapters; a url listed twice counts once.
    public static int countNew(Set<String> knownUrls, List<String> fetchedUrls) {
        Set<String> seen = new HashSet<>();
        int added = 0;
        for (String url : fetchedUrls) {
            if (url == null || knownUrls.contains(url) || !seen.add(url)) continue;
            added++;
        }
        return added;
    }
}
