package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchFiltersTest {
    private static Novel novel(String name, String status) {
        Novel novel = new Novel("https://example.net/" + name);
        novel.name = name;
        novel.status = status;
        return novel;
    }

    @Test
    public void sitesWordingsOfAStatusAreMappedToTheFourWeFilterBy() {
        assertEquals(SearchFilters.ONGOING, SearchFilters.canonicalStatus("ONGOING"));
        assertEquals(SearchFilters.ONGOING, SearchFilters.canonicalStatus("On-going"));
        assertEquals(SearchFilters.COMPLETED, SearchFilters.canonicalStatus("Completed"));
        assertEquals(SearchFilters.COMPLETED, SearchFilters.canonicalStatus("complete"));
        assertEquals(SearchFilters.COMPLETED, SearchFilters.canonicalStatus("Finished"));
        assertEquals(SearchFilters.HIATUS, SearchFilters.canonicalStatus("Hiatus"));
        assertEquals(SearchFilters.DROPPED, SearchFilters.canonicalStatus("Dropped"));
        assertEquals(SearchFilters.DROPPED, SearchFilters.canonicalStatus("Cancelled"));
        assertEquals(SearchFilters.DROPPED, SearchFilters.canonicalStatus("abandoned"));
    }

    @Test
    public void anythingElseHasNoStatus() {
        assertEquals("", SearchFilters.canonicalStatus("Unknown"));
        assertEquals("", SearchFilters.canonicalStatus("Stub"));
        assertEquals("", SearchFilters.canonicalStatus(""));
        assertEquals("", SearchFilters.canonicalStatus(null));
    }

    @Test
    public void noStatusWantedMatchesEverything() {
        assertTrue(SearchFilters.matchesStatus("", "Completed"));
        assertTrue(SearchFilters.matchesStatus("", null));
        assertTrue(SearchFilters.matchesStatus(null, "Unknown"));
    }

    @Test
    public void aWantedStatusNeedsAKnownMatchingStatus() {
        assertTrue(SearchFilters.matchesStatus("Completed", "COMPLETED"));
        assertTrue(SearchFilters.matchesStatus("Completed", "Complete"));
        assertFalse(SearchFilters.matchesStatus("Completed", "Ongoing"));
        assertFalse(SearchFilters.matchesStatus("Completed", "Unknown"));
        assertFalse(SearchFilters.matchesStatus("Completed", null));
    }

    @Test
    public void filteringKeepsTheOrderAndDropsWhatDoesNotMatch() {
        List<Novel> novels = Arrays.asList(
                novel("a", "Completed"), novel("b", "Ongoing"), novel("c", "Unknown"), novel("d", "Completed"));

        List<String> names = new ArrayList<>();
        for (Novel novel : SearchFilters.filterByStatus("Completed", novels)) names.add(novel.name);

        assertEquals(Arrays.asList("a", "d"), names);
        assertEquals(4, SearchFilters.filterByStatus("", novels).size());
    }

    @Test
    public void offeredValuesAreTheOnesTheMappingUnderstands() {
        for (String status : SearchFilters.STATUSES) {
            assertEquals(status, SearchFilters.canonicalStatus(status));
        }
        assertTrue(SearchFilters.GENRES.contains("Xianxia"));
    }
}
