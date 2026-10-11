package org.ranobe.ranobe.ui.chapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Chapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChapterSelectionTest {
    private static Chapter chapter(String url) {
        Chapter chapter = new Chapter("novel");
        chapter.url = url;
        return chapter;
    }

    private static List<Chapter> chapters(String... urls) {
        List<Chapter> list = new ArrayList<>();
        for (String url : urls) list.add(chapter(url));
        return list;
    }

    private static Set<String> set(String... urls) {
        return new HashSet<>(Arrays.asList(urls));
    }

    private static List<String> urls(List<Chapter> chapters) {
        List<String> urls = new ArrayList<>();
        for (Chapter chapter : chapters) urls.add(chapter.url);
        return urls;
    }

    @Test
    public void onlyDownloadedChaptersAreListedInTheirOwnOrder() {
        List<Chapter> all = chapters("c5", "c4", "c3", "c2", "c1");

        List<Chapter> shown = ChapterSelection.onlyDownloaded(all, set("c1", "c3", "c4"));

        assertEquals(Arrays.asList("c4", "c3", "c1"), urls(shown));
    }

    @Test
    public void nothingIsListedWhenNothingIsDownloaded() {
        assertTrue(ChapterSelection.onlyDownloaded(chapters("a", "b"), set()).isEmpty());
        assertTrue(ChapterSelection.onlyDownloaded(new ArrayList<Chapter>(), set("a")).isEmpty());
    }

    @Test
    public void togglingAddsThenRemovesWithoutChangingTheOriginal() {
        Set<String> none = set();

        Set<String> one = ChapterSelection.toggled(none, "a");
        Set<String> back = ChapterSelection.toggled(one, "a");

        assertTrue(none.isEmpty());
        assertEquals(set("a"), one);
        assertTrue(back.isEmpty());
    }

    @Test
    public void selectAllChoosesEverythingOnScreen() {
        List<Chapter> visible = chapters("a", "b", "c");

        assertEquals(set("a", "b", "c"), ChapterSelection.toggleAll(visible, set()));
        assertEquals(set("a", "b", "c"), ChapterSelection.toggleAll(visible, set("b")));
    }

    @Test
    public void selectAllAgainClearsOnlyWhatIsOnScreen() {
        List<Chapter> visible = chapters("a", "b");

        // "z" is chosen but not visible (a search narrowed the list), so it stays chosen
        assertEquals(set("z"), ChapterSelection.toggleAll(visible, set("a", "b", "z")));
    }

    @Test
    public void allSelectedNeedsSomethingOnScreenAndAllOfItChosen() {
        assertTrue(ChapterSelection.allSelected(chapters("a", "b"), set("a", "b", "c")));
        assertFalse(ChapterSelection.allSelected(chapters("a", "b"), set("a")));
        assertFalse(ChapterSelection.allSelected(new ArrayList<Chapter>(), set("a")));
    }

    @Test
    public void deletedChaptersDropOutOfTheChoice() {
        Set<String> kept = ChapterSelection.keepDownloaded(set("a", "b", "c"), set("c", "d"));

        assertEquals(set("c"), kept);
    }
}
