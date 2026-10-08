package org.ranobe.ranobe.util;

import org.ranobe.ranobe.models.Novel;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Orders search results so the best title matches come first.
public final class SearchRanking {
    static final int EXACT = 0;
    static final int PREFIX = 1;
    static final int PHRASE = 2;
    static final int ALL_WORDS = 3;
    static final int OTHER = 4;

    private SearchRanking() {
    }

    // A new list with the best matches first. The input is left alone.
    public static List<Novel> rank(String keyword, List<Novel> novels) {
        List<Novel> ranked = new ArrayList<>(novels);
        String query = normalize(keyword);
        if (query.isEmpty()) return ranked;
        Collections.sort(ranked, (a, b) -> Integer.compare(score(query, a.name), score(query, b.name)));
        return ranked;
    }

    // Whether every word of the keyword appears in the text (any order, case and accents ignored).
    public static boolean matchesAllWords(String keyword, String text) {
        String query = normalize(keyword);
        if (query.isEmpty()) return false;
        String haystack = normalize(text);
        for (String word : query.split(" ")) {
            if (!haystack.contains(word)) return false;
        }
        return true;
    }

    static int score(String normalizedQuery, String name) {
        String title = normalize(name);
        if (title.isEmpty()) return OTHER;
        if (title.equals(normalizedQuery)) return EXACT;
        if (title.startsWith(normalizedQuery)) return PREFIX;
        if (title.contains(normalizedQuery)) return PHRASE;
        for (String word : normalizedQuery.split(" ")) {
            if (!title.contains(word)) return OTHER;
        }
        return ALL_WORDS;
    }

    // Lower case, accents removed, anything that is not a letter or digit turned into one space.
    static String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String plain = decomposed.replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
