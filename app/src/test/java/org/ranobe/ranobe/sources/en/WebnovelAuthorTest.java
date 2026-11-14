package org.ranobe.ranobe.sources.en;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WebnovelAuthorTest {
    @Test
    public void theLinkShowingTheAuthorsNameIsTheirProfile() {
        String page = "<a href=\"/profile/111\">Some Reader</a>"
                + "<a class=\"c_primary\" href=\"/profile/4300000012?x=1\">Cuttlefish <b>That</b> Loves Diving</a>";
        assertEquals("4300000012", Webnovel.profileId(page, "Cuttlefish That Loves Diving"));
    }

    @Test
    public void aFullAddressAndOddSpacingStillMatch() {
        String page = "<A HREF='https://www.webnovel.com/profile/777'>\n  tang   jia san shao \n</A>";
        assertEquals("777", Webnovel.profileId(page, "Tang Jia San Shao"));
    }

    @Test
    public void entitiesInTheShownNameAreUnderstood() {
        assertEquals("9", Webnovel.profileId("<a href=\"/profile/9\">Tom &amp; Jerry</a>", "Tom & Jerry"));
    }

    // a page is full of readers' profile links; the first one is not the author
    @Test
    public void linksToOtherPeopleAreNotTaken() {
        String page = "<a href=\"/profile/111\">Some Reader</a><a href=\"/profile/222\">Another Reader</a>";
        assertNull(Webnovel.profileId(page, "The Real Author"));
    }

    @Test
    public void withoutAMatchingLinkTheIdInThePageDataIsUsed() {
        assertEquals("555", Webnovel.profileId("<script>g_data = {\"authorId\":\"555\"}</script>", "Anyone"));
        assertEquals("556", Webnovel.profileId("{\"authorId\": 556}", "Anyone"));
    }

    @Test
    public void aNameWithNothingToGoOnHasNoProfile() {
        assertNull(Webnovel.profileId("<p>nothing here</p>", "Anyone"));
        assertNull(Webnovel.profileId("<a href=\"/profile/1\">x</a>", ""));
        assertNull(Webnovel.profileId(null, "Anyone"));
    }

    @Test
    public void aRememberedAuthorIsFoundAgainByTheNormalisedName() {
        Webnovel.rememberAuthor("Memory Test Author", "42");
        assertEquals("42", Webnovel.knownAuthorId("memory  TEST author"));
        assertNull(Webnovel.knownAuthorId("A Different Memory Author"));
    }

    @Test
    public void rememberingNothingChangesNothing() {
        Webnovel.rememberAuthor("", "1");
        Webnovel.rememberAuthor("Null Id Author", null);
        assertNull(Webnovel.knownAuthorId(""));
        assertNull(Webnovel.knownAuthorId("Null Id Author"));
    }
}
