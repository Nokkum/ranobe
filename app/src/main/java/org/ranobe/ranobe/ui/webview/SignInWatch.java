package org.ranobe.ranobe.ui.webview;

/**
 * Decides when a site opened in a WebView may close itself after a sign-in: once, and only for a sign-in that happened
 * there.
 * <p>
 * "Signed in" alone is not the signal. A page opened by someone who is signed in already (to look at the account, say)
 * would show it at once and close the moment it opened. So what is there when the page opens is looked at first
 * ({@link #baseline}): if a sign-in exists already there is nothing to wait for, and the page stays until the user
 * closes it. If there is none, the page is armed, and the first sign-in that shows up closes it, which can happen only
 * once.
 */
public final class SignInWatch {
    private boolean baselineKnown;
    private boolean armed;
    private boolean done;

    private SignInWatch(boolean baselineKnown, boolean armed) {
        this.baselineKnown = baselineKnown;
        this.armed = armed;
    }

    /**
     * A page opened to use the site. Whether a sign-in is waited for depends on whether there already is one, which
     * {@link #baseline} has to be told first.
     */
    public static SignInWatch opened() {
        return new SignInWatch(false, false);
    }

    /**
     * A sign-in being completed right in this page (a magic link). It counts whatever was there before.
     */
    public static SignInWatch signInUnderway() {
        return new SignInWatch(true, true);
    }

    /**
     * True until {@link #baseline} has been told what was there when the page opened.
     */
    public boolean needsBaseline() {
        return !baselineKnown;
    }

    /**
     * What the first look found. Already signed in: nothing is waited for and the page never closes by itself. Not
     * signed in: from now on a sign-in closes it. Only the first answer counts.
     */
    public void baseline(boolean signedIn) {
        if (baselineKnown) return;
        baselineKnown = true;
        armed = !signedIn;
    }

    public boolean isArmed() {
        return armed && !done;
    }

    /**
     * Asked with whether there is a sign-in now. True exactly once: the first time that is so after the page was
     * armed.
     */
    public boolean shouldClose(boolean signedInNow) {
        if (!signedInNow || !isArmed()) return false;
        done = true;
        return true;
    }
}
