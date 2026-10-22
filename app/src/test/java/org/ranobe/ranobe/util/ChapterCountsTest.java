package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChapterCountsTest {
    @Test
    public void aLatestChapterLabelGivesItsNumber() {
        assertEquals(531, ChapterCounts.fromLabel("Chapter 531"));
        assertEquals(3121, ChapterCounts.fromLabel("Chapter 3121"));
        assertEquals(1186, ChapterCounts.fromLabel("Chapter 1186"));
    }

    @Test
    public void labelsAsRealSitesWriteThem() {
        // all seen on live search results
        assertEquals(1234, ChapterCounts.fromLabel("Chapter 1234 [END]"));
        assertEquals(1159, ChapterCounts.fromLabel("Chapter 1159 (END) - The End"));
        assertEquals(302, ChapterCounts.fromLabel("Chapter 302 - Daughter of the Republic VS Chinese Opera Singer (End)"));
        assertEquals(414, ChapterCounts.fromLabel("Chapter 414 (end)"));
        assertEquals(543, ChapterCounts.fromLabel("Chapter 543 (END) - With You, Every Day Is A Good Day (The Final Chapter)"));
    }

    @Test
    public void aCountedLabelGivesItsNumber() {
        assertEquals(109, ChapterCounts.fromLabel("109 Chapters"));
        assertEquals(1, ChapterCounts.fromLabel("1 Chapter"));
        assertEquals(1234, ChapterCounts.fromLabel("1,234 Chapters"));
        assertEquals(20500, ChapterCounts.fromLabel("20,500 Chapters"));
    }

    @Test
    public void otherWaysOfLabellingAChapter() {
        assertEquals(12, ChapterCounts.fromLabel("Ch. 12"));
        assertEquals(12, ChapterCounts.fromLabel("ch12"));
        assertEquals(5, ChapterCounts.fromLabel("Episode 5"));
        assertEquals(30, ChapterCounts.fromLabel("Vol 2 Chapter 30"));
        assertEquals(77, ChapterCounts.fromLabel("CHAPTER #77"));
        assertEquals(1234, ChapterCounts.fromLabel("Chapter 1,234"));
    }

    @Test
    public void aDecimalChapterCountsAsTheWholeChapterBeforeIt() {
        assertEquals(12, ChapterCounts.fromLabel("Chapter 12.5"));
    }

    @Test
    public void textWithoutAChapterNumberGivesZero() {
        assertEquals(0, ChapterCounts.fromLabel("Prologue"));
        assertEquals(0, ChapterCounts.fromLabel("Latest chapter"));
        assertEquals(0, ChapterCounts.fromLabel(""));
        assertEquals(0, ChapterCounts.fromLabel(null));
        assertEquals(0, ChapterCounts.fromLabel("Extra"));
    }

    @Test
    public void aFigureTooLongToBeACountGivesZero() {
        assertEquals(0, ChapterCounts.fromLabel("Chapter 99999999999999"));
    }

    @Test
    public void theChapterNumberWinsOverOtherNumbersInTheTitle() {
        assertEquals(88, ChapterCounts.fromLabel("Chapter 88 - Day 2 of the Siege"));
    }
}
