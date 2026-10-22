package org.ranobe.ranobe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class OptionPicksTest {
    private static final List<String> STATUSES = Arrays.asList("Ongoing", "Completed", "Hiatus", "Dropped");

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @Test
    public void pickingNothingIsAll() {
        assertTrue(OptionPicks.isAll(Collections.<String>emptySet(), STATUSES));
    }

    @Test
    public void pickingEveryOptionIsAll() {
        assertTrue(OptionPicks.isAll(set("Ongoing", "Completed", "Hiatus", "Dropped"), STATUSES));
    }

    @Test
    public void theOrderTheyWerePickedInDoesNotMatter() {
        assertTrue(OptionPicks.isAll(set("Dropped", "Ongoing", "Hiatus", "Completed"), STATUSES));
    }

    @Test
    public void pickingSomeButNotAllIsANarrowing() {
        assertFalse(OptionPicks.isAll(set("Ongoing"), STATUSES));
        assertFalse(OptionPicks.isAll(set("Ongoing", "Completed", "Hiatus"), STATUSES));
    }

    @Test
    public void oneMissingOptionIsEnoughToKeepItANarrowing() {
        // three of four, plus something that isn't an option: still not every option
        assertFalse(OptionPicks.isAll(set("Ongoing", "Completed", "Hiatus", "Stub"), STATUSES));
    }

    @Test
    public void anIdThatIsNoLongerAnOptionDoesNotStopItCountingAsEverything() {
        // a source picked earlier and switched off since is still in the picks
        assertTrue(OptionPicks.isAll(set("1", "2", "3", "gone"), Arrays.asList("1", "2", "3")));
    }

    @Test
    public void aSingleOptionPickedIsAll() {
        // with one source enabled, "that source" and "all sources" are the same search
        assertTrue(OptionPicks.isAll(set("7"), Collections.singletonList("7")));
    }

    @Test
    public void noOptionsAtAllIsAll() {
        assertTrue(OptionPicks.isAll(Collections.<String>emptySet(), Collections.<String>emptyList()));
    }
}
