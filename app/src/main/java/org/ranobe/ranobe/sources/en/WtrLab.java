package org.ranobe.ranobe.sources.en;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.DataSource;
import org.ranobe.ranobe.models.Filter;
import org.ranobe.ranobe.models.Lang;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.network.HttpClient;
import org.ranobe.ranobe.sources.ChallengeRequiredException;
import org.ranobe.ranobe.sources.ChapterLockedException;
import org.ranobe.ranobe.sources.SignInRequiredException;
import org.ranobe.ranobe.sources.SearchFilterSupport;
import org.ranobe.ranobe.sources.Source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WtrLab implements Source, SearchFilterSupport {
    private static final int SOURCE_ID = 21;
    private static final String BASE_URL = "https://wtr-lab.com";
    public static final String SIGN_IN_REQUIRED_PREFIX = "WTR-LAB sign-in required";
    private static final String LANGUAGE = "en";
    private static final Pattern NOVEL_PATH = Pattern.compile("/novel/(\\d+)/([^/?#]+)");
    private static final Pattern CHAPTER_PATH = Pattern.compile("/chapter-(\\d+)");
    private static final Pattern CHAPTER_ID_QUERY = Pattern.compile("[?&]chapter_id=(\\d+)");
    // WTR-LAB stores some names as %{shown text|base64 of the original}, e.g.
    // "Medival %{Monster Slayer Saga|V2l0Y2hlcg}". The site shows the first part, so do the same.
    private static final Pattern NAME_PLACEHOLDER =
            Pattern.compile("%\\{([^{}]*)\\|[A-Za-z0-9+/_=-]*\\}");
    // Markers look like ※11⛬. The site also uses 〓 as the closing mark and sometimes prefixes "wtr-lab ".
    private static final Pattern GLOSSARY_MARKER = Pattern.compile("(?:wtr-lab\\s+)?※(\\d+)[⛬\\u3013]");

    @Override
    public DataSource metadata() {
        DataSource source = new DataSource();
        source.sourceId = SOURCE_ID;
        source.url = BASE_URL;
        source.name = "WTR-LAB";
        source.lang = Lang.eng;
        source.dev = "Nokkum";
        source.logo = BASE_URL + "/assets/favicon/favicon-96x96.png";
        source.isActive = true;
        return source;
    }

    @Override
    public List<Novel> novels(int page) throws Exception {
        String url = BASE_URL + "/" + LANGUAGE + "/novel-list?page=" + Math.max(page, 1);
        JSONObject props = pageProps(HttpClient.GET(url, headers()));
        return parseNovels(props.optJSONArray("series"));
    }

    @Override
    public Novel details(Novel novel) throws Exception {
        NovelCoordinates coordinates = coordinatesFromUrl(novel.url);
        String html = HttpClient.GET(novel.url, headers());
        JSONObject props = pageProps(html);
        JSONObject serie = props.getJSONObject("serie");
        JSONObject serieData = serie.getJSONObject("serie_data");

        applySeriesData(novel, serieData);
        novel.sourceId = SOURCE_ID;

        List<String> genres = new ArrayList<>();
        for (Element link : Jsoup.parse(html).select("a[href]")) {
            if (link.attr("href").contains("genre=")) {
                addIfPresent(genres, link.text());
            }
        }
        if (genres.isEmpty()) {
            JSONArray tags = props.optJSONArray("tags");
            if (tags != null) {
                for (int i = 0; i < tags.length(); i++) {
                    JSONObject tag = tags.optJSONObject(i);
                    if (tag != null) addIfPresent(genres, stringValue(tag, "title"));
                }
            }
        }
        novel.genres = genres;

        JSONArray names = serie.optJSONArray("names");
        if (names != null) {
            List<String> alternateNames = new ArrayList<>();
            for (int i = 0; i < names.length(); i++) {
                JSONObject name = names.optJSONObject(i);
                if (name == null) continue;
                addIfPresent(alternateNames, stringValue(name, "raw_title"));
                String otherTitle = stringValue(name, "title");
                if (!otherTitle.equalsIgnoreCase(novel.name)) addIfPresent(alternateNames, otherTitle);
            }
            novel.alternateNames = alternateNames;
        }

        // Keep the canonical novel URL from the site, even if the caller supplied a chapter URL.
        novel.url = BASE_URL + "/" + LANGUAGE + "/novel/" + coordinates.rawId + "/" + coordinates.slug;
        return novel;
    }

    @Override
    public List<Chapter> chapters(Novel novel) throws Exception {
        NovelCoordinates coordinates = coordinatesFromUrl(novel.url);
        String response;
        try {
            response = HttpClient.GET_WTR_LAB_API(
                    BASE_URL + "/api/chapters/" + coordinates.rawId,
                    headers()
            );
        } catch (IOException e) {
            if (isAuthenticationHttpError(e.getMessage())) throw signInRequired(e);
            throw e;
        }
        JSONObject payload = parseJson(response, "chapter list");
        JSONArray rows = payload.optJSONArray("chapters");
        List<Chapter> chapters = new ArrayList<>();
        if (rows == null) return chapters;

        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;

            int order = row.optInt("order", 0);
            long chapterId = row.optLong("id", 0L);
            if (order <= 0) continue;

            Chapter chapter = new Chapter(novel.url);
            chapter.name = firstNonEmpty(stringValue(row, "title"), stringValue(row, "name"));
            chapter.updated = stringValue(row, "updated_at");
            chapter.id = order;
            chapter.url = BASE_URL + "/" + LANGUAGE + "/novel/" + coordinates.rawId + "/"
                    + coordinates.slug + "/chapter-" + order;
            // Chapter.id in this app is a float/order; keep WTR-LAB's larger exact ID in the URL.
            if (chapterId > 0L) chapter.url += "?chapter_id=" + chapterId;
            chapters.add(chapter);
        }
        return chapters;
    }

    @Override
    public Chapter chapter(Chapter chapter) throws Exception {
        String novelUrl = firstNonEmpty(chapter.novelUrl, chapter.url);
        NovelCoordinates coordinates = coordinatesFromUrl(novelUrl);
        int order = chapterNumber(chapter.url);
        if (order <= 0 && chapter.id > 0) order = Math.round(chapter.id);
        if (order <= 0) throw new IOException("Could not determine the WTR-LAB chapter number.");

        JSONObject request = new JSONObject();
        request.put("translate", "ai");
        request.put("language", LANGUAGE);
        request.put("raw_id", Long.parseLong(coordinates.rawId));
        request.put("chapter_no", order);
        request.put("retry", false);
        request.put("force_retry", false);

        long chapterId = chapterId(chapter.url);
        if (chapterId > 0L) request.put("chapter_id", chapterId);

        String responseBody;
        try {
            responseBody = HttpClient.POST_JSON_WTR_LAB_API(
                    BASE_URL + "/api/reader/get",
                    request.toString()
            );
        } catch (IOException e) {
            if (isAuthenticationHttpError(e.getMessage())) throw signInRequired(e);
            throw e;
        }
        JSONObject response = parseJson(responseBody, "chapter content");
        if (!response.optBoolean("success")) {
            String message = failureMessage(response);
            // After a burst of chapters WTR-LAB answers {"requireTurnstile":true,...} until the reader
            // passes a Cloudflare Turnstile check, which only a browser can do.
            if (needsChallenge(response)) throw new ChallengeRequiredException(message);
            if (isLockedMessage(message)) throw new ChapterLockedException(message);
            if (isAuthenticationFailureMessage(message)) throw signInRequired(null);
            // Guests only get AI translations for the first 10 chapters of a novel. The refusal carries
            // no obvious message, so ask the server whether we are signed in before blaming the chapter.
            if (isSignedOut()) throw signInRequired(null);
            throw new IOException("WTR-LAB could not load this chapter"
                    + (message.isEmpty() ? "" : ": " + message)
                    + ". Response: " + abbreviate(response.toString(), 200));
        }

        // The reader API used to return the text inline (data.data.body). On 2026-10-02 it started
        // returning an envelope with a content_url that points at the same payload. Accept both.
        String content = payloadContent(response);
        if (content.isEmpty()) {
            String contentUrl = contentUrl(response);
            if (contentUrl != null) content = fetchContent(contentUrl);
        }
        if (content.isEmpty()) {
            if (isSignedOut()) throw signInRequired(null);
            throw noContent(response);
        }

        chapter.content = content;
        JSONObject chapterInfo = response.optJSONObject("chapter");
        if (chapterInfo != null) {
            chapter.name = firstNonEmpty(stringValue(chapterInfo, "title"), chapter.name);
            chapter.updated = firstNonEmpty(stringValue(chapterInfo, "updated_at"), chapter.updated);
        }
        return chapter;
    }

    // Chapter text from a reader payload ({data: {data: {body, glossary_data}}}), or "" if absent.
    static String payloadContent(JSONObject payload) {
        if (payload == null) return "";
        return chapterContent(payload.optJSONObject("data"), payload.optJSONObject("glossary_data"));
    }

    static String contentUrl(JSONObject response) {
        return resolveContentUrl(stringValue(response, "content_url"));
    }

    // Resolves the server-provided content_url against the site. Returns null for anything but HTTPS.
    static String resolveContentUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        if (value.regionMatches(true, 0, "https://", 0, 8)) return value;
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return BASE_URL + value;
        if (value.contains("://")) return null; // cleartext or an unexpected scheme
        return BASE_URL + "/" + value;
    }

    private static String fetchContent(String url) throws IOException {
        String body;
        try {
            body = HttpClient.GET_WTR_LAB_CONTENT(url, headers());
        } catch (IOException e) {
            if (isAuthenticationHttpError(e.getMessage())) throw signInRequired(e);
            throw e;
        }
        JSONObject payload = parseJson(body, "chapter content");
        if (!payload.optBoolean("success", true)) {
            String message = failureMessage(payload);
            if (needsChallenge(payload)) throw new ChallengeRequiredException(message);
            if (isLockedMessage(message)) throw new ChapterLockedException(message);
            if (isAuthenticationFailureMessage(message)) throw signInRequired(null);
            throw new IOException("WTR-LAB could not load this chapter"
                    + (message.isEmpty() ? "." : ": " + message));
        }
        return payloadContent(payload);
    }

    static boolean needsChallenge(JSONObject response) {
        return response != null && response.optBoolean("requireTurnstile");
    }

    // Fast readers get the Turnstile demand above sooner; a steady pace during bulk downloads avoids some of it.
    private static final long REQUEST_GAP_MS = 2000L;

    @Override
    public long requestGapMillis() {
        return REQUEST_GAP_MS;
    }

    static String abbreviate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    static IOException noContent(JSONObject response) {
        JSONObject chapterInfo = response.optJSONObject("chapter");
        // WTR-LAB locks AI translations past the first 50 chapters until someone unlocks them with
        // tickets. That is not a sign-in problem, so report it separately.
        if (chapterInfo != null && chapterInfo.optBoolean("locked")) {
            return new ChapterLockedException("WTR-LAB has not unlocked this AI translation yet.");
        }
        List<String> fields = new ArrayList<>();
        for (java.util.Iterator<String> keys = response.keys(); keys.hasNext(); ) fields.add(keys.next());
        return new IOException("WTR-LAB returned no chapter content (response fields: "
                + join(fields, ", ") + ").");
    }

    public static boolean isSignInRequired(String message) {
        if (message == null) return false;
        return message.toLowerCase(java.util.Locale.ROOT)
                .startsWith(SIGN_IN_REQUIRED_PREFIX.toLowerCase(java.util.Locale.ROOT));
    }

    // "locked" / "unlock_required" count; "blocked" (e.g. a Cloudflare block) and "clock" do not.
    private static final Pattern LOCKED_MESSAGE =
            Pattern.compile("(?<![a-z])(?:un)?lock(?:ed)?(?![a-z])", Pattern.CASE_INSENSITIVE);

    static boolean isLockedMessage(String message) {
        return message != null && LOCKED_MESSAGE.matcher(message).find();
    }

    private static boolean isAuthenticationFailureMessage(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("need_login")
                || normalized.contains("needs_login")
                || normalized.contains("login_required")
                || normalized.contains("sign_in_required")
                || normalized.contains("auth_required")
                || normalized.contains("authentication_required")
                || normalized.contains("unauthorized")
                || normalized.contains("unauthenticated")
                || normalized.contains("login required")
                || normalized.contains("sign in required")
                || normalized.contains("authentication required")
                || normalized.contains("you need to login")
                || normalized.contains("you need to log in")
                || normalized.contains("please sign in")
                || normalized.contains("please log in")
                || normalized.contains("please login");
    }

    private static boolean isAuthenticationHttpError(String message) {
        return message != null
                && (message.startsWith("HTTP 401 ")
                || message.startsWith("HTTP 403 ")
                || message.startsWith("HTTP 30"));
    }

    private static IOException signInRequired(Exception cause) {
        return new SignInRequiredException(SIGN_IN_REQUIRED_PREFIX + " to read this chapter.", cause);
    }

    // Asks the server whether this app's own requests carry a signed-in session. Returns true only
    // when it is sure the answer is no; any doubt (network error, odd reply) returns false so a real
    // failure is never blamed on sign-in.
    private static boolean isSignedOut() {
        try {
            String body = HttpClient.GET_WTR_LAB_API(BASE_URL + "/api/auth/get-session", headers());
            return Boolean.TRUE.equals(signedOutFromSessionBody(body));
        } catch (Exception e) {
            return false;
        }
    }

    // Asks WTR-LAB to end the session this app is using. Best effort: a failure is ignored, because the
    // caller always forgets the session locally afterwards. Makes a network call, so run it off the
    // main thread.
    public static void endSessionOnServer() {
        try {
            HttpClient.POST_JSON_WTR_LAB_API(BASE_URL + "/api/auth/sign-out", "{}");
        } catch (Exception ignored) {
            // still signed out on this device
        }
    }

    // What WTR-LAB reports about the session that this app's own requests carry.
    public static final class SessionStatus {
        // TRUE = signed in, FALSE = signed out, null = could not tell.
        public final Boolean signedIn;
        // Display name when signed in and the server gave one, otherwise "".
        public final String name;

        SessionStatus(Boolean signedIn, String name) {
            this.signedIn = signedIn;
            this.name = name == null ? "" : name;
        }
    }

    // Makes a network call, so run it off the main thread.
    public static SessionStatus checkSession() {
        try {
            String body = HttpClient.GET_WTR_LAB_API(BASE_URL + "/api/auth/get-session", headers());
            Boolean signedOut = signedOutFromSessionBody(body);
            if (signedOut == null) return new SessionStatus(null, "");
            if (signedOut) return new SessionStatus(false, "");
            return new SessionStatus(true, sessionUserName(body));
        } catch (Exception e) {
            return new SessionStatus(null, "");
        }
    }

    // The account's display name from a get-session reply. Deliberately never the e-mail address.
    static String sessionUserName(String body) {
        try {
            Object value = new org.json.JSONTokener(body == null ? "" : body.trim()).nextValue();
            if (!(value instanceof JSONObject)) return "";
            JSONObject user = ((JSONObject) value).optJSONObject("user");
            if (user == null) return "";
            for (String key : new String[]{"user_name", "username", "name"}) {
                String text = stringValue(user, key);
                if (!text.isEmpty()) return text;
            }
        } catch (org.json.JSONException ignored) {
            // fall through
        }
        return "";
    }

    // TRUE = signed out, FALSE = signed in, null = can't tell. Better Auth answers `null` when signed out.
    static Boolean signedOutFromSessionBody(String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return null;
        try {
            Object value = new org.json.JSONTokener(trimmed).nextValue();
            if (value == JSONObject.NULL) return Boolean.TRUE;
            if (value instanceof JSONObject) {
                JSONObject session = (JSONObject) value;
                boolean hasUser = session.opt("user") != null && session.opt("user") != JSONObject.NULL;
                boolean hasSession = session.opt("session") != null && session.opt("session") != JSONObject.NULL;
                return !(hasUser || hasSession);
            }
        } catch (org.json.JSONException ignored) {
            // not JSON (for example an HTML error page)
        }
        return null;
    }

    // First non-empty text among the fields WTR-LAB might use for an error message.
    static String failureMessage(JSONObject response) {
        for (String key : new String[]{"error", "message", "msg", "reason", "detail", "code"}) {
            String text = stringValue(response, key);
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    // Search results include each novel's status, so the app can filter them by it.
    @Override
    public boolean filtersStatusItself() {
        return false;
    }

    @Override
    public boolean reportsStatusInResults() {
        return true;
    }

    @Override
    public boolean supportsGenreFilter() {
        return false;
    }

    @Override
    public List<Novel> search(Filter filters, int page) throws Exception {
        // The public quick-search endpoint returns a bounded result list and has no page parameter.
        if (page > 1 || !filters.hashKeyword()) return new ArrayList<>();
        String keyword = filters.getKeyword() == null ? "" : filters.getKeyword().trim();
        if (keyword.isEmpty()) return new ArrayList<>();

        JSONObject request = new JSONObject();
        request.put("text", keyword);
        JSONObject response = parseJson(
                HttpClient.POST_JSON(BASE_URL + "/api/search", request.toString()),
                "search results"
        );
        if (!response.optBoolean("success")) {
            String message = stringValue(response, "error");
            throw new IOException("WTR-LAB search failed" + (message.isEmpty() ? "." : ": " + message));
        }
        return parseNovels(response.optJSONArray("data"));
    }

    private static List<Novel> parseNovels(JSONArray records) {
        List<Novel> novels = new ArrayList<>();
        if (records == null) return novels;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;

            String rawId = stringValue(record, "raw_id");
            String slug = stringValue(record, "slug");
            if (rawId.isEmpty() || slug.isEmpty()) continue;

            Novel novel = new Novel(
                    BASE_URL + "/" + LANGUAGE + "/novel/" + rawId + "/" + slug,
                    SOURCE_ID
            );
            applySeriesData(novel, record);
            novels.add(novel);
        }
        return novels;
    }

    private static void applySeriesData(Novel novel, JSONObject record) {
        JSONObject data = record.optJSONObject("data");
        if (data == null) data = new JSONObject();

        novel.name = stringValue(data, "title");
        novel.cover = absoluteUrl(stringValue(data, "image"));
        novel.summary = stringValue(data, "description");

        String author = stringValue(data, "author");
        if (!author.isEmpty()) {
            List<String> authors = new ArrayList<>();
            authors.add(author);
            novel.authors = authors;
        }

        novel.status = statusLabel(record.optInt("status", -1));
        novel.lastKnownChapterCount = record.optInt(
                "chapter_count",
                record.optInt("raw_chapter_count", 0)
        );
        Object rating = record.opt("rating");
        if (rating instanceof Number) novel.rating = ((Number) rating).floatValue();

        String createdAt = stringValue(record, "created_at");
        if (createdAt.length() >= 4) {
            try {
                novel.year = Integer.parseInt(createdAt.substring(0, 4));
            } catch (NumberFormatException ignored) {
                novel.year = 0;
            }
        }
    }

    private static String statusLabel(int status) {
        switch (status) {
            case 0:
                return "Ongoing";
            case 1:
                return "Completed";
            case 2:
                return "Hiatus";
            case 3:
                return "Dropped";
            default:
                return "Unknown";
        }
    }

    private static String bodyText(Object body) {
        List<String> paragraphs = new ArrayList<>();
        if (body instanceof JSONArray) {
            JSONArray parts = (JSONArray) body;
            for (int i = 0; i < parts.length(); i++) {
                Object part = parts.opt(i);
                if (part instanceof String) {
                    addIfPresent(paragraphs, Jsoup.parseBodyFragment((String) part).text());
                } else if (part instanceof JSONObject) {
                    addIfPresent(paragraphs, stringValue((JSONObject) part, "text"));
                }
            }
        } else if (body instanceof String) {
            addIfPresent(paragraphs, Jsoup.parseBodyFragment((String) body).text());
        }
        return join(paragraphs, "\n\n");
    }

    static String chapterContent(JSONObject readerData) {
        return chapterContent(readerData, null);
    }

    static String chapterContent(JSONObject readerData, JSONObject responseGlossaryData) {
        JSONObject translated = readerData == null ? null : readerData.optJSONObject("data");
        Object body = translated == null ? null : translated.opt("body");

        JSONObject glossaryData = readerData == null ? null : readerData.optJSONObject("glossary_data");
        if (glossaryData == null && translated != null) {
            glossaryData = translated.optJSONObject("glossary_data");
        }
        if (glossaryData == null) glossaryData = responseGlossaryData;
        return replaceGlossaryMarkers(bodyText(body), glossaryData);
    }

    static String replaceGlossaryMarkers(String content, JSONObject glossaryData) {
        if (content == null || content.isEmpty() || glossaryData == null) return content;
        Object terms = glossaryData.opt("terms");
        if (terms == null || terms == JSONObject.NULL) return content;

        Matcher matcher = GLOSSARY_MARKER.matcher(content);
        StringBuffer replaced = new StringBuffer();
        while (matcher.find()) {
            String term;
            try {
                term = glossaryTerm(terms, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                term = null;
            }
            matcher.appendReplacement(
                    replaced,
                    Matcher.quoteReplacement(term == null ? matcher.group() : term)
            );
        }
        matcher.appendTail(replaced);
        return replaced.toString();
    }

    private static String glossaryTerm(Object terms, int index) {
        if (index < 0) return null;
        Object term = null;
        String key = String.valueOf(index);
        if (terms instanceof JSONArray) {
            JSONArray termRows = (JSONArray) terms;
            if (index < termRows.length()) term = termRows.opt(index);
        } else if (terms instanceof JSONObject) {
            term = ((JSONObject) terms).opt(key);
        }
        return glossaryTermText(term);
    }

    private static String glossaryTermText(Object term) {
        if (term instanceof String) {
            String value = ((String) term).trim();
            return value.isEmpty() ? null : value;
        }
        if (term instanceof JSONArray) {
            // WTR-LAB sends each glossary term as an array; the first entry is the text to show.
            return glossaryTermText(((JSONArray) term).opt(0));
        }
        if (!(term instanceof JSONObject)) return null;

        JSONObject termObject = (JSONObject) term;
        String[] textFields = {
                "display", "display_name", "target", "target_term", "translated_term",
                "translation", "translated", "text", "name", "term_name", "term", "value", "label"
        };
        for (String field : textFields) {
            String value = stringValue(termObject, field);
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    private static JSONObject pageProps(String html) throws IOException {
        Element nextData = Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");
        if (nextData == null) throw new IOException("WTR-LAB returned a page without novel data.");
        try {
            return new JSONObject(nextData.data())
                    .getJSONObject("props")
                    .getJSONObject("pageProps");
        } catch (JSONException e) {
            throw new IOException("Could not read WTR-LAB page data.", e);
        }
    }

    private static JSONObject parseJson(String body, String description) throws IOException {
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("Could not read WTR-LAB " + description + ".", e);
        }
    }

    private static NovelCoordinates coordinatesFromUrl(String url) throws IOException {
        Matcher matcher = NOVEL_PATH.matcher(url == null ? "" : url);
        if (!matcher.find()) throw new IOException("Could not read the WTR-LAB novel URL.");
        return new NovelCoordinates(matcher.group(1), matcher.group(2));
    }

    private static int chapterNumber(String url) {
        Matcher matcher = CHAPTER_PATH.matcher(url == null ? "" : url);
        return matcher.find() ? parseInt(matcher.group(1)) : 0;
    }

    private static long chapterId(String url) {
        Matcher matcher = CHAPTER_ID_QUERY.matcher(url == null ? "" : url);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String absoluteUrl(String url) {
        if (url.isEmpty() || url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        return BASE_URL + (url.startsWith("/") ? url : "/" + url);
    }

    private static String stringValue(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        return replacePlaceholders(String.valueOf(value)).trim();
    }

    // Replaces each %{shown|base64} placeholder with its shown text.
    static String replacePlaceholders(String text) {
        if (text == null || !text.contains("%{")) return text;
        Matcher matcher = NAME_PLACEHOLDER.matcher(text);
        StringBuffer replaced = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(replaced, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(replaced);
        return replaced.toString();
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.trim().isEmpty() ? (second == null ? "" : second.trim()) : first.trim();
    }

    private static void addIfPresent(List<String> values, String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.isEmpty() && !values.contains(normalized)) values.add(normalized);
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) joined.append(delimiter);
            joined.append(value);
        }
        return joined.toString();
    }

    private static HashMap<String, String> headers() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/json");
        headers.put("Referer", BASE_URL + "/");
        return headers;
    }

    private static final class NovelCoordinates {
        final String rawId;
        final String slug;

        NovelCoordinates(String rawId, String slug) {
            this.rawId = rawId;
            this.slug = slug;
        }
    }
}