package org.ranobe.ranobe.ui.webview;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SignInWatchTest {
    // the case that closed the WebView the moment it opened: the user was signed in already
    @Test
    public void aPageOpenedWhileSignedInNeverClosesItself() {
        SignInWatch watch = SignInWatch.opened();
        assertTrue(watch.needsBaseline());
        watch.baseline(true);

        assertFalse(watch.needsBaseline());
        assertFalse(watch.isArmed());
        assertFalse(watch.shouldClose(true));
        assertFalse(watch.shouldClose(true));
    }

    @Test
    public void aPageOpenedSignedOutClosesWhenTheSignInSucceeds() {
        SignInWatch watch = SignInWatch.opened();
        watch.baseline(false);

        assertTrue(watch.isArmed());
        // still on the login page
        assertFalse(watch.shouldClose(false));
        assertTrue(watch.shouldClose(true));
    }

    @Test
    public void itClosesOnlyOnce() {
        SignInWatch watch = SignInWatch.opened();
        watch.baseline(false);

        assertTrue(watch.shouldClose(true));
        assertFalse(watch.shouldClose(true));
        assertFalse(watch.isArmed());
    }

    @Test
    public void nothingCanCloseItBeforeTheFirstLook() {
        SignInWatch watch = SignInWatch.opened();

        assertFalse(watch.isArmed());
        assertFalse(watch.shouldClose(true));
    }

    @Test
    public void onlyTheFirstLookCounts() {
        SignInWatch signedIn = SignInWatch.opened();
        signedIn.baseline(true);
        signedIn.baseline(false);
        assertFalse(signedIn.shouldClose(true));

        SignInWatch signedOut = SignInWatch.opened();
        signedOut.baseline(false);
        signedOut.baseline(true);
        assertTrue(signedOut.shouldClose(true));
    }

    // a magic link is a sign-in being completed in the page, so it counts even if a session was there before
    @Test
    public void aSignInUnderwayClosesWhateverWasThereBefore() {
        SignInWatch watch = SignInWatch.signInUnderway();

        assertFalse(watch.needsBaseline());
        assertTrue(watch.isArmed());
        assertTrue(watch.shouldClose(true));
        assertFalse(watch.shouldClose(true));
    }
}
