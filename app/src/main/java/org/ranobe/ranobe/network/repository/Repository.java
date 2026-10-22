package org.ranobe.ranobe.network.repository;

import org.ranobe.ranobe.config.RanobeSettings;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Filter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.sources.AuthorSearch;
import org.ranobe.ranobe.sources.Source;
import org.ranobe.ranobe.sources.SourceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Repository {
    // shared across all repositories; a new pool per instance spun up fresh threads for every request
    private static final Executor executor = Executors.newCachedThreadPool();
    private final Source source;

    public Repository() {
        this.source = SourceManager.getSource(RanobeSettings.get().getCurrentSource());
    }

    // novels opened from old history rows may not carry a source id; fall back to the current one
    public long requestGapMillis() {
        return source.requestGapMillis();
    }

    public Repository(int sourceId) {
        this.source = SourceManager.getSource(sourceId > 0 ? sourceId : RanobeSettings.get().getCurrentSource());
    }

    public void novels(int page, Callback<List<Novel>> callback) {
        executor.execute(() -> {
            try {
                List<Novel> result = source.novels(page);
                callback.onComplete(result);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void details(Novel novel, Callback<Novel> callback) {
        executor.execute(() -> {
            try {
                Novel result = source.details(novel);
                callback.onComplete(result);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void chapters(Novel novel, Callback<List<Chapter>> callback) {
        executor.execute(() -> {
            try {
                List<Chapter> items = source.chapters(novel);
                callback.onComplete(items);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void chapter(Chapter chapter, Callback<Chapter> callback) {
        executor.execute(() -> {
            try {
                Chapter item = source.chapter(chapter);
                callback.onComplete(item);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // Synchronous version for use on background threads (e.g. DownloadService)
    public Chapter chapterSync(Chapter chapter) throws Exception {
        return source.chapter(chapter);
    }

    public void search(Filter filter, int page, Callback<List<Novel>> callback) {
        executor.execute(() -> {
            try {
                List<Novel> items = source.search(filter, page);
                callback.onComplete(items);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // One page for a source that can only filter by one status at a time, asked about several: a request per
    // status, the answers joined (a novel listed under two statuses once). The filter carries the keyword and
    // genre; each request gets one of the statuses. It fails only when every request failed, so one status
    // that errors doesn't hide what the others found.
    public void searchByStatuses(Filter filter, List<String> statuses, int page, Callback<List<Novel>> callback) {
        executor.execute(() -> {
            List<Novel> merged = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Exception lastError = null;
            boolean anyAnswered = false;
            for (String status : statuses) {
                Filter one = new Filter();
                one.addFilter(Filter.FILTER_KEYWORD, filter.getKeyword());
                if (filter.hasGenre()) one.addFilter(Filter.FILTER_GENRE, filter.getGenre());
                one.addFilter(Filter.FILTER_STATUS, status);
                try {
                    for (Novel novel : source.search(one, page)) {
                        if (novel.url == null || seen.add(novel.url)) merged.add(novel);
                    }
                    anyAnswered = true;
                } catch (Exception e) {
                    lastError = e;
                }
            }
            if (!anyAnswered && lastError != null) callback.onError(lastError);
            else callback.onComplete(merged);
        });
    }

    // Whether the source can list an author's novels at all (see AuthorSearch).
    public boolean supportsAuthorWorks() {
        return source instanceof AuthorSearch;
    }

    public void authorWorks(String author, int page, Callback<List<Novel>> callback) {
        executor.execute(() -> {
            try {
                if (!(source instanceof AuthorSearch)) {
                    callback.onComplete(new ArrayList<>());
                    return;
                }
                callback.onComplete(((AuthorSearch) source).authorWorks(author, page));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public interface Callback<T> {
        void onComplete(T result);

        void onError(Exception e);
    }
}
