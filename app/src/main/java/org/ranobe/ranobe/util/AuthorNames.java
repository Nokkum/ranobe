package org.ranobe.ranobe.util;

import org.ranobe.ranobe.models.Novel;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Author names as sources give them: padded, repeated, sometimes a placeholder. Cleans them for display,
// and compares them so "Cuttlefish That Loves Diving" and "cuttlefish  that loves diving" are one author.
public final class AuthorNames {
    // what sites put where an author is missing; there is nothing to look up for these
    private static final Set<String> PLACEHOLDERS = new HashSet<>(Arrays.asList(
            "unknown", "unknown author", "n/a", "na", "none", "anonymous", "updating", "-"));

    private AuthorNames() {
    }

    // Trimmed, single-spaced, without blanks or repeats (case ignored); the order is kept.
    public static List<String> clean(List<String> names) {
        List<String> cleaned = new ArrayList<>();
        if (names == null) return cleaned;
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (name == null) continue;
            String tidy = name.trim().replaceAll("\\s+", " ");
            if (tidy.isEmpty() || !seen.add(tidy.toLowerCase(Locale.ROOT))) continue;
            cleaned.add(tidy);
        }
        return cleaned;
    }

    // Whether a name is a real one that can be looked up.
    public static boolean isLookupName(String name) {
        if (name == null) return false;
        String tidy = name.trim();
        return !tidy.isEmpty() && !PLACEHOLDERS.contains(tidy.toLowerCase(Locale.ROOT)) && !key(tidy).isEmpty();
    }

    // Letters and digits only, lower case, accents removed: "J. K. Rowling" and "jk rowling" share a key.
    static String key(String name) {
        if (name == null) return "";
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    // Whether the wanted author is one of the authors listed. A blank name matches nothing.
    public static boolean matches(String wanted, List<String> authors) {
        String want = key(wanted);
        if (want.isEmpty() || authors == null) return false;
        for (String author : authors) {
            if (want.equals(key(author))) return true;
        }
        return false;
    }

    // The novels written by the author. A novel that lists no authors can't be checked: it is kept when the
    // site already filtered by author (keepUnknown) and dropped when the list came from a plain text search.
    public static List<Novel> keepWrittenBy(String wanted, List<Novel> novels, boolean keepUnknown) {
        List<Novel> kept = new ArrayList<>();
        for (Novel novel : novels) {
            boolean listed = !clean(novel.authors).isEmpty();
            if (listed ? matches(wanted, novel.authors) : keepUnknown) kept.add(novel);
        }
        return kept;
    }

    // For the query part of an address; a space becomes "+", which every site here reads as a space.
    public static String encode(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return name;
        }
    }
}
