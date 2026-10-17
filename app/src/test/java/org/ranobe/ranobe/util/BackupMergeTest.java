package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class BackupMergeTest {

    private static Novel novel(String url, String name) {
        Novel novel = new Novel(url);
        novel.name = name;
        return novel;
    }

    private static ReadHistory entry(String url, long timestamp, int position) {
        ReadHistory entry = new ReadHistory();
        entry.url = url;
        entry.novelUrl = "https://example.org/n";
        entry.timestamp = timestamp;
        entry.position = position;
        return entry;
    }

    // ---- library ----

    @Test
    public void library_savesOnlyNovelsThatAreNew() {
        BackupMerge.LibraryPlan plan = BackupMerge.planLibrary(
                new HashSet<>(Arrays.asList("https://example.org/a", "https://example.org/b")),
                Arrays.asList(novel("https://example.org/a", "A"), novel("https://example.org/c", "C")));

        assertEquals(1, plan.toSave.size());
        assertEquals("https://example.org/c", plan.toSave.get(0).url);
        assertEquals(1, plan.alreadySaved);
    }

    @Test
    public void library_importingIntoAnEmptyLibrarySavesEverything() {
        BackupMerge.LibraryPlan plan = BackupMerge.planLibrary(new HashSet<String>(),
                Arrays.asList(novel("https://example.org/a", "A"), novel("https://example.org/b", "B")));

        assertEquals(2, plan.toSave.size());
        assertEquals(0, plan.alreadySaved);
    }

    @Test
    public void library_aNovelListedTwiceInTheFileIsSavedOnce() {
        BackupMerge.LibraryPlan plan = BackupMerge.planLibrary(new HashSet<String>(),
                Arrays.asList(novel("https://example.org/a", "first"), novel("https://example.org/a", "second")));

        assertEquals(1, plan.toSave.size());
        assertEquals("first", plan.toSave.get(0).name);
        assertEquals(1, plan.alreadySaved);
    }

    @Test
    public void library_importingTheSameFileAgainChangesNothing() {
        List<Novel> file = Arrays.asList(novel("https://example.org/a", "A"), novel("https://example.org/b", "B"));
        HashSet<String> afterFirstImport = new HashSet<>();
        for (Novel novel : BackupMerge.planLibrary(new HashSet<String>(), file).toSave) afterFirstImport.add(novel.url);

        BackupMerge.LibraryPlan again = BackupMerge.planLibrary(afterFirstImport, file);

        assertTrue(again.toSave.isEmpty());
        assertEquals(2, again.alreadySaved);
    }

    // ---- history ----

    @Test
    public void history_aChapterNotOnTheDeviceIsAdded() {
        BackupMerge.HistoryPlan plan = BackupMerge.planHistory(new HashMap<String, Long>(),
                Collections.singletonList(entry("https://example.org/c1", 100L, 5)));

        assertEquals(1, plan.toSave.size());
        assertEquals(1, plan.added);
        assertEquals(0, plan.updated);
        assertEquals(0, plan.kept);
    }

    @Test
    public void history_theLaterReadingWins() {
        Map<String, Long> onDevice = new HashMap<>();
        onDevice.put("https://example.org/newerHere", 500L);
        onDevice.put("https://example.org/newerInFile", 100L);
        onDevice.put("https://example.org/same", 300L);

        BackupMerge.HistoryPlan plan = BackupMerge.planHistory(onDevice, Arrays.asList(
                entry("https://example.org/newerHere", 400L, 1),
                entry("https://example.org/newerInFile", 200L, 2),
                entry("https://example.org/same", 300L, 3)));

        assertEquals(1, plan.toSave.size());
        assertEquals("https://example.org/newerInFile", plan.toSave.get(0).url);
        assertEquals(0, plan.added);
        assertEquals(1, plan.updated);
        assertEquals(2, plan.kept);
    }

    @Test
    public void history_aChapterListedTwiceInTheFileKeepsItsLatestEntry() {
        BackupMerge.HistoryPlan plan = BackupMerge.planHistory(new HashMap<String, Long>(), Arrays.asList(
                entry("https://example.org/c1", 100L, 1),
                entry("https://example.org/c1", 300L, 9),
                entry("https://example.org/c1", 200L, 5)));

        assertEquals(1, plan.toSave.size());
        assertEquals(9, plan.toSave.get(0).position);
        assertEquals(1, plan.added);
    }

    @Test
    public void history_importingTheSameFileAgainChangesNothing() {
        List<ReadHistory> file = Arrays.asList(entry("https://example.org/c1", 100L, 1), entry("https://example.org/c2", 200L, 2));
        Map<String, Long> afterFirstImport = new HashMap<>();
        for (ReadHistory entry : BackupMerge.planHistory(new HashMap<String, Long>(), file).toSave) {
            afterFirstImport.put(entry.url, entry.timestamp);
        }

        BackupMerge.HistoryPlan again = BackupMerge.planHistory(afterFirstImport, file);

        assertTrue(again.toSave.isEmpty());
        assertEquals(2, again.kept);
    }

    @Test
    public void history_anOldFileNeverOverwritesNewerReading() {
        Map<String, Long> onDevice = new HashMap<>();
        onDevice.put("https://example.org/c1", 9_000L);

        BackupMerge.HistoryPlan plan = BackupMerge.planHistory(onDevice,
                Collections.singletonList(entry("https://example.org/c1", 1_000L, 1)));

        assertTrue(plan.toSave.isEmpty());
        assertEquals(1, plan.kept);
        assertEquals(new ArrayList<ReadHistory>(), plan.toSave);
    }
}
