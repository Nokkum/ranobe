package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.sources.SearchFilterSupport;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// Each fake has the capabilities of a real source, so these read as "what happens with source X".
public class SearchPlanTest {
    private static final Set<String> NONE = Collections.emptySet();

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    // WuxiaWorld, LightNovelHeaven, WordRain69: filter status themselves (one at a time), genre too, and
    // their cards carry a chapter count
    private static final SearchFilterSupport MADARA = new SearchFilterSupport() {
        public boolean filtersStatusItself() { return true; }
        public boolean reportsStatusInResults() { return false; }
        public boolean supportsGenreFilter() { return true; }
        public boolean reportsGenresInResults() { return true; }
        public boolean reportsChapterCountInResults() { return true; }
    };

    // WtrLab: no filter of its own, but its results carry status and chapter count
    private static final SearchFilterSupport WTRLAB = new SearchFilterSupport() {
        public boolean filtersStatusItself() { return false; }
        public boolean reportsStatusInResults() { return true; }
        public boolean supportsGenreFilter() { return false; }
        public boolean reportsChapterCountInResults() { return true; }
    };

    // RoyalRoad: filters status itself, nothing for genre, reports chapters
    private static final SearchFilterSupport ROYAL_ROAD = new SearchFilterSupport() {
        public boolean filtersStatusItself() { return true; }
        public boolean reportsStatusInResults() { return false; }
        public boolean supportsGenreFilter() { return false; }
        public boolean reportsChapterCountInResults() { return true; }
    };

    // AllNovel: results carry status, but only "completed" can be told from the rest
    private static final SearchFilterSupport ALL_NOVEL = new SearchFilterSupport() {
        public boolean filtersStatusItself() { return false; }
        public boolean reportsStatusInResults() { return true; }
        public boolean supportsGenreFilter() { return true; }
        @Override public boolean supportsStatus(String status) { return SearchFilters.COMPLETED.equals(status); }
    };

    // DivineDaoLibrary: genre only
    private static final SearchFilterSupport GENRE_ONLY = new SearchFilterSupport() {
        public boolean filtersStatusItself() { return false; }
        public boolean reportsStatusInResults() { return false; }
        public boolean supportsGenreFilter() { return true; }
    };

    // ---- nothing set ----

    @Test
    public void withNoFilterEverySourceTakesPartAndNothingIsFilteredHere() {
        for (SearchFilterSupport support : new SearchFilterSupport[]{MADARA, WTRLAB, ROYAL_ROAD, ALL_NOVEL, GENRE_ONLY, null}) {
            SearchPlan plan = SearchPlan.forSource(support, NONE, "", NONE);

            assertTrue(plan.honored);
            assertFalse(plan.filterStatusHere);
            assertFalse(plan.filterGenreHere);
            assertFalse(plan.filterChaptersHere);
            assertTrue(plan.nativeStatuses.isEmpty());
        }
    }

    // ---- status ----

    @Test
    public void oneStatusIsPutToASourceThatFiltersItself() {
        SearchPlan plan = SearchPlan.forSource(MADARA, set(SearchFilters.COMPLETED), "", NONE);

        assertTrue(plan.honored);
        assertEquals(Arrays.asList(SearchFilters.COMPLETED), plan.nativeStatuses);
        assertFalse(plan.filterStatusHere);
    }

    @Test
    public void severalStatusesBecomeOneRequestEachForASourceThatFiltersItself() {
        SearchPlan plan = SearchPlan.forSource(ROYAL_ROAD,
                set(SearchFilters.DROPPED, SearchFilters.ONGOING), "", NONE);

        assertTrue(plan.honored);
        // in the standard order, whatever order they were picked in
        assertEquals(Arrays.asList(SearchFilters.ONGOING, SearchFilters.DROPPED), plan.nativeStatuses);
        assertFalse(plan.filterStatusHere);
    }

    @Test
    public void severalStatusesAreFilteredHereForASourceThatOnlyReportsThem() {
        SearchPlan plan = SearchPlan.forSource(WTRLAB, set(SearchFilters.ONGOING, SearchFilters.HIATUS), "", NONE);

        assertTrue(plan.honored);
        assertTrue(plan.filterStatusHere);
        assertTrue(plan.nativeStatuses.isEmpty());
    }

    @Test
    public void aSourceThatCanTellOnlySomeStatusesApartIsUsedOnlyIfItCanTellThemAll() {
        assertTrue(SearchPlan.forSource(ALL_NOVEL, set(SearchFilters.COMPLETED), "", NONE).honored);
        assertFalse(SearchPlan.forSource(ALL_NOVEL, set(SearchFilters.ONGOING), "", NONE).honored);
        // one it can't tell is enough to leave it out, rather than return only part of what was asked for
        assertFalse(SearchPlan.forSource(ALL_NOVEL, set(SearchFilters.COMPLETED, SearchFilters.ONGOING), "", NONE).honored);
    }

    @Test
    public void aSourceWithNoStatusSupportIsSkippedWheneverAStatusIsChosen() {
        assertFalse(SearchPlan.forSource(GENRE_ONLY, set(SearchFilters.ONGOING), "", NONE).honored);
        assertFalse(SearchPlan.forSource(null, set(SearchFilters.ONGOING), "", NONE).honored);
    }

    // ---- genre ----

    @Test
    public void aGenreIsLeftToASourceThatFiltersItAndFilteredHereOtherwise() {
        SearchPlan native_ = SearchPlan.forSource(GENRE_ONLY, NONE, "Fantasy", NONE);
        assertTrue(native_.honored);
        assertFalse(native_.filterGenreHere);

        // supports the filter itself, so the app has nothing to add even though it also reports genres
        SearchPlan madara = SearchPlan.forSource(MADARA, NONE, "Fantasy", NONE);
        assertTrue(madara.honored);
        assertFalse(madara.filterGenreHere);

        SearchFilterSupport reportsOnly = new SearchFilterSupport() {
            public boolean filtersStatusItself() { return false; }
            public boolean reportsStatusInResults() { return false; }
            public boolean supportsGenreFilter() { return false; }
            @Override public boolean reportsGenresInResults() { return true; }
        };
        SearchPlan here = SearchPlan.forSource(reportsOnly, NONE, "Fantasy", NONE);
        assertTrue(here.honored);
        assertTrue(here.filterGenreHere);
    }

    @Test
    public void aSourceThatCannotDoGenreIsSkippedWhenOneIsChosen() {
        assertFalse(SearchPlan.forSource(WTRLAB, NONE, "Fantasy", NONE).honored);
        assertFalse(SearchPlan.forSource(ROYAL_ROAD, NONE, "Fantasy", NONE).honored);
        assertFalse(SearchPlan.forSource(null, NONE, "Fantasy", NONE).honored);
    }

    // ---- chapter ranges ----

    @Test
    public void chapterRangesAreFilteredHereForSourcesThatReportACount() {
        for (SearchFilterSupport support : new SearchFilterSupport[]{MADARA, WTRLAB, ROYAL_ROAD}) {
            SearchPlan plan = SearchPlan.forSource(support, NONE, "", set("1-100"));

            assertTrue(plan.honored);
            assertTrue(plan.filterChaptersHere);
        }
    }

    @Test
    public void aSourceThatReportsNoCountIsSkippedWhenARangeIsChosen() {
        assertFalse(SearchPlan.forSource(ALL_NOVEL, NONE, "", set("1-100")).honored);
        assertFalse(SearchPlan.forSource(GENRE_ONLY, NONE, "", set("101-500")).honored);
        assertFalse(SearchPlan.forSource(null, NONE, "", set("1-100")).honored);
    }

    // ---- together ----

    @Test
    public void everyFilterSetAtOnceNeedsASourceThatCanDoAllOfThem() {
        Set<String> statuses = set(SearchFilters.ONGOING, SearchFilters.COMPLETED);
        Set<String> ranges = set("501-1000", "1001-1500");

        SearchPlan madara = SearchPlan.forSource(MADARA, statuses, "Fantasy", ranges);
        assertTrue(madara.honored);
        assertEquals(Arrays.asList(SearchFilters.ONGOING, SearchFilters.COMPLETED), madara.nativeStatuses);
        assertTrue(madara.filterChaptersHere);
        assertFalse(madara.filterGenreHere);

        // RoyalRoad has no genre filter, WtrLab neither: both are left out once a genre is added
        assertFalse(SearchPlan.forSource(ROYAL_ROAD, statuses, "Fantasy", ranges).honored);
        assertFalse(SearchPlan.forSource(WTRLAB, statuses, "Fantasy", ranges).honored);
        // and AllNovel has no chapter count
        assertFalse(SearchPlan.forSource(ALL_NOVEL, set(SearchFilters.COMPLETED), "Fantasy", ranges).honored);
    }

    @Test
    public void aSkippedSourceCarriesNoWork() {
        SearchPlan plan = SearchPlan.forSource(null, set(SearchFilters.ONGOING), "", NONE);

        assertFalse(plan.honored);
        assertTrue(plan.nativeStatuses.isEmpty());
        assertFalse(plan.filterStatusHere);
        assertFalse(plan.filterChaptersHere);
    }
}
