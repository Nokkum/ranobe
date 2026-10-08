package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NovelPingFilterTest {
    @Test
    public void onlyOngoingAndCompletedAreFilterable() {
        assertEquals("ongoing", NovelPing.statusParam("Ongoing"));
        assertEquals("completed", NovelPing.statusParam("Completed"));
        assertEquals("", NovelPing.statusParam("Hiatus"));
        assertEquals("", NovelPing.statusParam("Dropped"));
        assertEquals("", NovelPing.statusParam(null));

        NovelPing source = new NovelPing();
        assertTrue(source.supportsStatus("Ongoing"));
        assertTrue(source.supportsStatus("Completed"));
        assertFalse(source.supportsStatus("Hiatus"));
        assertFalse(source.supportsStatus("Dropped"));
    }

    @Test
    public void genresUseTheSitesCapitalsAndSpellings() {
        assertEquals("XIANXIA", NovelPing.genreParam("Xianxia"));
        assertEquals("MARTIAL ARTS", NovelPing.genreParam("martial arts"));
        assertEquals("SLICE OF LIFE", NovelPing.genreParam("Slice of Life"));
        assertEquals("LITRPG", NovelPing.genreParam("LitRPG"));
        assertEquals("SCI-FI", NovelPing.genreParam("Science Fiction"));
        assertEquals("SCHOOL LIFE", NovelPing.genreParam("School Life"));
    }

    @Test
    public void everyGenreWeOfferExistsOnTheSite() {
        for (String genre : org.ranobe.ranobe.util.SearchFilters.GENRES) {
            assertFalse(genre + " is missing", NovelPing.genreParam(genre).isEmpty());
        }
    }

    @Test
    public void aGenreTheSiteLacksGivesNothing() {
        assertEquals("", NovelPing.genreParam("Cooking"));
        assertEquals("", NovelPing.genreParam(""));
        assertEquals("", NovelPing.genreParam(null));
    }

    @Test
    public void theAddressPartCombinesStatusAndGenre() {
        assertEquals("&status=completed&genres=XIANXIA", NovelPing.filterQuery("Completed", "XIANXIA"));
        assertEquals("&genres=SLICE+OF+LIFE", NovelPing.filterQuery(null, "SLICE OF LIFE"));
        assertEquals("&genres=ANIME+%26+COMICS", NovelPing.filterQuery("", "ANIME & COMICS"));
        assertEquals("&status=ongoing", NovelPing.filterQuery("Ongoing", ""));
        assertEquals("", NovelPing.filterQuery(null, null));
    }

    @Test
    public void novelPingFiltersBothItself() {
        NovelPing source = new NovelPing();

        assertTrue(source.filtersStatusItself());
        assertFalse(source.reportsStatusInResults());
        assertTrue(source.supportsGenreFilter());
    }
}
