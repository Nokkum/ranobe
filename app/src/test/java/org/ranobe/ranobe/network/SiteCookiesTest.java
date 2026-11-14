package org.ranobe.ranobe.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class SiteCookiesTest {
    @Test
    public void namesAreWhatComesBeforeTheEquals() {
        assertEquals(Arrays.asList("a", "b"), SiteCookies.cookieNames("a=1; b=2"));
    }

    @Test
    public void aValueMayItselfContainEqualsSigns() {
        assertEquals(Arrays.asList("sb-token", "x"), SiteCookies.cookieNames("sb-token=eyJhbGci==; x=y=z"));
    }

    @Test
    public void spacesAroundNamesAreIgnored() {
        assertEquals(Arrays.asList("a", "b"), SiteCookies.cookieNames("  a = 1 ;b=2  "));
    }

    @Test
    public void aNameShowsUpOnceEvenIfSeveralUrlsReturnedIt() {
        assertEquals(Arrays.asList("a"), SiteCookies.cookieNames("a=1; a=2"));
    }

    @Test
    public void noCookiesGiveNoNames() {
        assertTrue(SiteCookies.cookieNames(null).isEmpty());
        assertTrue(SiteCookies.cookieNames("").isEmpty());
        assertTrue(SiteCookies.cookieNames(" ; ;").isEmpty());
    }

    @Test
    public void theNamesToKeepAreLeftOut() {
        assertEquals(Arrays.asList("ywkey", "other"),
                SiteCookies.namesToClear("ywkey=1; cf_clearance=2; other=3; _csrfToken=4",
                        new HashSet<>(Arrays.asList("cf_clearance", "_csrfToken"))));
    }

    @Test
    public void withNothingToKeepEverythingGoes() {
        assertEquals(Arrays.asList("a", "b"), SiteCookies.namesToClear("a=1; b=2", Collections.<String>emptySet()));
        assertEquals(Arrays.asList("a", "b"), SiteCookies.namesToClear("a=1; b=2", null));
    }
}
