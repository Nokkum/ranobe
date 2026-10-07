package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.sources.SourceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

// Parses saved copies of the real pages and REST replies; nothing here touches the network.
public class DivineDaoLibraryTest {
    private static final String STORY_URL =
            "https://www.divinedaolibrary.com/story/dungeon-exploration-starting-from-level-1/";

    private static String fixture(String name) throws IOException {
        try (InputStream in = DivineDaoLibraryTest.class.getResourceAsStream("/divinedaolibrary/" + name)) {
            if (in == null) throw new IOException("missing fixture " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read; (read = in.read(buffer)) != -1; ) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void sourceIsRegisteredWithItsOwnId() {
        assertEquals(22, DivineDaoLibrary.SOURCE_ID);
        assertEquals(DivineDaoLibrary.class, SourceManager.getSources().get(22));

        assertEquals(22, new DivineDaoLibrary().metadata().sourceId);
        assertEquals("Divine Dao Library", new DivineDaoLibrary().metadata().name);
        assertTrue(new DivineDaoLibrary().requestGapMillis() > 0);
    }

    // list

    @Test
    public void storyListGivesTitlesAddressesAndCovers() throws Exception {
        List<Novel> novels = DivineDaoLibrary.parseStories(fixture("stories.json"));

        assertEquals(3, novels.size()); // the entry without a link is skipped
        assertEquals("Worthless Profession: Dragon Tamer", novels.get(0).name);
        assertEquals("https://www.divinedaolibrary.com/story/worthless-profession-dragon-tamer/", novels.get(0).url);
        assertEquals(22, novels.get(0).sourceId);
        // the small cover when there is one, the full one when there is not, none without media
        assertEquals("https://www.divinedaolibrary.com/wp-content/uploads/2025/02/Cover-200x300.jpg", novels.get(0).cover);
        assertEquals("https://www.divinedaolibrary.com/wp-content/uploads/2025/02/Cover.jpg", novels.get(1).cover);
        assertEquals("", novels.get(2).cover);
    }

    @Test
    public void titlesAreDecodedFromHtml() throws Exception {
        List<Novel> novels = DivineDaoLibrary.parseStories(fixture("stories.json"));

        assertEquals("Among the Dungeon Streamers, for Some Reason I\u2019m the Only One & Only", novels.get(1).name);
    }

    @Test
    public void pageBeyondTheLastOneAndBrokenReplyGiveNoNovels() {
        // WordPress answers an error object once the page number is too high
        String outOfRange = "{\"code\":\"rest_post_invalid_page_number\",\"message\":\"The page number requested is larger than the number of pages available.\",\"data\":{\"status\":400}}";

        assertTrue(DivineDaoLibrary.parseStories(outOfRange).isEmpty());
        assertTrue(DivineDaoLibrary.parseStories("<html>Not found</html>").isEmpty());
        assertTrue(DivineDaoLibrary.parseStories("[]").isEmpty());
        assertTrue(DivineDaoLibrary.parseStories(null).isEmpty());
    }

    // details

    @Test
    public void detailsReadTheRealAuthorAndNotTheUploader() throws Exception {
        Novel novel = DivineDaoLibrary.parseDetails(fixture("story.html"), new Novel(STORY_URL));

        assertEquals("Dungeon Exploration; Starting from Level 1", novel.name);
        assertEquals(Arrays.asList("わんた (Wanta)"), novel.authors); // not "Silavin", who posted the translation
        assertEquals(Arrays.asList("I Want To Explore Dungeons Together With a JK (High-School Girl)!"), novel.alternateNames);
        assertEquals("Ongoing", novel.status);
        assertEquals(2026, novel.year);
        assertEquals(22, novel.sourceId);
        assertEquals("https://www.divinedaolibrary.com/wp-content/uploads/2026/03/Explore-Dungeon-With-JK-Cover-DDL.jpg", novel.cover);
    }

    @Test
    public void detailsSummaryStartsAfterTheDescriptionHeading() throws Exception {
        Novel novel = DivineDaoLibrary.parseDetails(fixture("story.html"), new Novel(STORY_URL));

        assertEquals("Kanamiya Masato was never meant to be a hero. Burdened by debt, he stepped onto the only path left open to him: becoming an explorer.\n\n"
                + "Tachibana Rika had even less to lose. She chose the same dangerous path.", novel.summary);
    }

    @Test
    public void detailsUseGenresAndFallBackToTagsWhenThereAreNone() throws Exception {
        String html = fixture("story.html");

        assertEquals(Arrays.asList("Action", "Adventure", "Dungeon"),
                DivineDaoLibrary.parseDetails(html, new Novel(STORY_URL)).genres);

        String withoutGenres = html.replace("story__taxonomies tag-group", "story__nothing");
        assertEquals(Arrays.asList("Calm Protagonist", "Dungeon Crawling"),
                DivineDaoLibrary.parseDetails(withoutGenres, new Novel(STORY_URL)).genres);
    }

    @Test
    public void detailsCopeWithAPageThatHasNoSummarySection() {
        Novel novel = DivineDaoLibrary.parseDetails(
                "<html><body><h1 class=\"story__identity-title\">Only A Title</h1></body></html>", new Novel(STORY_URL));

        assertEquals("Only A Title", novel.name);
        assertTrue(novel.authors.isEmpty());
        assertTrue(novel.alternateNames.isEmpty());
        assertEquals("", novel.summary);
    }

    // chapters

    @Test
    public void chapterListIsOldestFirstAndSkipsTheShowMoreRow() throws Exception {
        Novel novel = new Novel(STORY_URL);

        List<Chapter> chapters = DivineDaoLibrary.parseChapters(fixture("story.html"), novel);

        assertEquals(4, chapters.size());
        assertEquals("Chapter 1, I\u2019m Going To Become an Explorer and Earn a Living!", chapters.get(0).name);
        assertEquals("Chapter 4, Tokyo Dungeon, 2nd Floor", chapters.get(3).name);
        assertEquals(1f, chapters.get(0).id, 0f);
        assertEquals(4f, chapters.get(3).id, 0f);
        assertEquals("March 24, 2026", chapters.get(0).updated);
        assertEquals("April 14, 2026", chapters.get(3).updated);
        assertEquals(STORY_URL, chapters.get(2).novelUrl);
        assertEquals("https://www.divinedaolibrary.com/story/dungeon-exploration-starting-from-level-1/"
                        + "dungeon-exploration-starting-from-level-1-chapter-3-report-of-the-first-exploration/",
                chapters.get(2).url);
    }

    @Test
    public void pageWithNoChapterListGivesNoChapters() {
        assertTrue(DivineDaoLibrary.parseChapters("<html><body>Nothing here</body></html>", new Novel(STORY_URL)).isEmpty());
    }

    // chapter text

    @Test
    public void chapterTextDropsSpacersAndKeepsLineBreaksTablesListsAndImages() throws Exception {
        String text = DivineDaoLibrary.parseChapterHtml(fixture("chapter.html"));

        assertEquals("Translator: Lizz\n\n"
                + "There was a parking lot for explorers near the Tokyo Dungeon. He opened a can of coffee before starting the engine.\n\n"
                + "*Pshh.*\n\n"
                + "Status\nLevel 3\nRank E\n\n"
                + "\u25c6\u25c6\u25c6\n\n"
                + "* * *\n\n"
                + "Name | Masato\nLevel | 3\n\n"
                + "- Iron pipe\n- Healing potion\n\n"
                + "Note one\n\nNote two\n\n"
                + "[img]https://www.divinedaolibrary.com/wp-content/uploads/2026/04/map.png[/img]\n\n"
                + "Last paragraph.", text);
    }

    @Test
    public void pageWithoutChapterBodyGivesNoText() {
        assertEquals("", DivineDaoLibrary.parseChapterHtml("<html><body><p>Menu</p></body></html>"));
    }

    @Test
    public void restCopyOfAChapterGivesTheSameKindOfText() throws Exception {
        String text = DivineDaoLibrary.parseChapterRest(fixture("chapter-rest.json"));

        assertEquals("Translator: Lizz\n\n"
                + "Twenty\u2011five years ago, dungeons appeared all over the world.\n\n"
                + "\u201cI finally got my explorer\u2019s license!\u201d", text);
    }

    @Test
    public void emptyOrBrokenRestReplyGivesNoText() {
        assertEquals("", DivineDaoLibrary.parseChapterRest("[]"));
        assertEquals("", DivineDaoLibrary.parseChapterRest("{\"code\":\"rest_no_route\"}"));
        assertEquals("", DivineDaoLibrary.parseChapterRest("<html></html>"));
        assertEquals("", DivineDaoLibrary.parseChapterRest(null));
    }

    @Test
    public void restAddressUsesTheLastPartOfThePageAddress() {
        String expected = "https://www.divinedaolibrary.com/wp-json/wp/v2/fcn_chapter?slug=my-chapter-1&_fields=id,title,content";

        assertEquals(expected, DivineDaoLibrary.restChapterUrl("https://www.divinedaolibrary.com/story/my-story/my-chapter-1/"));
        assertEquals(expected, DivineDaoLibrary.restChapterUrl("https://www.divinedaolibrary.com/story/my-story/my-chapter-1"));
        assertEquals(expected, DivineDaoLibrary.restChapterUrl("https://www.divinedaolibrary.com/story/my-story/my-chapter-1/?x=1#top"));
    }

    @Test
    public void nonBreakingSpacesAreTrimmedLikeOrdinarySpaces() {
        assertEquals("text", DivineDaoLibrary.clean("\u00a0 text \u00a0"));
        assertEquals("", DivineDaoLibrary.clean("\u00a0"));
        assertEquals("", DivineDaoLibrary.clean(null));
    }
}
