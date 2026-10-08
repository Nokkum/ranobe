package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchHistoryTest {
    @Test
    public void newestSearchGoesFirst() {
        assertEquals(Arrays.asList("c", "a", "b"), SearchHistory.with(Arrays.asList("a", "b"), "c", 10));
    }

    @Test
    public void repeatingASearchMovesItToTheFrontWithoutDuplicates() {
        assertEquals(Arrays.asList("B", "a", "c"), SearchHistory.with(Arrays.asList("a", "b", "c"), "  B ", 10));
    }

    @Test
    public void listIsCapped() {
        List<String> current = new ArrayList<>(Arrays.asList("1", "2", "3"));

        assertEquals(Arrays.asList("new", "1", "2"), SearchHistory.with(current, "new", 3));
    }

    @Test
    public void blankSearchLeavesTheListAlone() {
        assertEquals(Arrays.asList("a", "b"), SearchHistory.with(Arrays.asList("a", "b"), "   ", 10));
        assertEquals(Arrays.asList("a", "b"), SearchHistory.with(Arrays.asList("a", "b"), null, 10));
    }

    @Test
    public void removingMatchesIgnoringCaseAndSpaces() {
        assertEquals(Arrays.asList("a", "c"), SearchHistory.without(Arrays.asList("a", "B", "c"), " b "));
        assertEquals(Arrays.asList("a"), SearchHistory.without(Arrays.asList("a"), "zzz"));
    }

    @Test
    public void storedFormSurvivesAWriteAndReadBack() {
        List<String> entries = Arrays.asList("dragon tamer", "\u043c\u0438\u0440", "quote \" inside");

        assertEquals(entries, SearchHistory.fromJson(SearchHistory.toJson(entries)));
    }

    @Test
    public void unreadableStoredFormGivesAnEmptyList() {
        assertTrue(SearchHistory.fromJson(null).isEmpty());
        assertTrue(SearchHistory.fromJson("").isEmpty());
        assertTrue(SearchHistory.fromJson("not json").isEmpty());
        assertTrue(SearchHistory.fromJson("{}").isEmpty());
        assertEquals(Arrays.asList("keep"), SearchHistory.fromJson("[\"\", \"  \", \"keep\"]"));
    }
}
