package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.sources.ChapterUnlockSupport;

public class WebnovelLockTest {
    @Test
    public void aReadableChapterIsNotLocked() {
        String box = "<div class=\"cha-content\"><div class=\"cha-words\"><p>Once upon a time</p></div></div>";
        assertFalse(Webnovel.lockedMarkup(box, "", true));
    }

    @Test
    public void aLockModifierOnTheBoxLocksIt() {
        String box = "<div class=\"cha-content _lock\"><div class=\"cha-words\"><p>Only a preview</p></div></div>";
        assertTrue(Webnovel.lockedMarkup(box, "", true));
        assertTrue(Webnovel.lockedMarkup("<div class='j_lock'><div class='cha-words'>x</div></div>", "", true));
    }

    @Test
    public void theLockIconLocksIt() {
        String box = "<div class=\"cha-content\"><svg><use xlink:href=\"#i-lock\"></use></svg><div class=\"cha-words\"></div></div>";
        assertTrue(Webnovel.lockedMarkup(box, "", true));
    }

    // free chapters carry "unlock more" banners, with text on the page those must not lock it
    @Test
    public void anUnlockBannerAloneDoesNotLockAChapterThatHasText() {
        String box = "<div class=\"cha-content\"><div class=\"cha-words\"><p>Text</p></div><a class=\"j_unlock_tip unlock-btn\">Unlock more</a></div>";
        assertFalse(Webnovel.lockedMarkup(box, "Unlock more", true));
    }

    @Test
    public void withNoTextTheLooserSignsCount() {
        assertTrue(Webnovel.lockedMarkup("<div class=\"cha-page\"><a class=\"unlock-btn\">Go</a></div>", "Go", false));
        assertTrue(Webnovel.lockedMarkup("<div class=\"x\"><p>Unlock this chapter</p></div>", "Unlock this chapter", false));
        assertTrue(Webnovel.lockedMarkup("<div class=\"x\"><p>Use a Fast Pass</p></div>", "Use a Fast Pass", false));
    }

    @Test
    public void aPageWithNothingLockLikeIsJustUnreadable() {
        assertFalse(Webnovel.lockedMarkup("<div class=\"cha-page\"></div>", "", false));
        // "block" and "clock" contain the letters but are not locks
        assertFalse(Webnovel.lockedMarkup("<div class=\"block-quote clock\"></div>", "", false));
    }

    @Test
    public void unlockPagesAreTheSitesOwn() {
        Webnovel source = new Webnovel();
        assertTrue(source instanceof ChapterUnlockSupport);
        assertEquals("https://passport.webnovel.com/login.html", source.signInUrl());
        Chapter chapter = new Chapter("https://www.webnovel.com/book/x_1");
        chapter.url = "https://www.webnovel.com/book/x_1/y_2";
        assertEquals(chapter.url, source.unlockUrl(chapter));
    }
}
