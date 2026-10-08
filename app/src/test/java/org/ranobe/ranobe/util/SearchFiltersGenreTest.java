package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchFiltersGenreTest {
    private static Novel novel(String name, String... genres) {
        Novel novel = new Novel("https://example.net/" + name);
        novel.name = name;
        novel.genres = new ArrayList<>(Arrays.asList(genres));
        return novel;
    }

    @Test
    public void genresMatchWhateverTheSpellingOrCase() {
        assertTrue(SearchFilters.matchesGenre("Martial Arts", Arrays.asList("Action", "Martial Arts")));
        assertTrue(SearchFilters.matchesGenre("slice-of-life", Arrays.asList("Slice of Life")));
        assertTrue(SearchFilters.matchesGenre("LitRPG", Arrays.asList("LITRPG")));
        assertTrue(SearchFilters.matchesGenre("Science Fiction", Arrays.asList("Romance", "Sci-fi")));
        assertTrue(SearchFilters.matchesGenre("Sci-Fi", Arrays.asList("Science Fiction")));
    }

    @Test
    public void aGenreThatIsNotThereDoesNotMatch() {
        assertFalse(SearchFilters.matchesGenre("Action", Arrays.asList("Adventure", "Fantasy")));
        assertFalse(SearchFilters.matchesGenre("Action", new ArrayList<String>()));
        assertFalse(SearchFilters.matchesGenre("Action", null));
    }

    @Test
    public void noGenreWantedMatchesEverything() {
        assertTrue(SearchFilters.matchesGenre("", Arrays.asList("Action")));
        assertTrue(SearchFilters.matchesGenre(null, null));
    }

    @Test
    public void filteringByGenreKeepsTheOrder() {
        List<Novel> novels = Arrays.asList(
                novel("a", "Action"), novel("b", "Romance"), novel("c", "Fantasy", "Action"), novel("d"));

        List<String> names = new ArrayList<>();
        for (Novel novel : SearchFilters.filterByGenre("Action", novels)) names.add(novel.name);

        assertEquals(Arrays.asList("a", "c"), names);
        assertEquals(4, SearchFilters.filterByGenre("", novels).size());
    }

    @Test
    public void onHoldCountsAsHiatusAndTheThemeSpellingsAreUnderstood() {
        assertEquals(SearchFilters.HIATUS, SearchFilters.canonicalStatus("On Hold"));
        assertEquals(SearchFilters.HIATUS, SearchFilters.canonicalStatus("on-hold"));
        assertEquals(SearchFilters.ONGOING, SearchFilters.canonicalStatus("OnGoing"));
        assertEquals(SearchFilters.DROPPED, SearchFilters.canonicalStatus("Canceled"));
        assertEquals(SearchFilters.COMPLETED, SearchFilters.canonicalStatus("Completed"));
    }
}
