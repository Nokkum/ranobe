package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;

public class AllNovelStatusTest {
    private static Element row(String titleLabels) {
        return Jsoup.parse("<div class=\"col-truyen-main archive\"><div class=\"row\"><div class=\"col-xs-7\">"
                + "<h3 class=\"truyen-title\"><a href=\"/some-novel.html\" title=\"Some Novel\">Some Novel</a>" + titleLabels + "</h3>"
                + "<span class=\"author\">Someone</span></div></div></div>").selectFirst("div.row");
    }

    @Test
    public void aFullLabelMarksACompletedNovel() {
        assertTrue(AllNovel.isFull(row("<span class=\"label-title label-full\"></span>")));
        assertTrue(AllNovel.isFull(row("<span class=\"label-title label-hot\"></span><span class=\"label-title label-full\"></span>")));
    }

    @Test
    public void otherLabelsOrNoneDoNotMeanCompleted() {
        assertFalse(AllNovel.isFull(row("")));
        assertFalse(AllNovel.isFull(row("<span class=\"label-title label-hot\"></span>")));
        assertFalse(AllNovel.isFull(row("<span class=\"label-title label-new\"></span>")));
    }

    @Test
    public void onlyCompletedCanBeFilteredAndOnlyFromTheResults() {
        AllNovel source = new AllNovel();

        assertTrue(source.supportsStatus("Completed"));
        assertFalse(source.supportsStatus("Ongoing"));
        assertFalse(source.supportsStatus("Hiatus"));
        assertFalse(source.supportsStatus("Dropped"));
        assertFalse(source.filtersStatusItself());
        assertTrue(source.reportsStatusInResults());
        assertFalse(source.supportsGenreFilter());
        assertFalse(source.reportsGenresInResults());
        assertEquals(false, source.supportsStatus(""));
    }
}
