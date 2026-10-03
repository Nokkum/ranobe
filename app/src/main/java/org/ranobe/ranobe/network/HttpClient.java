package org.ranobe.ranobe.network;

import android.content.Context;

import androidx.annotation.NonNull;

import org.ranobe.ranobe.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpClient {
    // okhttp's default "okhttp/x.y" agent gets challenged or throttled by most novel sites
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    private static volatile OkHttpClient client = null;
    private static volatile OkHttpClient wtrLabApiClient = null;

    private static OkHttpClient client() {
        if (client == null) {
            synchronized (HttpClient.class) {
                if (client != null) return client;
                Context context = App.getContext();
                String tmp = System.getProperty("java.io.tmpdir");
                File baseDir = context != null ? context.getCacheDir() : new File(tmp != null ? tmp : ".");
                File cacheDir = new File(baseDir, "cache-files");
                Cache cache = new Cache(cacheDir, 50 * 1024 * 1024); //50 MiB, chapter pages are large
                client = new OkHttpClient
                        .Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        // keep sockets alive across screens so repeat requests to a source skip TLS setup
                        .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                        .addInterceptor(new DefaultHeadersInterceptor())
                        .addInterceptor(new OfflineCacheInterceptor())
                        .addNetworkInterceptor(new CacheInterceptor())
                        .cache(cache)
                        .build();
            }
        }
        return client;
    }

    private static OkHttpClient wtrLabApiClient() {
        if (wtrLabApiClient == null) {
            synchronized (HttpClient.class) {
                if (wtrLabApiClient == null) {
                    // Session cookies are attached by the WTR-LAB API methods below. Do not
                    // follow redirects: an API redirect must never carry those cookies elsewhere.
                    wtrLabApiClient = client().newBuilder()
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .build();
                }
            }
        }
        return wtrLabApiClient;
    }

    private static Request.Builder wtrLabApiRequest(String url) throws IOException {
        String cookieHeader = WtrLabSession.cookieHeaderForApi(url);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-store");
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            builder.header("Cookie", cookieHeader);
        }
        return builder;
    }

    public static String GET_WTR_LAB_API(String url, HashMap<String, String> headers) throws IOException {
        Request.Builder builder = wtrLabApiRequest(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        try (Response response = wtrLabApiClient().newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " from WTR-LAB API");
            }
            return body == null ? "" : body.string();
        }
    }

    /**
     * Fetches a content URL that the WTR-LAB reader API handed back. The URL is chosen by the server, so
     * the session is attached only to HTTPS URLs on wtr-lab.com or its subdomains and never followed
     * through redirects; any other host is fetched without it.
     */
    public static String GET_WTR_LAB_CONTENT(String url, HashMap<String, String> headers) throws IOException {
        if (!WtrLabSession.isSiteUrl(url)) return GET(url, headers);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-store");
        String cookieHeader = WtrLabSession.cookieHeaderForSite(url);
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            builder.header("Cookie", cookieHeader);
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }
        try (Response response = wtrLabApiClient().newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " from WTR-LAB API");
            }
            return body == null ? "" : body.string();
        }
    }

    public static String POST_JSON_WTR_LAB_API(String url, String json) throws IOException {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(mediaType, json);
        Request request = wtrLabApiRequest(url)
                .header("Accept", "application/json")
                .header("Origin", WtrLabSession.ORIGIN)
                .post(requestBody)
                .build();

        try (Response response = wtrLabApiClient().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " from WTR-LAB API");
            }
            return body == null ? "" : body.string();
        }
    }

    public static String GET(String url, HashMap<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        try (Response response = HttpClient.client().newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        }
    }

    public static void DOWNLOAD(String url, HashMap<String, String> headers, File dest) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        try (Response response = HttpClient.client().newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    public static String POST(String url, HashMap<String, String> headers, HashMap<String, String> form) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        FormBody.Builder formBody = new FormBody.Builder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            formBody.add(entry.getKey(), entry.getValue());
        }
        try (Response response = HttpClient.client().newCall(builder.post(formBody.build()).build()).execute()) {
            ResponseBody body = response.body();
            return body == null ? "" : body.string();
        }
    }

    public static String POST_JSON(String url, String json) throws IOException {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(mediaType, json);
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(requestBody)
                .build();

        try (Response response = HttpClient.client().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return body == null ? "" : body.string();
        }
    }

    public static class DefaultHeadersInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (request.header("User-Agent") != null) return chain.proceed(request);
            return chain.proceed(request.newBuilder().header("User-Agent", USER_AGENT).build());
        }
    }

    // when the network is down or flaky, fall back to whatever copy is in the disk cache
    public static class OfflineCacheInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            try {
                return chain.proceed(request);
            } catch (IOException e) {
                if (!"GET".equals(request.method()) || request.header("Cookie") != null) throw e;
                CacheControl staleOk = new CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(7, TimeUnit.DAYS)
                        .build();
                Response cached = chain.proceed(request.newBuilder().cacheControl(staleOk).build());
                if (cached.isSuccessful()) return cached;
                cached.close();
                throw e;
            }
        }
    }

    public static class CacheInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());

            // never pin error / challenge pages in the cache, they'd be served for the next 15 minutes
            if (!response.isSuccessful()) return response;
            // Authenticated WTR-LAB API results are private and must not be cached on disk.
            if (chain.request().header("Cookie") != null) {
                return response.newBuilder().header("Cache-Control", "no-store").build();
            }

            CacheControl cacheControl = new CacheControl.Builder()
                    .maxAge(15, TimeUnit.MINUTES) // 15 minutes cache
                    .build();

            return response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    .header("Cache-Control", cacheControl.toString())
                    .build();
        }
    }
}
