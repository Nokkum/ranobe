package org.ranobe.ranobe.sources;

import org.jsoup.nodes.Element;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.util.SearchFilters;

import java.util.ArrayList;
import java.util.List;

// What the Madara WordPress theme gives a search: a status filter in the address, and a status and
// genres on every result card. Light Novel Heaven, WuxiaWorld and WordRain69 all use it.
public final class MadaraSearch {
    private MadaraSearch() {
    }

    // The value the theme's status filter takes, or "" for a status it does not have.
    public static String statusParam(String status) {
        switch (SearchFilters.canonicalStatus(status)) {
            case SearchFilters.ONGOING:
                return "on-going";
            case SearchFilters.COMPLETED:
                return "end";
            case SearchFilters.HIATUS:
                return "on-hold";
            case SearchFilters.DROPPED:
                return "canceled";
            default:
                return "";
        }
    }

    // The part of the search address that filters by status; "" when there is nothing to filter.
    public static String statusQuery(String status) {
        String param = statusParam(status);
        return param.isEmpty() ? "" : "&status%5B%5D=" + param;
    }

    // The genres shown on a result card (links, or plain comma-separated text).
    public static List<String> genresFrom(Element card) {
        List<String> genres = new ArrayList<>();
        Element content = card.selectFirst(".mg_genres .summary-content");
        if (content == null) return genres;
        List<String> linked = content.select("a").eachText();
        if (!linked.isEmpty()) {
            for (String genre : linked) {
                if (!genre.trim().isEmpty()) genres.add(genre.trim());
            }
            return genres;
        }
        for (String part : content.text().split(",")) {
            if (!part.trim().isEmpty()) genres.add(part.trim());
        }
        return genres;
    }

    public static String statusFrom(Element card) {
        Element status = card.selectFirst(".mg_status .summary-content");
        return status == null ? "" : status.text().trim();
    }

    // Copies the card's genres and status onto the novel, so the app can filter results by genre.
    public static void fillFromCard(Novel novel, Element card) {
        novel.genres = genresFrom(card);
        String status = statusFrom(card);
        if (!status.isEmpty()) novel.status = status;
    }
}
