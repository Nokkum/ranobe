package org.ranobe.ranobe.network;

import android.webkit.CookieManager;
import android.webkit.WebStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Forgets one site's sign-in on this device, and only that site's. The WebView's cookie store is shared by
 * everything the app signs in to or passes a check for (WTR-LAB, Webnovel, Cloudflare clearances...), so wiping all of
 * it to sign out of one account would sign the user out of the others too.
 */
public final class SiteCookies {
    private SiteCookies() {
    }

    /**
     * Expires the cookies of {@code domain} and deletes its page storage.
     *
     * @param domain         the registrable domain the cookies were set for, "example.com"
     * @param cookieUrls     where the site's cookies can be seen from (the host, the host's api, with and without www)
     * @param storageOrigins the origins whose local storage and the like to delete
     * @param keep           cookie names that stay: they say nothing about the account (a Cloudflare clearance, a
     *                       language choice), and losing them would only cost the user a new check
     */
    public static void clear(String domain, String[] cookieUrls, String[] storageOrigins, Set<String> keep) {
        CookieManager cookies = CookieManager.getInstance();
        for (String url : cookieUrls) {
            for (String name : namesToClear(cookies.getCookie(url), keep)) {
                expire(cookies, url, name, domain);
            }
        }
        cookies.flush();
        WebStorage storage = WebStorage.getInstance();
        for (String origin : storageOrigins) {
            storage.deleteOrigin(origin);
        }
    }

    // The names in a Cookie header ("a=1; b=2" gives a and b); null or empty gives none.
    static List<String> cookieNames(String header) {
        List<String> names = new ArrayList<>();
        if (header == null) return names;
        for (String pair : header.split(";")) {
            String name = pair.trim();
            int equals = name.indexOf('=');
            if (equals >= 0) name = name.substring(0, equals).trim();
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }
        return names;
    }

    // What of a Cookie header is to go: every name except the ones to keep.
    static List<String> namesToClear(String header, Set<String> keep) {
        List<String> names = cookieNames(header);
        names.removeAll(keep == null ? Collections.<String>emptySet() : keep);
        return names;
    }

    // A cookie can only be replaced by one with the same name, domain and path, and the header doesn't say which
    // domain or path it was set with, so the likely combinations are all expired: tied to the host or to
    // the whole domain, on the root path or the api's.
    private static void expire(CookieManager cookies, String url, String name, String domain) {
        String gone = name + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure";
        for (String path : new String[]{"/", "/api"}) {
            cookies.setCookie(url, gone + "; Path=" + path);
            cookies.setCookie(url, gone + "; Path=" + path + "; Domain=" + domain);
        }
    }
}
