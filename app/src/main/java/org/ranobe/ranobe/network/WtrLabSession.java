package org.ranobe.ranobe.network;

import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import java.io.IOException;
import java.util.Locale;

// Reads WTR-LAB cookies from Android's WebView cookie store only for its HTTPS API origin.
public final class WtrLabSession {
    public static final String ORIGIN = "https://wtr-lab.com";
    private static final String HOST = "wtr-lab.com";

    private WtrLabSession() {
    }

    // Forgets the WTR-LAB session on this device.
    public static void clearLocal() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        cookies.flush();
        WebStorage.getInstance().deleteAllData();
    }

    // True for HTTPS URLs on wtr-lab.com or one of its subdomains.
    public static boolean isSiteUrl(String rawUrl) {
        Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || uri.getUserInfo() != null
                || (port != -1 && port != 443)) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return HOST.equals(lower) || lower.endsWith("." + HOST);
    }

    // Cookies for a server-provided WTR-LAB content URL.
    public static String cookieHeaderForSite(String rawUrl) throws IOException {
        if (!isSiteUrl(rawUrl)) {
            throw new IOException("Refusing to send WTR-LAB session outside its HTTPS site.");
        }
        return CookieManager.getInstance().getCookie(rawUrl);
    }

    public static String cookieHeaderForApi(String rawUrl) throws IOException {
        Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getEncodedPath();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || !HOST.equals(host.toLowerCase(Locale.ROOT))
                || uri.getUserInfo() != null
                || (port != -1 && port != 443)
                || path == null
                || !path.startsWith("/api/")) {
            throw new IOException("Refusing to send WTR-LAB session outside its HTTPS API origin.");
        }

        // Asking CookieManager for the exact API URL preserves each cookie's WebView domain,
        // path, Secure, and HttpOnly rules. The value is used for this request only.
        return CookieManager.getInstance().getCookie(rawUrl);
    }
}