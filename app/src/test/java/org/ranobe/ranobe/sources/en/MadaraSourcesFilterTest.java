package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.sources.SearchFilterSupport;

public class MadaraSourcesFilterTest {
    private static void assertMadaraSupport(SearchFilterSupport source) {
        assertTrue(source.filtersStatusItself());
        assertFalse(source.reportsStatusInResults());
        assertFalse(source.supportsGenreFilter());
        assertTrue(source.reportsGenresInResults());
        assertTrue(source.supportsStatus("Ongoing"));
        assertTrue(source.supportsStatus("Completed"));
        assertTrue(source.supportsStatus("Hiatus"));
        assertTrue(source.supportsStatus("Dropped"));
    }

    @Test
    public void lightNovelHeavenUsesTheThemesFilters() {
        assertMadaraSupport(new LightNovelHeaven());
    }

    @Test
    public void wuxiaWorldUsesTheThemesFilters() {
        assertMadaraSupport(new WuxiaWorld());
    }

    @Test
    public void wordRain69UsesTheThemesFilters() {
        assertMadaraSupport(new WordRain69());
    }
}
