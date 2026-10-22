package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearchFiltersMultiStatusTest {
    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static Novel novel(String name, String status) {
        Novel novel = new Novel("https://example.org/" + name);
        novel.name = name;
        novel.status = status;
        return novel;
    }

    @Test
    public void noStatusChosenMatchesEverything() {
        assertTrue(SearchFilters.matchesAnyStatus(Collections.<String>emptySet(), "Completed"));
        assertTrue(SearchFilters.matchesAnyStatus(Collections.<String>emptySet(), null));
        assertTrue(SearchFilters.matchesAnyStatus(null, "Unknown"));
    }

    @Test
    public void aNovelMatchesWhenItsStatusIsAnyOfTheChosenOnes() {
        Set<String> chosen = set(SearchFilters.ONGOING, SearchFilters.COMPLETED);

        assertTrue(SearchFilters.matchesAnyStatus(chosen, "Ongoing"));
        assertTrue(SearchFilters.matchesAnyStatus(chosen, "COMPLETED"));
        assertFalse(SearchFilters.matchesAnyStatus(chosen, "Hiatus"));
        assertFalse(SearchFilters.matchesAnyStatus(chosen, "Dropped"));
    }

    @Test
    public void sitesWordingsAreUnderstoodOnBothSides() {
        Set<String> chosen = set(SearchFilters.COMPLETED, SearchFilters.DROPPED);

        assertTrue(SearchFilters.matchesAnyStatus(chosen, "Finished"));
        assertTrue(SearchFilters.matchesAnyStatus(chosen, "Cancelled"));
        assertFalse(SearchFilters.matchesAnyStatus(chosen, "On-going"));
    }

    @Test
    public void withStatusesChosenAnUnknownStatusNeverMatches() {
        Set<String> chosen = set(SearchFilters.ONGOING, SearchFilters.HIATUS);

        assertFalse(SearchFilters.matchesAnyStatus(chosen, "Unknown"));
        assertFalse(SearchFilters.matchesAnyStatus(chosen, ""));
        assertFalse(SearchFilters.matchesAnyStatus(chosen, null));
    }

    @Test
    public void filteringKeepsTheOrderAndDropsWhatDoesNotMatch() {
        List<Novel> novels = Arrays.asList(novel("a", "Ongoing"), novel("b", "Hiatus"), novel("c", "Completed"),
                novel("d", null), novel("e", "Dropped"), novel("f", "Completed"));

        List<Novel> kept = SearchFilters.filterByStatuses(set(SearchFilters.COMPLETED, SearchFilters.DROPPED), novels);

        assertEquals(3, kept.size());
        assertEquals("c", kept.get(0).name);
        assertEquals("e", kept.get(1).name);
        assertEquals("f", kept.get(2).name);
    }

    @Test
    public void filteringWithAnyKeepsEverything() {
        List<Novel> novels = Arrays.asList(novel("a", "Ongoing"), novel("b", null));

        assertEquals(2, SearchFilters.filterByStatuses(Collections.<String>emptySet(), novels).size());
    }

    @Test
    public void orderedStatusesFollowTheListedOrderNotThePickedOrder() {
        List<String> ordered = SearchFilters.orderedStatuses(
                set(SearchFilters.DROPPED, SearchFilters.ONGOING, SearchFilters.HIATUS));

        assertEquals(Arrays.asList(SearchFilters.ONGOING, SearchFilters.HIATUS, SearchFilters.DROPPED), ordered);
    }

    @Test
    public void orderedStatusesUseTheStandardNamesAndDropWhatIsNotOne() {
        List<String> ordered = SearchFilters.orderedStatuses(set("completed", "nonsense", ""));

        assertEquals(Collections.singletonList(SearchFilters.COMPLETED), ordered);
    }

    @Test
    public void orderedStatusesOfNothingIsEmpty() {
        assertTrue(SearchFilters.orderedStatuses(null).isEmpty());
        assertTrue(SearchFilters.orderedStatuses(new HashSet<String>()).isEmpty());
    }
}
