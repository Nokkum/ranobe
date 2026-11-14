package org.ranobe.ranobe.network;

import android.webkit.CookieManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The Webnovel account as far as this device knows: signed in through a WebView, whose cookies the app's own requests
 * share (see {@link WebViewCookieJar}), which is how a chapter unlocked there reads in the reader.
 */
public final class WebnovelSession {
    // Webnovel's own sign-in page
    public static final String LOGIN_URL = "https://passport.webnovel.com/login.html?appid=900&areaid=2&auto=1&source=enweb&format=redirect&returnurl=https%3A%2F%2Fm.webnovel.com%2Flogin%2Fcallback%3FredirectUrl%3Dhttps%253A%252F%252Fm.webnovel.com%252F";
    public static final String HOME_URL = "https://www.webnovel.com/";
    private static final String DOMAIN = "webnovel.com";
    // where the site's cookies can be seen from: the site, its sign-in and its mobile version
    private static final String[] COOKIE_URLS = {
            HOME_URL, "https://passport.webnovel.com/", "https://m.webnovel.com/"
    };
    // page storage (local storage and the like) is kept per origin
    private static final String[] STORAGE_ORIGINS = {
            "https://www.webnovel.com", "https://passport.webnovel.com", "https://m.webnovel.com"
    };
    // The cookies that say who is signed in. The site's cookie policy lists these as its identity cookies; they are
    // here, in one place, because they could not be checked against a live sign-in.
    private static final Set<String> IDENTITY_COOKIES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("ywkey", "ywguid", "inkstone_auth_token")));
    // What signing out leaves: a Cloudflare clearance (losing it means another check), the anonymous device id and
    // the reader's own language and gender choices. None of them is the account.
    private static final Set<String> KEEP_ON_SIGN_OUT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "cf_clearance", "_csrfToken", "webnovel_uuid", "webnovel-language", "webnovel-content-language",
            "bookCitysex", "wn-bookcity-gender")));

    private WebnovelSession() {
    }

    // For a page that closes itself after a sign-in: what to look for, and where.
    public static String[] identityCookieNames() {
        return IDENTITY_COOKIES.toArray(new String[0]);
    }

    public static String[] cookieUrls() {
        return COOKIE_URLS.clone();
    }

    // Whether the WebView holds a Webnovel sign-in.
    public static boolean isSignedIn() {
        CookieManager cookies = CookieManager.getInstance();
        for (String url : COOKIE_URLS) {
            if (isSignedIn(cookies.getCookie(url))) return true;
        }
        return false;
    }

    // Whether a Cookie header carries one of the identity cookies.
    static boolean isSignedIn(String header) {
        for (String name : SiteCookies.cookieNames(header)) {
            if (IDENTITY_COOKIES.contains(name)) return true;
        }
        return false;
    }

    // Forgets the Webnovel sign-in on this device, and only that: other sites' sign-ins and Cloudflare clearances stay.
    public static void clearLocal() {
        SiteCookies.clear(DOMAIN, COOKIE_URLS, STORAGE_ORIGINS, KEEP_ON_SIGN_OUT);
    }
}
