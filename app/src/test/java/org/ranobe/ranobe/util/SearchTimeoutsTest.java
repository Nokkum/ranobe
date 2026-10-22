package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SearchTimeoutsTest {
    @Test
    public void theOptionsAre15To30SecondsInFives() {
        assertEquals(4, SearchTimeouts.OPTIONS.length);
        assertEquals(15, SearchTimeouts.OPTIONS[0]);
        assertEquals(20, SearchTimeouts.OPTIONS[1]);
        assertEquals(25, SearchTimeouts.OPTIONS[2]);
        assertEquals(30, SearchTimeouts.OPTIONS[3]);
    }

    @Test
    public void theDefaultIsOneOfTheOptions() {
        assertEquals(SearchTimeouts.DEFAULT, SearchTimeouts.normalize(SearchTimeouts.DEFAULT));
    }

    @Test
    public void everyOptionIsKept() {
        for (int option : SearchTimeouts.OPTIONS) assertEquals(option, SearchTimeouts.normalize(option));
    }

    @Test
    public void anythingElseBecomesTheDefault() {
        assertEquals(SearchTimeouts.DEFAULT, SearchTimeouts.normalize(0));
        assertEquals(SearchTimeouts.DEFAULT, SearchTimeouts.normalize(-5));
        assertEquals(SearchTimeouts.DEFAULT, SearchTimeouts.normalize(16));
        assertEquals(SearchTimeouts.DEFAULT, SearchTimeouts.normalize(60));
        assertEquals(SearchTimeouts.DEFAULT, SearchTimeouts.normalize(Integer.MAX_VALUE));
    }

    @Test
    public void millisIsTheNormalizedSecondsTimes1000() {
        assertEquals(15_000L, SearchTimeouts.millis(15));
        assertEquals(30_000L, SearchTimeouts.millis(30));
        assertEquals(SearchTimeouts.DEFAULT * 1000L, SearchTimeouts.millis(7));
    }

    @Test
    public void indexOfFindsTheOptionOrTheDefault() {
        assertEquals(0, SearchTimeouts.indexOf(15));
        assertEquals(3, SearchTimeouts.indexOf(30));
        assertEquals(SearchTimeouts.indexOf(SearchTimeouts.DEFAULT), SearchTimeouts.indexOf(99));
    }
}
