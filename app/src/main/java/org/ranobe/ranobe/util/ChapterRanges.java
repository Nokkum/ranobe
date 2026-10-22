package org.ranobe.ranobe.util;

import org.ranobe.ranobe.models.Novel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// The chapter ranges the search can narrow by: 1-100, 101-500, 501-1000, then every 500 up to 5000, then
// everything above. A novel matches when its chapter count falls in any chosen range.
public final class ChapterRanges {
    public static final class Range {
        // also what the chip shows: "1-100", "5001+"
        public final String id;
        public final int min;
        // Integer.MAX_VALUE for the last, open-ended range
        public final int max;

        Range(String id, int min, int max) {
            this.id = id;
            this.min = min;
            this.max = max;
        }

        public boolean contains(int chapterCount) {
            return chapterCount >= min && chapterCount <= max;
        }
    }

    public static final List<Range> ALL;

    static {
        List<Range> ranges = new ArrayList<>();
        ranges.add(new Range("1-100", 1, 100));
        ranges.add(new Range("101-500", 101, 500));
        ranges.add(new Range("501-1000", 501, 1000));
        for (int min = 1001; min < 5000; min += 500) {
            ranges.add(new Range(min + "-" + (min + 499), min, min + 499));
        }
        ranges.add(new Range("5001+", 5001, Integer.MAX_VALUE));
        ALL = Collections.unmodifiableList(ranges);
    }

    private ChapterRanges() {
    }

    // The range with this id, or null.
    public static Range byId(String id) {
        for (Range range : ALL) {
            if (range.id.equals(id)) return range;
        }
        return null;
    }

    // The chosen ranges' ids in the order the ranges are listed, not the order they were picked, so the same
    // choice always gives the same list. Ids that aren't a range are dropped.
    public static List<String> orderedIds(Set<String> chosen) {
        List<String> ordered = new ArrayList<>();
        if (chosen == null) return ordered;
        for (Range range : ALL) {
            if (chosen.contains(range.id)) ordered.add(range.id);
        }
        return ordered;
    }

    // An empty selection is "All": every novel matches, including one whose chapter count isn't known. With a
    // range chosen, a novel needs a known count inside one of the chosen ranges.
    public static boolean matches(Set<String> selectedIds, int chapterCount) {
        if (selectedIds == null || selectedIds.isEmpty()) return true;
        if (chapterCount <= 0) return false;
        for (String id : selectedIds) {
            Range range = byId(id);
            if (range != null && range.contains(chapterCount)) return true;
        }
        return false;
    }

    public static List<Novel> filter(Set<String> selectedIds, List<Novel> novels) {
        List<Novel> kept = new ArrayList<>();
        for (Novel novel : novels) {
            if (matches(selectedIds, novel.searchChapterCount)) kept.add(novel);
        }
        return kept;
    }
}
