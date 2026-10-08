package org.ranobe.ranobe.sources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;
import org.ranobe.ranobe.models.Novel;

import java.util.Arrays;

public class MadaraSearchTest {
    private static Element card(String inner) {
        return Jsoup.parse("<div class=\"c-tabs-item__content\">" + inner + "</div>").selectFirst(".c-tabs-item__content");
    }

    private static final String WITH_LINKS = "<div class=\"post-title\"><h3><a href=\"https://example.net/novel/a/\">A</a></h3></div>"
            + "<div class=\"mg_status\"><div class=\"summary-heading\"><h5>Status</h5></div><div class=\"summary-content\">\n OnGoing \n</div></div>"
            + "<div class=\"mg_genres\"><div class=\"summary-heading\"><h5>Genre(s)</h5></div><div class=\"summary-content\">"
            + "<a href=\"https://example.net/genre/action/\" rel=\"tag\">Action</a>, <a href=\"https://example.net/genre/martial-arts/\" rel=\"tag\">Martial Arts</a></div></div>";

    @Test
    public void statusesBecomeTheValuesTheThemeTakes() {
        assertEquals("on-going", MadaraSearch.statusParam("Ongoing"));
        assertEquals("end", MadaraSearch.statusParam("Completed"));
        assertEquals("on-hold", MadaraSearch.statusParam("Hiatus"));
        assertEquals("canceled", MadaraSearch.statusParam("Dropped"));
        assertEquals("", MadaraSearch.statusParam(""));
        assertEquals("", MadaraSearch.statusParam(null));
        assertEquals("", MadaraSearch.statusParam("whatever"));
    }

    @Test
    public void theAddressPartIsEncodedAndEmptyWhenThereIsNoStatus() {
        assertEquals("&status%5B%5D=end", MadaraSearch.statusQuery("Completed"));
        assertEquals("", MadaraSearch.statusQuery(null));
        assertEquals("", MadaraSearch.statusQuery("whatever"));
    }

    @Test
    public void genresAreReadFromLinksOnTheCard() {
        assertEquals(Arrays.asList("Action", "Martial Arts"), MadaraSearch.genresFrom(card(WITH_LINKS)));
    }

    @Test
    public void genresAreReadFromPlainTextWhenThereAreNoLinks() {
        Element plain = card("<div class=\"mg_genres\"><div class=\"summary-content\">Action, Fantasy ,  Romance</div></div>");

        assertEquals(Arrays.asList("Action", "Fantasy", "Romance"), MadaraSearch.genresFrom(plain));
    }

    @Test
    public void aCardWithoutGenresOrStatusGivesNothing() {
        Element bare = card("<div class=\"post-title\"><h3><a href=\"/x\">X</a></h3></div>");

        assertTrue(MadaraSearch.genresFrom(bare).isEmpty());
        assertEquals("", MadaraSearch.statusFrom(bare));
    }

    @Test
    public void theCardStatusIsTrimmed() {
        assertEquals("OnGoing", MadaraSearch.statusFrom(card(WITH_LINKS)));
    }

    @Test
    public void genresAndStatusAreCopiedOntoTheNovel() {
        Novel novel = new Novel("https://example.net/novel/a/");

        MadaraSearch.fillFromCard(novel, card(WITH_LINKS));

        assertEquals(Arrays.asList("Action", "Martial Arts"), novel.genres);
        assertEquals("OnGoing", novel.status);
    }

    @Test
    public void aMissingStatusLeavesTheNovelsStatusAlone() {
        Novel novel = new Novel("https://example.net/novel/a/");
        novel.status = "Completed";

        MadaraSearch.fillFromCard(novel, card("<div class=\"mg_genres\"><div class=\"summary-content\">Action</div></div>"));

        assertEquals("Completed", novel.status);
    }
}
