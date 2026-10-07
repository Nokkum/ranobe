package org.ranobe.ranobe.sources.en;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.DataSource;
import org.ranobe.ranobe.models.Filter;
import org.ranobe.ranobe.models.Lang;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.network.HttpClient;
import org.ranobe.ranobe.sources.Source;
import org.ranobe.ranobe.util.SourceUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Divine Dao Library runs the Fictioneer WordPress theme.
public class DivineDaoLibrary implements Source {
    public static final String BASE_URL = "https://www.divinedaolibrary.com";
    public static final int SOURCE_ID = 22;

    private static final int PAGE_SIZE = 20;
    private static final String STORY_FIELDS = "id,slug,link,title,modified,_embedded,_links.wp:featuredmedia";
    private static final String LINE_BREAK = "\u0001";
    // not \\b: the page glues "March 24, 2026" and "Mar 24, '26" together as "2026Mar", with no gap between them
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");

    @Override
    public DataSource metadata() {
        DataSource source = new DataSource();
        source.sourceId = SOURCE_ID;
        source.url = BASE_URL;
        source.name = "Divine Dao Library";
        source.lang = Lang.eng;
        source.dev = "Nokkum";
        source.logo = BASE_URL + "/wp-content/uploads/2025/02/cropped-DDL-Main-Logo-270x270.png";
        source.isActive = true;
        return source;
    }

    // A small hobby site: pause between chapters during bulk downloads rather than hammer it. There is no
    // @Override on purpose, so this compiles whether or not Source declares requestGapMillis().
    public long requestGapMillis() {
        return 400L;
    }

    @Override
    public List<Novel> novels(int page) throws Exception {
        return storyList(page, null);
    }

    @Override
    public List<Novel> search(Filter filters, int page) throws Exception {
        if (!filters.hashKeyword()) return novels(page);
        return storyList(page, filters.getKeyword());
    }

    @Override
    public Novel details(Novel novel) throws Exception {
        return parseDetails(HttpClient.GET(novel.url, new HashMap<>()), novel);
    }

    @Override
    public List<Chapter> chapters(Novel novel) throws Exception {
        return parseChapters(HttpClient.GET(novel.url, new HashMap<>()), novel);
    }

    @Override
    public Chapter chapter(Chapter chapter) throws Exception {
        String content = parseChapterHtml(HttpClient.GET(chapter.url, new HashMap<>()));
        if (content.isEmpty()) {
            // The REST copy of the chapter is plain JSON, in case the page layout changes.
            content = parseChapterRest(HttpClient.GET(restChapterUrl(chapter.url), new HashMap<>()));
        }
        if (content.isEmpty()) throw new IOException("Divine Dao Library returned no text for this chapter.");
        chapter.content = content;
        return chapter;
    }

    // lists

    private List<Novel> storyList(int page, String keyword) throws Exception {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/wp-json/wp/v2/fcn_story?per_page=").append(PAGE_SIZE)
                .append("&page=").append(Math.max(page, 1));
        if (keyword == null) {
            url.append("&orderby=modified&order=desc");
        } else {
            url.append("&search=").append(URLEncoder.encode(keyword, "UTF-8"));
        }
        url.append("&_embed=wp:featuredmedia&_fields=").append(STORY_FIELDS);
        return parseStories(HttpClient.GET(url.toString(), new HashMap<>()));
    }

    // WordPress answers an error object, not a list, once the page number is past the last page.
    static List<Novel> parseStories(String json) {
        List<Novel> items = new ArrayList<>();
        if (json == null) return items;
        try {
            Object value = new JSONTokener(json.trim()).nextValue();
            if (!(value instanceof JSONArray)) return items;
            JSONArray stories = (JSONArray) value;
            for (int i = 0; i < stories.length(); i++) {
                JSONObject story = stories.optJSONObject(i);
                if (story == null) continue;
                String url = story.optString("link", "");
                if (url.isEmpty()) continue;

                Novel novel = new Novel(url);
                novel.sourceId = SOURCE_ID;
                JSONObject title = story.optJSONObject("title");
                novel.name = plainText(title == null ? "" : title.optString("rendered", ""));
                novel.cover = coverFrom(story.optJSONObject("_embedded"));
                items.add(novel);
            }
        } catch (JSONException e) {
            // not JSON (for example an error page): nothing to list
        }
        return items;
    }

    private static String coverFrom(JSONObject embedded) {
        if (embedded == null) return "";
        JSONArray media = embedded.optJSONArray("wp:featuredmedia");
        JSONObject first = media == null ? null : media.optJSONObject(0);
        if (first == null) return "";
        JSONObject details = first.optJSONObject("media_details");
        JSONObject sizes = details == null ? null : details.optJSONObject("sizes");
        JSONObject medium = sizes == null ? null : sizes.optJSONObject("medium");
        String url = medium == null ? "" : medium.optString("source_url", "");
        return url.isEmpty() ? first.optString("source_url", "") : url;
    }

    // details

    static Novel parseDetails(String html, Novel novel) {
        Document doc = Jsoup.parse(html);
        novel.sourceId = SOURCE_ID;

        String name = text(doc.selectFirst("h1.story__identity-title"));
        if (!name.isEmpty()) novel.name = name;

        String cover = "";
        Element thumbnail = doc.selectFirst("figure.story__thumbnail a");
        if (thumbnail != null) cover = thumbnail.attr("href").trim();
        if (cover.isEmpty()) {
            Element og = doc.selectFirst("meta[property=og:image]");
            if (og != null) cover = og.attr("content").trim();
        }
        if (cover.isEmpty()) {
            Element image = doc.selectFirst("figure.story__thumbnail img");
            if (image != null) cover = image.attr("src").trim();
        }
        if (!cover.isEmpty()) novel.cover = cover;

        Element summary = doc.selectFirst("section.story__summary");
        String author = summary == null ? "" : parseAuthor(summary);
        // The "by" line names whoever posted the story, which is the translator, so it is only a fallback.
        if (author.isEmpty()) author = text(doc.selectFirst(".story__identity-meta a.author"));
        novel.authors = new ArrayList<>();
        if (!author.isEmpty()) novel.authors.add(author);

        novel.alternateNames = summary == null ? new ArrayList<>() : parseOtherNames(summary);
        novel.summary = summary == null ? "" : parseDescription(summary);

        List<String> genres = new ArrayList<>();
        for (Element pill : doc.select(".story__taxonomies a.tag-pill")) {
            String genre = text(pill);
            if (!genre.isEmpty()) genres.add(genre);
        }
        if (genres.isEmpty()) {
            // some stories have tags but no genres
            for (Element pill : doc.select(".story__tags-and-warnings a.tag-pill")) {
                String tag = text(pill);
                if (!tag.isEmpty() && genres.size() < 8) genres.add(tag);
            }
        }
        novel.genres = genres;

        String status = text(doc.selectFirst(".story__meta .story__status"));
        if (!status.isEmpty()) novel.status = status;

        Element published = doc.selectFirst(".story__meta .story__date");
        if (published != null) {
            Matcher matcher = YEAR.matcher(published.text());
            if (matcher.find()) novel.year = Integer.parseInt(matcher.group());
        }
        return novel;
    }

    // "Author: Wanta" in the summary names the real author.
    static String parseAuthor(Element summary) {
        for (Element heading : summary.select("h1, h2, h3, h4")) {
            String heading_text = clean(heading.text());
            if (heading_text.toLowerCase(Locale.ROOT).startsWith("author")) return afterColon(heading_text);
        }
        return "";
    }

    static String afterColon(String text) {
        int colon = Math.max(text.indexOf(':'), text.indexOf('：'));
        if (colon >= 0) return clean(text.substring(colon + 1));
        return clean(text.replaceFirst("(?i)^authors?\\s*", ""));
    }

    static List<String> parseOtherNames(Element summary) {
        List<String> names = new ArrayList<>();
        boolean collecting = false;
        for (Element child : summary.children()) {
            String tag = child.tagName();
            if (isHeading(tag)) {
                collecting = clean(child.text()).toLowerCase(Locale.ROOT).startsWith("other name");
                continue;
            }
            if (tag.equals("hr")) {
                collecting = false;
                continue;
            }
            if (!collecting) continue;
            for (String part : clean(child.text()).split("[;\\n]")) {
                String name = clean(part);
                if (!name.isEmpty()) names.add(name);
            }
        }
        return names;
    }

    // The text after the "Description" heading, or every paragraph when there is no such heading.
    static String parseDescription(Element summary) {
        List<String> paragraphs = new ArrayList<>();
        boolean sawHeading = false;
        boolean collecting = false;
        for (Element child : summary.children()) {
            String tag = child.tagName();
            if (isHeading(tag)) {
                boolean description = clean(child.text()).toLowerCase(Locale.ROOT).startsWith("description");
                sawHeading |= description;
                collecting = description;
                continue;
            }
            String text = clean(child.text());
            if (collecting && !text.isEmpty() && !tag.equals("hr")) paragraphs.add(text);
        }
        if (!sawHeading) {
            for (Element paragraph : summary.select("p")) {
                String text = clean(paragraph.text());
                if (!text.isEmpty()) paragraphs.add(text);
            }
        }
        return String.join("\n\n", paragraphs);
    }

    // chapters

    // The story page lists every published chapter, oldest first. Only the folding is done by script.
    static List<Chapter> parseChapters(String html, Novel novel) {
        Document doc = Jsoup.parse(html);
        List<Chapter> items = new ArrayList<>();
        float number = 0;
        for (Element row : doc.select("li.chapter-group__list-item")) {
            Element link = row.selectFirst("a.chapter-group__list-item-link");
            if (link == null) continue; // the "Show more" row
            String url = link.attr("href").trim();
            if (url.isEmpty()) continue;

            Chapter chapter = new Chapter(novel.url);
            chapter.url = url;
            chapter.name = clean(link.text());
            chapter.id = ++number;
            chapter.updated = clean(row.select("time.chapter-group__list-item-date .list-view").text());
            items.add(chapter);
        }
        return items;
    }

    static String parseChapterHtml(String html) {
        Element root = Jsoup.parse(html, BASE_URL).selectFirst("#chapter-content");
        if (root == null) return "";
        Element wrapper = root.selectFirst("div.chapter-formatting");
        return blocksToText(wrapper != null ? wrapper : root);
    }

    static String parseChapterRest(String json) {
        if (json == null) return "";
        try {
            Object value = new JSONTokener(json.trim()).nextValue();
            JSONObject chapter = value instanceof JSONArray ? ((JSONArray) value).optJSONObject(0) : null;
            if (chapter == null) return "";
            JSONObject content = chapter.optJSONObject("content");
            String html = content == null ? "" : content.optString("rendered", "");
            return html.isEmpty() ? "" : blocksToText(Jsoup.parseBodyFragment(html, BASE_URL).body());
        } catch (JSONException e) {
            return "";
        }
    }

    // The REST address of a chapter, from the last part of its page address.
    static String restChapterUrl(String chapterUrl) {
        String path = chapterUrl;
        int cut = path.indexOf('?');
        if (cut >= 0) path = path.substring(0, cut);
        cut = path.indexOf('#');
        if (cut >= 0) path = path.substring(0, cut);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String slug = path.substring(path.lastIndexOf('/') + 1);
        return BASE_URL + "/wp-json/wp/v2/fcn_chapter?slug=" + slug + "&_fields=id,title,content";
    }

    // One block of text per paragraph. Empty "&nbsp;" spacer paragraphs are dropped.
    static String blocksToText(Element root) {
        List<String> blocks = new ArrayList<>();
        collectBlocks(root, blocks);
        if (blocks.isEmpty()) {
            String text = clean(root.text());
            if (!text.isEmpty()) blocks.add(text);
        }
        return String.join("\n\n", blocks);
    }

    private static void collectBlocks(Element parent, List<String> blocks) {
        for (Element child : parent.children()) {
            String tag = child.tagName();
            if (tag.equals("hr")) {
                blocks.add("* * *");
            } else if (tag.equals("table")) {
                addIfNotEmpty(blocks, tableText(child));
            } else if (tag.equals("ul") || tag.equals("ol")) {
                addIfNotEmpty(blocks, listText(child));
            } else if (isContainer(child)) {
                collectBlocks(child, blocks);
            } else {
                addIfNotEmpty(blocks, blockText(child));
            }
        }
    }

    private static void addIfNotEmpty(List<String> blocks, String block) {
        if (!block.isEmpty()) blocks.add(block);
    }

    // A div, section or quote that wraps further blocks rather than holding text itself.
    private static boolean isContainer(Element element) {
        String tag = element.tagName();
        if (!(tag.equals("div") || tag.equals("section") || tag.equals("article")
                || tag.equals("blockquote") || tag.equals("center"))) {
            return false;
        }
        for (Element child : element.children()) {
            String childTag = child.tagName();
            if (childTag.equals("p") || childTag.equals("div") || childTag.equals("table")
                    || childTag.equals("ul") || childTag.equals("ol") || childTag.equals("hr")
                    || childTag.equals("blockquote") || isHeading(childTag)) {
                return true;
            }
        }
        return false;
    }

    private static String blockText(Element element) {
        StringBuilder block = new StringBuilder();
        for (Element image : element.select("img")) {
            String src = firstNonEmpty(image.absUrl("data-lazy-src"), image.absUrl("data-src"), image.absUrl("src"));
            if (!src.isEmpty()) block.append(SourceUtils.imageTag(src)).append('\n');
        }
        // text() folds a <br> into a space, so mark each one first and turn the marks into new lines
        for (Element lineBreak : element.select("br")) lineBreak.replaceWith(new TextNode(LINE_BREAK));
        List<String> lines = new ArrayList<>();
        for (String line : element.text().replace(LINE_BREAK, "\n").split("\n")) {
            String text = clean(line);
            if (!text.isEmpty()) lines.add(text);
        }
        block.append(String.join("\n", lines));
        return block.toString().trim();
    }

    private static String tableText(Element table) {
        List<String> rows = new ArrayList<>();
        for (Element row : table.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element cell : row.select("th, td")) {
                String text = clean(cell.text());
                if (!text.isEmpty()) cells.add(text);
            }
            if (!cells.isEmpty()) rows.add(String.join(" | ", cells));
        }
        return String.join("\n", rows);
    }

    private static String listText(Element list) {
        List<String> items = new ArrayList<>();
        for (Element item : list.children()) {
            String text = clean(item.text());
            if (!text.isEmpty()) items.add("- " + text);
        }
        return String.join("\n", items);
    }

    // helpers

    private static boolean isHeading(String tag) {
        return tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1));
    }

    private static String plainText(String html) {
        return html == null || html.isEmpty() ? "" : clean(Jsoup.parse(html).text());
    }

    private static String text(Element element) {
        return element == null ? "" : clean(element.text());
    }

    /** Trims, treating a non-breaking space as an ordinary space (String.trim() leaves it in). */
    static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
