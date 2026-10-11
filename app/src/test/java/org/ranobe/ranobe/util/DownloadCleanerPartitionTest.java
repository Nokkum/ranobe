package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DownloadCleanerPartitionTest {
    @Test
    public void itemsAreSplitIntoOrderedSlices() {
        List<List<Integer>> batches = DownloadCleaner.partition(Arrays.asList(1, 2, 3, 4, 5), 2);

        assertEquals(3, batches.size());
        assertEquals(Arrays.asList(1, 2), batches.get(0));
        assertEquals(Arrays.asList(3, 4), batches.get(1));
        assertEquals(Arrays.asList(5), batches.get(2));
    }

    @Test
    public void aListThatFitsStaysInOneSlice() {
        assertEquals(1, DownloadCleaner.partition(Arrays.asList("a", "b"), 400).size());
        assertEquals(1, DownloadCleaner.partition(Arrays.asList("a", "b"), 2).size());
    }

    @Test
    public void anEmptyListHasNoSlices() {
        assertTrue(DownloadCleaner.partition(new ArrayList<String>(), 10).isEmpty());
    }

    @Test
    public void everyItemLandsInExactlyOneSlice() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 1234; i++) items.add(i);

        int total = 0;
        for (List<Integer> batch : DownloadCleaner.partition(items, 400)) {
            assertTrue(batch.size() <= 400);
            total += batch.size();
        }
        assertEquals(1234, total);
    }
}
