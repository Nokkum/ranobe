package org.ranobe.ranobe.sources.en;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.DataSource;
import org.ranobe.ranobe.models.Filter;
import org.ranobe.ranobe.models.Lang;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.network.HttpClient;
import org.ranobe.ranobe.sources.Source;
import org.ranobe.ranobe.util.NumberUtils;
import org.ranobe.ranobe.util.SourceUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class NovelPing implements Source {

    private final String baseUrl = "https://novelping.com";
    private final int sourceId = 18;

    @Override
    public DataSource metadata() {
        DataSource source = new DataSource();
        source.sourceId = sourceId;
        source.url = baseUrl;
        source.name = "Novel Ping";
        source.lang = Lang.eng;
        source.dev = "ap-atul";
        source.logo = "https://novelping.com/img/logo.png";
        source.isActive = true;
        return source;
    }

    @Override
    public List<Novel> novels(int page) throws Exception {
        String web = page == 1
                ? baseUrl + "/sort/popular"
                : baseUrl + "/sort/popular?page=" + page;
        return parse(HttpClient.GET(web, new HashMap<>()));
    }

    private List<Novel> parse(String body) {
        List<Novel> items = new ArrayList<>();
        Element doc = Jsoup.parse(body).select("div.list-novel").first();

        if (doc == null) return items;

        for (Element element : doc.select("div.row")) {
            String url = element.select("h3.novel-title > a").attr("href").trim();

            if (!url.isEmpty()) {
                Novel item = new Novel(url);
                item.sourceId = sourceId;
                item.name = element.select("h3.novel-title > a").text().trim();
                item.cover = coverUrl(element.selectFirst("img"));
                items.add(item);
            }
        }

        return items;
    }

    @Override
    public Novel details(Novel novel) throws Exception {
        Element doc = Jsoup.parse(HttpClient.GET(novel.url, new HashMap<>()));

        novel.sourceId = sourceId;
        novel.name = doc.select("h3.title").first().text().trim();
        novel.cover = coverUrl(doc.selectFirst("div.book img"));
        if (novel.cover.isEmpty()) {
            novel.cover = absoluteCoverUrl(doc.select("meta[property=og:image]").attr("content"));
        }

        doc.select("div.desc-text").select("p").append("::");
        novel.summary = doc.select("div.desc-text").text().replaceAll("::", "\n\n").trim();
        novel.rating = NumberUtils.toFloat(doc.select("span[itemprop=ratingValue]").text()) / 2;

        for (Element element : doc.select("ul.info-meta > li")) {
            String header = element.select("h3").text();

            if (header.equalsIgnoreCase("Author:")) {
                novel.authors = Arrays.asList(element.select("a").text().split(","));
            } else if (header.equalsIgnoreCase("Genre:")) {
                List<String> genres = new ArrayList<>();
                for (Element a : element.select("a")) genres.add(a.text());
                novel.genres = genres;
            } else if (header.equalsIgnoreCase("Alternative names:")) {
                novel.alternateNames = Arrays.asList(element.select("a").text().split(","));
            } else if (header.equalsIgnoreCase("Status:")) {
                novel.status = element.select("a").text().trim();
            }
        }

        return novel;
    }

    private String coverUrl(Element image) {
        if (image == null) return "";

        String[] attributes = {"data-src", "data-lazy-src", "data-original", "src"};
        for (String attribute : attributes) {
            String value = image.attr(attribute).trim();
            if (!value.isEmpty() && !value.toLowerCase().startsWith("data:")) {
                return absoluteCoverUrl(value);
            }
        }

        String srcset = image.attr("srcset").trim();
        if (!srcset.isEmpty()) {
            String firstCandidate = srcset.split(",")[0].trim().split("\\s+")[0];
            return absoluteCoverUrl(firstCandidate);
        }
        return "";
    }

    private String absoluteCoverUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) return "";
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return baseUrl + value;
        return value.replaceFirst("/novel_\\d+_\\d+/", "/novel/");
    }

    private String getNovelId(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }

    @Override
    public List<Chapter> chapters(Novel novel) throws Exception {
        List<Chapter> items = new ArrayList<>();
        String base = baseUrl.concat("/ajax/chapter-archive?novelId=").concat(getNovelId(novel.url));
        Element doc = Jsoup.parse(HttpClient.GET(base, new HashMap<>()));

        for (Element element : doc.select("a")) {
            Chapter item = new Chapter(novel.url);

            item.url = element.attr("href").trim();
            item.name = element.attr("title").trim();
            item.id = NumberUtils.toFloat(item.name);
            items.add(item);
        }

        return items;
    }

    @Override
    public Chapter chapter(Chapter chapter) throws Exception {
        Element doc = Jsoup.parse(HttpClient.GET(chapter.url, new HashMap<>()));

        chapter.content = "";
        doc.select("div.chr-c").select("p").append("::");
        doc.select("div.unlock-buttons").remove();
        chapter.content = SourceUtils.cleanContent(
                doc.select("div.chr-c").text().replaceAll("::", "\n\n\n").trim()
        );

        return chapter;
    }

    @Override
    public List<Novel> search(Filter filters, int page) throws IOException {
        if (filters.hashKeyword()) {
            String keyword = filters.getKeyword();
            String web = SourceUtils.buildUrl(baseUrl, "/search?keyword=", keyword, "&page=", String.valueOf(page));
            return parse(HttpClient.GET(web, new HashMap<>()));
        }
        return new ArrayList<>();
    }
}
