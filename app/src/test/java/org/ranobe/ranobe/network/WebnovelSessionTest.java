package org.ranobe.ranobe.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebnovelSessionTest {
    @Test
    public void anIdentityCookieMeansSignedIn() {
        assertTrue(WebnovelSession.isSignedIn("_csrfToken=x; ywkey=abc; webnovel_uuid=u"));
        assertTrue(WebnovelSession.isSignedIn("ywguid=123"));
        assertTrue(WebnovelSession.isSignedIn("inkstone_auth_token=t"));
    }

    // a visitor who never signed in still has these
    @Test
    public void theCookiesEveryVisitorHasDoNotCount() {
        assertFalse(WebnovelSession.isSignedIn("_csrfToken=x; webnovel_uuid=u; cf_clearance=c; bookCitysex=1"));
    }

    @Test
    public void noCookiesMeansNotSignedIn() {
        assertFalse(WebnovelSession.isSignedIn(null));
        assertFalse(WebnovelSession.isSignedIn(""));
    }

    // only the name counts, never a value that happens to look like one
    @Test
    public void aValueIsNotAName() {
        assertFalse(WebnovelSession.isSignedIn("note=ywkey; other=ywguid"));
    }

    @Test
    public void theSignInPageIsWebnovelsOwn() {
        assertEquals("https://passport.webnovel.com/login.html", WebnovelSession.LOGIN_URL);
    }
}
