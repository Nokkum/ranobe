package org.ranobe.ranobe.sources;

import androidx.annotation.StringRes;

import org.ranobe.ranobe.models.Chapter;

/**
 * Implemented by a source whose locked chapters the user can unlock on the site itself, in a
 * WebView, usually after signing in to an account there. When such a chapter comes back locked
 * (see {@link ChapterLockedException}) the reader offers these steps and tries the chapter again
 * once the user is back.
 */
public interface ChapterUnlockSupport {
    /**
     * The page where the user signs in to the site.
     */
    String signInUrl();

    /**
     * The page where this chapter can be unlocked.
     */
    String unlockUrl(Chapter chapter);

    /**
     * The browser agent the site must be shown to: the one this source's own requests use, because
     * what the site grants a browser (a Cloudflare clearance, for one) is tied to it. Null for the
     * app's default.
     */
    String userAgent();

    /**
     * The names of the cookies that show a sign-in, so the sign-in page can close itself once the user has signed in
     * (and only then). Null when there is nothing reliable to look for; the user then closes the page.
     */
    default String[] signInCookies() {
        return null;
    }

    /**
     * Where those cookies are read; null for the sign-in page's own address.
     */
    default String[] signInCookieUrls() {
        return null;
    }

    /**
     * What the user has to do, shown under "Chapter Locked".
     */
    @StringRes
    int unlockSteps();
}
