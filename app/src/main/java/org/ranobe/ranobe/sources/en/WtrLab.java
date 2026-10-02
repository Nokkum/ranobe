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
import org.ranobe.ranobe.sources.Source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WtrLab implements Source {
    private static final int SOURCE_ID = 21;
    private static final String BASE_URL = "https://wtr-lab.com";
    private static final String LANGUAGE = "en";
    private static final Pattern NOVEL_PATH = Pattern.compile("/novel/(\\d+)/([^/?#]+)");
    private static final Pattern CHAPTER_PATH = Pattern.compile("/chapter-(\\d+)");
    private static final Pattern CHAPTER_ID_QUERY = Pattern.compile("[?&]chapter_id=(\\d+)");

    @Override
    public DataSource metadata() {
        DataSource source = new DataSource();
        source.sourceId = SOURCE_ID;
        source.url = BASE_URL;
        source.name = "WTR-LAB";
        source.lang = Lang.eng;
        source.dev = "Ranobe";
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
        String response = HttpClient.GET(BASE_URL + "/api/chapters/" + coordinates.rawId, headers());
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
        request.put("chapter_no", "chapter-" + order);

        long chapterId = chapterId(chapter.url);
        if (chapterId > 0L) request.put("chapter_id", chapterId);

        JSONObject response = parseJson(
                HttpClient.POST_JSON(BASE_URL + "/api/reader/get", request.toString()),
                "chapter content"
        );
        if (!response.optBoolean("success")) {
            String message = firstNonEmpty(stringValue(response, "error"), stringValue(response, "code"));
            throw new IOException("WTR-LAB could not load this chapter"
                    + (message.isEmpty() ? "." : ": " + message));
        }

        JSONObject readerData = response.optJSONObject("data");
        JSONObject translated = readerData == null ? null : readerData.optJSONObject("data");
        Object body = translated == null ? null : translated.opt("body");
        String content = bodyText(body);
        if (content.isEmpty()) throw new IOException("WTR-LAB returned an empty chapter.");

        chapter.content = content;
        JSONObject chapterInfo = response.optJSONObject("chapter");
        if (chapterInfo != null) {
            chapter.name = firstNonEmpty(stringValue(chapterInfo, "title"), chapter.name);
            chapter.updated = firstNonEmpty(stringValue(chapterInfo, "updated_at"), chapter.updated);
        }
        return chapter;
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
        return String.valueOf(value).trim();
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