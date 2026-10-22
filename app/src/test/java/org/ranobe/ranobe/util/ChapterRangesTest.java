package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChapterRangesTest {
    private static Set<String> ids(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static Novel novel(String name, int chapters) {
        Novel novel = new Novel("https://example.org/" + name);
        novel.name = name;
        novel.searchChapterCount = chapters;
        return novel;
    }

    @Test
    public void theRangesRunFrom1To5000ThenOpenEnded() {
        List<String> labels = new ArrayList<>();
        for (ChapterRanges.Range range : ChapterRanges.ALL) labels.add(range.id);

        assertEquals(Arrays.asList("1-100", "101-500", "501-1000", "1001-1500", "1501-2000", "2001-2500",
                "2501-3000", "3001-3500", "3501-4000", "4001-4500", "4501-5000", "5001+"), labels);
    }

    @Test
    public void theRangesNeitherOverlapNorLeaveAGap() {
        for (int i = 1; i < ChapterRanges.ALL.size(); i++) {
            assertEquals(ChapterRanges.ALL.get(i - 1).max + 1, ChapterRanges.ALL.get(i).min);
        }
        assertEquals(1, ChapterRanges.ALL.get(0).min);
        assertEquals(Integer.MAX_VALUE, ChapterRanges.ALL.get(ChapterRanges.ALL.size() - 1).max);
    }

    @Test
    public void everyChapterCountFallsInExactlyOneRange() {
        for (int count : new int[]{1, 99, 100, 101, 499, 500, 501, 999, 1000, 1001, 1500, 1501, 4999, 5000, 5001, 12345, 99999}) {
            int hits = 0;
            for (ChapterRanges.Range range : ChapterRanges.ALL) if (range.contains(count)) hits++;
            assertEquals("count " + count, 1, hits);
        }
    }

    @Test
    public void boundariesBelongToTheRightRange() {
        assertTrue(ChapterRanges.matches(ids("1-100"), 100));
        assertFalse(ChapterRanges.matches(ids("1-100"), 101));
        assertTrue(ChapterRanges.matches(ids("101-500"), 101));
        assertTrue(ChapterRanges.matches(ids("101-500"), 500));
        assertFalse(ChapterRanges.matches(ids("101-500"), 501));
        assertTrue(ChapterRanges.matches(ids("501-1000"), 501));
        assertTrue(ChapterRanges.matches(ids("501-1000"), 1000));
        assertTrue(ChapterRanges.matches(ids("1001-1500"), 1001));
        assertTrue(ChapterRanges.matches(ids("4501-5000"), 5000));
        assertTrue(ChapterRanges.matches(ids("5001+"), 5001));
        assertTrue(ChapterRanges.matches(ids("5001+"), 20000));
        assertFalse(ChapterRanges.matches(ids("5001+"), 5000));
    }

    @Test
    public void severalRangesMatchAnyOfThem() {
        Set<String> chosen = ids("1-100", "501-1000");

        assertTrue(ChapterRanges.matches(chosen, 50));
        assertFalse(ChapterRanges.matches(chosen, 300));
        assertTrue(ChapterRanges.matches(chosen, 750));
        assertFalse(ChapterRanges.matches(chosen, 1200));
    }

    @Test
    public void noRangeChosenIsAllSoEverythingMatchesEvenAnUnknownCount() {
        assertTrue(ChapterRanges.matches(Collections.<String>emptySet(), 4));
        assertTrue(ChapterRanges.matches(Collections.<String>emptySet(), 99999));
        assertTrue(ChapterRanges.matches(Collections.<String>emptySet(), 0));
        assertTrue(ChapterRanges.matches(null, 0));
    }

    @Test
    public void withARangeChosenAnUnknownCountNeverMatches() {
        assertFalse(ChapterRanges.matches(ids("1-100"), 0));
        assertFalse(ChapterRanges.matches(ids("1-100", "5001+"), -3));
    }

    @Test
    public void anUnknownRangeIdMatchesNothing() {
        assertFalse(ChapterRanges.matches(ids("nonsense"), 50));
        assertNull(ChapterRanges.byId("nonsense"));
        assertEquals("101-500", ChapterRanges.byId("101-500").id);
    }

    @Test
    public void filteringKeepsTheOrderAndDropsWhatIsOutsideTheRanges() {
        List<Novel> novels = Arrays.asList(novel("a", 80), novel("b", 300), novel("c", 90), novel("d", 0), novel("e", 700));

        List<Novel> kept = ChapterRanges.filter(ids("1-100", "501-1000"), novels);

        assertEquals(3, kept.size());
        assertEquals("a", kept.get(0).name);
        assertEquals("c", kept.get(1).name);
        assertEquals("e", kept.get(2).name);
    }

    @Test
    public void orderedIdsFollowTheListedOrderNotThePickedOrder() {
        assertEquals(Arrays.asList("1-100", "501-1000", "5001+"),
                ChapterRanges.orderedIds(ids("5001+", "1-100", "501-1000")));
    }

    @Test
    public void orderedIdsDropWhatIsNotARange() {
        assertEquals(Arrays.asList("101-500"), ChapterRanges.orderedIds(ids("nonsense", "101-500", "")));
        assertTrue(ChapterRanges.orderedIds(null).isEmpty());
        assertTrue(ChapterRanges.orderedIds(Collections.<String>emptySet()).isEmpty());
    }

    @Test
    public void filteringWithAllKeepsEverything() {
        List<Novel> novels = Arrays.asList(novel("a", 80), novel("b", 0), novel("c", 9000));

        assertEquals(3, ChapterRanges.filter(Collections.<String>emptySet(), novels).size());
    }
}
