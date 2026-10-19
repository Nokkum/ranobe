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

    private static ReadHistory chapter(String novelUrl, String url, float number, long timestamp) {
        ReadHistory entry = new ReadHistory();
        entry.novelUrl = novelUrl;
        entry.url = url;
        entry.id = number;
        entry.timestamp = timestamp;
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

    // ---- what a history export holds: the last chapter read of each novel ----

    @Test
    public void lastRead_keepsOnlyTheLatestChapterOfEachNovel() {
        List<ReadHistory> all = Arrays.asList(
                chapter("https://example.org/a", "https://example.org/a/1", 1, 100L),
                chapter("https://example.org/a", "https://example.org/a/2", 2, 200L),
                chapter("https://example.org/a", "https://example.org/a/3", 3, 300L),
                chapter("https://example.org/b", "https://example.org/b/1", 1, 150L),
                chapter("https://example.org/b", "https://example.org/b/2", 2, 250L));

        List<ReadHistory> last = BackupMerge.lastReadPerNovel(all);

        assertEquals(2, last.size());
        assertEquals("https://example.org/a/3", last.get(0).url);
        assertEquals("https://example.org/b/2", last.get(1).url);
    }

    @Test
    public void lastRead_isTheChapterReadMostRecentlyNotTheHighestNumber() {
        // chapter 10 was read first, then the reader went back to chapter 3
        List<ReadHistory> last = BackupMerge.lastReadPerNovel(Arrays.asList(
                chapter("https://example.org/a", "https://example.org/a/10", 10, 100L),
                chapter("https://example.org/a", "https://example.org/a/3", 3, 200L)));

        assertEquals(1, last.size());
        assertEquals("https://example.org/a/3", last.get(0).url);
    }

    @Test
    public void lastRead_newestNovelComesFirst() {
        List<ReadHistory> last = BackupMerge.lastReadPerNovel(Arrays.asList(
                chapter("https://example.org/old", "https://example.org/old/1", 1, 100L),
                chapter("https://example.org/new", "https://example.org/new/1", 1, 900L),
                chapter("https://example.org/mid", "https://example.org/mid/1", 1, 500L)));

        assertEquals("https://example.org/new", last.get(0).novelUrl);
        assertEquals("https://example.org/mid", last.get(1).novelUrl);
        assertEquals("https://example.org/old", last.get(2).novelUrl);
    }

    @Test
    public void lastRead_chaptersMarkedAtTheSameMomentPickTheHigherNumber() {
        // marking a run of chapters as read in one go gives them all the same time
        List<ReadHistory> all = Arrays.asList(
                chapter("https://example.org/a", "https://example.org/a/5", 5, 100L),
                chapter("https://example.org/a", "https://example.org/a/7", 7, 100L),
                chapter("https://example.org/a", "https://example.org/a/6", 6, 100L));

        assertEquals("https://example.org/a/7", BackupMerge.lastReadPerNovel(all).get(0).url);
    }

    @Test
    public void lastRead_doesNotDependOnTheOrderOfTheList() {
        List<ReadHistory> all = new ArrayList<>(Arrays.asList(
                chapter("https://example.org/a", "https://example.org/a/x", 4, 100L),
                chapter("https://example.org/a", "https://example.org/a/y", 4, 100L),
                chapter("https://example.org/b", "https://example.org/b/1", 1, 100L),
                chapter("https://example.org/a", "https://example.org/a/z", 4, 100L)));
        List<ReadHistory> forward = BackupMerge.lastReadPerNovel(all);
        Collections.reverse(all);
        List<ReadHistory> backward = BackupMerge.lastReadPerNovel(all);

        assertEquals(forward.size(), backward.size());
        for (int i = 0; i < forward.size(); i++) {
            assertEquals(forward.get(i).url, backward.get(i).url);
        }
    }

    @Test
    public void lastRead_leavesOutRowsWithNoNovel() {
        ReadHistory orphan = chapter(null, "https://example.org/x/1", 1, 100L);
        ReadHistory blank = chapter("", "https://example.org/y/1", 1, 100L);

        List<ReadHistory> last = BackupMerge.lastReadPerNovel(Arrays.asList(orphan, blank,
                chapter("https://example.org/a", "https://example.org/a/1", 1, 100L)));

        assertEquals(1, last.size());
        assertEquals("https://example.org/a", last.get(0).novelUrl);
    }

    @Test
    public void lastRead_emptyHistoryGivesAnEmptyList() {
        assertTrue(BackupMerge.lastReadPerNovel(new ArrayList<ReadHistory>()).isEmpty());
    }

    @Test
    public void lastRead_theFileHoldsOneEntryPerNovelAndNoOtherChapters() throws Exception {
        List<ReadHistory> all = new ArrayList<>();
        for (int i = 1; i <= 40; i++) all.add(chapter("https://example.org/a", "https://example.org/a/" + i, i, i * 10L));
        // novel B was read after novel A, so it comes first
        for (int i = 1; i <= 25; i++) all.add(chapter("https://example.org/b", "https://example.org/b/" + i, i, 1000L + i * 10L));

        String json = BackupCodec.historyToJson(BackupMerge.lastReadPerNovel(all), 1L);
        BackupCodec.Parsed<ReadHistory> parsed = BackupCodec.parseHistory(json);

        assertEquals(2, parsed.items.size());
        assertEquals("https://example.org/b/25", parsed.items.get(0).url);
        assertEquals("https://example.org/a/40", parsed.items.get(1).url);
        assertTrue(!json.contains("https://example.org/a/39"));
    }

    @Test
    public void lastRead_aFileOfOnlyLastChaptersStillImportsCorrectly() {
        // the device has read novel A up to chapter 5; the file says novel A was last read at chapter 8 and
        // novel B at chapter 2. Both land, and each novel's latest chapter is the most recently read one.
        Map<String, Long> onDevice = new HashMap<>();
        onDevice.put("https://example.org/a/5", 500L);

        BackupMerge.HistoryPlan plan = BackupMerge.planHistory(onDevice, Arrays.asList(
                chapter("https://example.org/a", "https://example.org/a/8", 8, 900L),
                chapter("https://example.org/b", "https://example.org/b/2", 2, 400L)));

        assertEquals(2, plan.added);
        assertEquals(0, plan.kept);
    }
}
