package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuthorNamesTest {
    @Test
    public void aPathEncodesSpacesAsPercent20() {
        assertEquals("Tang%20Jia%20San%20Shao", AuthorNames.encodePath("Tang Jia San Shao"));
    }

    @Test
    public void aPlusInANameIsKeptAsAPlusNotASpace() {
        assertEquals("a%2Bb", AuthorNames.encodePath("a+b"));
    }

    @Test
    public void nonLatinNamesArePercentEncodedAsUtf8() {
        assertEquals("%E5%94%90%E5%AE%B6%E4%B8%89%E5%B0%91", AuthorNames.encodePath("唐家三少"));
    }

    @Test
    public void aQueryStillUsesPlusForSpaces() {
        assertEquals("Tang+Jia", AuthorNames.encode("Tang Jia"));
    }

    @Test
    public void aPageThatNamesTheAuthorCounts() {
        assertTrue(AuthorNames.appearsIn("<h3 class=\"title\">Novels by Tang  Jia San Shao</h3>", "Tang Jia San Shao"));
        // case, spacing and accents do not matter, as in matches()
        assertTrue(AuthorNames.appearsIn("by TANG JIA-SAN SHAO", "tang jia san shao"));
    }

    @Test
    public void aGenericListingDoesNotCount() {
        assertFalse(AuthorNames.appearsIn("<h1>Latest releases</h1><a>Some Novel</a>", "Tang Jia San Shao"));
    }

    @Test
    public void aBlankAuthorOrPageNeverCounts() {
        assertFalse(AuthorNames.appearsIn("anything", ""));
        assertFalse(AuthorNames.appearsIn("anything", "  "));
        assertFalse(AuthorNames.appearsIn(null, "Someone"));
    }
}
