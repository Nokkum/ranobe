package org.ranobe.ranobe.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Chapter;

import java.util.ArrayList;
import java.util.List;

public class PausedDownloadsTest {
    @Test
    public void entriesSurviveAWriteAndReadBack() {
        List<PausedDownloads.Entry> entries = new ArrayList<>();
        entries.add(new PausedDownloads.Entry(
                "https://wtr-lab.com/en/novel/1/a/chapter-11?chapter_id=900",
                "https://wtr-lab.com/en/novel/1/a", "Chapter 11: Dawn", 11f, 21));
        entries.add(new PausedDownloads.Entry("https://example.net/c/12", "", "Chapter 12", 12.5f, 3));

        List<PausedDownloads.Entry> back = PausedDownloads.fromJson(PausedDownloads.toJson(entries));

        assertEquals(2, back.size());
        assertEquals(entries.get(0).url, back.get(0).url);
        assertEquals(entries.get(0).novelUrl, back.get(0).novelUrl);
        assertEquals("Chapter 11: Dawn", back.get(0).name);
        assertEquals(11f, back.get(0).id, 0f);
        assertEquals(21, back.get(0).sourceId);
        assertEquals(12.5f, back.get(1).id, 0f);
        assertEquals(3, back.get(1).sourceId);
    }

    @Test
    public void orderIsKeptSoTheChapterThatHitTheCheckComesFirst() {
        List<PausedDownloads.Entry> entries = new ArrayList<>();
        for (int i = 11; i <= 15; i++) {
            entries.add(new PausedDownloads.Entry("u" + i, "n", "c" + i, i, 21));
        }

        List<PausedDownloads.Entry> back = PausedDownloads.fromJson(PausedDownloads.toJson(entries));

        for (int i = 0; i < back.size(); i++) assertEquals("u" + (11 + i), back.get(i).url);
    }

    @Test
    public void unreadableOrIncompleteInputGivesAnEmptyOrShorterList() {
        assertTrue(PausedDownloads.fromJson(null).isEmpty());
        assertTrue(PausedDownloads.fromJson("").isEmpty());
        assertTrue(PausedDownloads.fromJson("not json").isEmpty());
        assertTrue(PausedDownloads.fromJson("{}").isEmpty());
        assertEquals(1, PausedDownloads.fromJson("[{\"s\":1},{\"u\":\"keep\",\"s\":2}]").size());
    }

    @Test
    public void restoredEntryBecomesAChapterTheSourceCanFetch() {
        PausedDownloads.Entry entry = new PausedDownloads.Entry("url", "novel", "Name", 7f, 21);

        Chapter chapter = entry.toChapter();

        assertEquals("url", chapter.url);
        assertEquals("novel", chapter.novelUrl);
        assertEquals("Name", chapter.name);
        assertEquals(7f, chapter.id, 0f);
        assertNull(chapter.content);
        assertNull(new PausedDownloads.Entry("url", null, null, 1f, 1).toChapter().novelUrl);
    }
}
