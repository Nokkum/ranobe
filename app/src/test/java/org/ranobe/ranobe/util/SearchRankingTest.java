package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchRankingTest {
    private static Novel novel(String name) {
        Novel novel = new Novel("https://example.net/" + name.hashCode());
        novel.name = name;
        return novel;
    }

    private static List<String> names(List<Novel> novels) {
        List<String> names = new ArrayList<>();
        for (Novel novel : novels) names.add(novel.name);
        return names;
    }

    @Test
    public void bestTitleMatchesComeFirst() {
        List<Novel> results = Arrays.asList(
                novel("A Story That Mentions Dragon Tamer Once"),
                novel("Worthless Profession: Dragon Tamer"),
                novel("Dragon Tamer"),
                novel("Dragon Tamer Academy"),
                novel("The Tamer And The Dragon"));

        List<String> ranked = names(SearchRanking.rank("dragon tamer", results));

        assertEquals(Arrays.asList(
                "Dragon Tamer",                                  // exact
                "Dragon Tamer Academy",                          // starts with
                "A Story That Mentions Dragon Tamer Once",       // contains the phrase
                "Worthless Profession: Dragon Tamer",            // contains the phrase
                "The Tamer And The Dragon"), ranked);            // all the words, other order
    }

    @Test
    public void resultsWithTheSameScoreKeepTheSourcesOwnOrder() {
        List<Novel> results = Arrays.asList(novel("Zebra Dragon Tamer"), novel("Apple Dragon Tamer"), novel("Mango Dragon Tamer"));

        assertEquals(Arrays.asList("Zebra Dragon Tamer", "Apple Dragon Tamer", "Mango Dragon Tamer"),
                names(SearchRanking.rank("dragon tamer", results)));
    }

    @Test
    public void caseAccentsAndPunctuationDoNotMatter() {
        List<Novel> results = Arrays.asList(novel("Something else"), novel("L\u2019\u00c9cole des H\u00e9ros"));

        assertEquals("L\u2019\u00c9cole des H\u00e9ros", SearchRanking.rank("ecole DES heros", results).get(0).name);
    }

    @Test
    public void blankKeywordAndMissingNamesAreHandled() {
        List<Novel> results = Arrays.asList(novel("B"), novel("A"));
        Novel unnamed = new Novel("https://example.net/x");

        assertEquals(Arrays.asList("B", "A"), names(SearchRanking.rank("  ", results)));
        assertEquals(Arrays.asList("B", "A"), names(SearchRanking.rank(null, results)));
        assertEquals(2, SearchRanking.rank("a", Arrays.asList(unnamed, novel("A"))).size());
        assertEquals("A", SearchRanking.rank("a", Arrays.asList(unnamed, novel("A"))).get(0).name);
    }

    @Test
    public void rankingDoesNotChangeTheInputList() {
        List<Novel> results = new ArrayList<>(Arrays.asList(novel("Other"), novel("Match")));

        SearchRanking.rank("match", results);

        assertEquals("Other", results.get(0).name);
    }

    @Test
    public void everyWordMustAppearForALibraryStyleMatch() {
        assertTrue(SearchRanking.matchesAllWords("tamer dragon", "Worthless Profession: Dragon Tamer"));
        assertTrue(SearchRanking.matchesAllWords("DRAGON", "dragon tamer"));
        assertFalse(SearchRanking.matchesAllWords("dragon knight", "Dragon Tamer"));
        assertFalse(SearchRanking.matchesAllWords("", "Dragon Tamer"));
        assertFalse(SearchRanking.matchesAllWords("dragon", null));
    }

    @Test
    public void normalizeKeepsLettersOfOtherAlphabets() {
        assertEquals("\u043c\u0438\u0440 \u043d\u043e\u0432\u0435\u043b\u043b", SearchRanking.normalize("  \u041c\u0438\u0440, \u041d\u043e\u0432\u0435\u043b\u043b! "));
        assertEquals("", SearchRanking.normalize("!!!"));
        assertEquals("", SearchRanking.normalize(null));
    }
}
