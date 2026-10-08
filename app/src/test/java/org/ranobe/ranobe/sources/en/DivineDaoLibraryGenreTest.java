package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class DivineDaoLibraryGenreTest {
    // the shape of https://www.divinedaolibrary.com/wp-json/wp/v2/fcn_genre?_fields=id,name, shortened
    private static final String GENRES = "[{\"id\":6862,\"name\":\"Action\"},{\"id\":6867,\"name\":\"Science Fiction\"},"
            + "{\"id\":6878,\"name\":\"Children\\u2019s\"},{\"id\":6930,\"name\":\"Suicide \\/ Self Harm\"},"
            + "{\"id\":6942,\"name\":\"Xianxia\"},{\"id\":0,\"name\":\"No id\"},{\"id\":7,\"name\":\"\"}]";

    @Test
    public void genreNamesMapToTheirTermIds() {
        Map<String, Integer> genres = DivineDaoLibrary.parseGenres(GENRES);

        assertEquals(Integer.valueOf(6942), genres.get(DivineDaoLibrary.normalizeGenre("Xianxia")));
        assertEquals(Integer.valueOf(6867), genres.get(DivineDaoLibrary.normalizeGenre("  science fiction ")));
        assertEquals(Integer.valueOf(6878), genres.get(DivineDaoLibrary.normalizeGenre("Children\u2019s")));
        assertEquals(Integer.valueOf(6930), genres.get(DivineDaoLibrary.normalizeGenre("Suicide / Self Harm")));
    }

    @Test
    public void entriesWithoutAUsableNameOrIdAreSkipped() {
        Map<String, Integer> genres = DivineDaoLibrary.parseGenres(GENRES);

        assertEquals(5, genres.size());
        assertNull(genres.get("no id"));
    }

    @Test
    public void anUnknownGenreHasNoId() {
        assertNull(DivineDaoLibrary.parseGenres(GENRES).get(DivineDaoLibrary.normalizeGenre("Cooking")));
    }

    @Test
    public void unreadableGenreListGivesAnEmptyMap() {
        assertTrue(DivineDaoLibrary.parseGenres(null).isEmpty());
        assertTrue(DivineDaoLibrary.parseGenres("").isEmpty());
        assertTrue(DivineDaoLibrary.parseGenres("<html></html>").isEmpty());
        assertTrue(DivineDaoLibrary.parseGenres("{\"code\":\"rest_no_route\"}").isEmpty());
    }

    @Test
    public void divineDaoLibraryFiltersGenreOnly() {
        DivineDaoLibrary source = new DivineDaoLibrary();

        assertTrue(source.supportsGenreFilter());
        assertFalse(source.filtersStatusItself());
        assertFalse(source.reportsStatusInResults());
    }
}
