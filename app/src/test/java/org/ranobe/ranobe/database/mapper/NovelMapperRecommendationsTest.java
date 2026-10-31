package org.ranobe.ranobe.database.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.NovelMetadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NovelMapperRecommendationsTest {
    private static Novel tile(String url, int sourceId, String name, String cover) {
        Novel novel = new Novel(url, sourceId);
        novel.name = name;
        novel.cover = cover;
        return novel;
    }

    @Test
    public void recommendationsSurviveTheJsonRoundTrip() {
        List<Novel> items = Arrays.asList(
                tile("https://www.webnovel.com/book/1", 24, "One", "https://img/1.jpg"),
                tile("https://www.webnovel.com/book/2", 24, "Two", null));

        List<Novel> back = NovelMapper.recommendationsFromJson(NovelMapper.recommendationsToJson(items));

        assertEquals(2, back.size());
        assertEquals("https://www.webnovel.com/book/1", back.get(0).url);
        assertEquals("One", back.get(0).name);
        assertEquals("https://img/1.jpg", back.get(0).cover);
        assertEquals(24, back.get(0).sourceId);
        assertNull(back.get(1).cover);
    }

    @Test
    public void nothingToSaveGivesNull() {
        assertNull(NovelMapper.recommendationsToJson(null));
        assertNull(NovelMapper.recommendationsToJson(Collections.<Novel>emptyList()));
    }

    @Test
    public void aMissingOrBrokenCacheEntryShowsNoSuggestions() {
        assertTrue(NovelMapper.recommendationsFromJson(null).isEmpty());
        assertTrue(NovelMapper.recommendationsFromJson("").isEmpty());
        assertTrue(NovelMapper.recommendationsFromJson("not json").isEmpty());
        // one bad entry drops the lot rather than showing half a row
        assertTrue(NovelMapper.recommendationsFromJson("[{\"name\":\"no url\"}]").isEmpty());
    }

    @Test
    public void novelAndMetadataCarryThem() {
        Novel novel = new Novel("https://www.webnovel.com/book/9", 24);
        novel.recommendations = Collections.singletonList(tile("https://www.webnovel.com/book/1", 24, "One", "c"));

        NovelMetadata metadata = NovelMapper.ToNovelMetadata(novel);
        assertEquals(1, NovelMapper.ToNovel(metadata).recommendations.size());
        assertEquals("One", NovelMapper.ToNovel(metadata).recommendations.get(0).name);

        NovelMetadata cachedBeforeTheColumnExisted = new NovelMetadata("https://www.webnovel.com/book/9");
        assertTrue(NovelMapper.ToNovel(cachedBeforeTheColumnExisted).recommendations.isEmpty());
    }
}
