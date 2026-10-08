package org.ranobe.ranobe.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilterTest {
    @Test
    public void statusAndGenreAreOptional() {
        Filter filter = new Filter();
        filter.addFilter(Filter.FILTER_KEYWORD, "dragon");

        assertTrue(filter.hashKeyword());
        assertFalse(filter.hasStatus());
        assertFalse(filter.hasGenre());

        filter.addFilter(Filter.FILTER_STATUS, "Completed");
        filter.addFilter(Filter.FILTER_GENRE, "Xianxia");

        assertTrue(filter.hasStatus());
        assertEquals("Completed", filter.getStatus());
        assertTrue(filter.hasGenre());
        assertEquals("Xianxia", filter.getGenre());
    }

    @Test
    public void anEmptyValueCountsAsNotSet() {
        Filter filter = new Filter();
        filter.addFilter(Filter.FILTER_STATUS, "");
        filter.addFilter(Filter.FILTER_GENRE, "");

        assertFalse(filter.hasStatus());
        assertFalse(filter.hasGenre());
    }

    @Test
    public void filtersWithDifferentOptionsAreNotEqual() {
        Filter plain = new Filter();
        plain.addFilter(Filter.FILTER_KEYWORD, "dragon");
        Filter withStatus = new Filter();
        withStatus.addFilter(Filter.FILTER_KEYWORD, "dragon");
        withStatus.addFilter(Filter.FILTER_STATUS, "Completed");

        assertNotEquals(plain, withStatus);
    }
}
