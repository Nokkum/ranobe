package org.ranobe.ranobe.util;

import org.ranobe.ranobe.models.Novel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// The values the search filters offer, and the rules for matching a novel's status.
public final class SearchFilters {
    public static final String ANY = "";
    public static final String ONGOING = "Ongoing";
    public static final String COMPLETED = "Completed";
    public static final String HIATUS = "Hiatus";
    public static final String DROPPED = "Dropped";

    public static final List<String> STATUSES = Collections.unmodifiableList(
            Arrays.asList(ONGOING, COMPLETED, HIATUS, DROPPED));

    // Common genres. Each source maps the name to its own genre list, and ignores one it lacks.
    public static final List<String> GENRES = Collections.unmodifiableList(Arrays.asList(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Harem", "Historical", "Horror",
            "Isekai", "LitRPG", "Martial Arts", "Mystery", "Romance", "School Life",
            "Science Fiction", "Slice of Life", "Supernatural", "Xianxia", "Xuanhuan"));

    private SearchFilters() {
    }

    // Maps the many ways sites word a status onto the four we filter by; "" when it is none of them.
    public static String canonicalStatus(String text) {
        if (text == null) return ANY;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("ongoing") || lower.contains("on-going") || lower.contains("on going")) return ONGOING;
        if (lower.contains("complet") || lower.contains("finish")) return COMPLETED;
        if (lower.contains("hiatus") || lower.contains("on hold") || lower.contains("on-hold")) return HIATUS;
        if (lower.contains("drop") || lower.contains("cancel") || lower.contains("abandon")) return DROPPED;
        return ANY;
    }

    // A novel whose status is unknown never matches a status that was asked for.
    public static boolean matchesStatus(String wanted, String actual) {
        String want = canonicalStatus(wanted);
        if (want.isEmpty()) return true;
        return want.equals(canonicalStatus(actual));
    }

    // Lower case letters and digits only, with the spellings of science fiction made one.
    static String genreKey(String name) {
        if (name == null) return "";
        String key = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
        if (key.equals("scifi") || key.equals("sf")) return "sciencefiction";
        return key;
    }

    // A novel with no known genres never matches a genre that was asked for.
    public static boolean matchesGenre(String wanted, List<String> genres) {
        String want = genreKey(wanted);
        if (want.isEmpty()) return true;
        if (genres == null) return false;
        for (String genre : genres) {
            if (want.equals(genreKey(genre))) return true;
        }
        return false;
    }

    public static List<Novel> filterByGenre(String wanted, List<Novel> novels) {
        List<Novel> kept = new ArrayList<>();
        for (Novel novel : novels) {
            if (matchesGenre(wanted, novel.genres)) kept.add(novel);
        }
        return kept;
    }

    public static List<Novel> filterByStatus(String wanted, List<Novel> novels) {
        List<Novel> kept = new ArrayList<>();
        for (Novel novel : novels) {
            if (matchesStatus(wanted, novel.status)) kept.add(novel);
        }
        return kept;
    }
}
