package org.ranobe.ranobe.ui.author.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.database.mapper.NovelMapper;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.NovelMetadata;
import org.ranobe.ranobe.network.repository.Repository;
import org.ranobe.ranobe.util.AuthorNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The works of one author: the novels saved on this device by that name (any source), and, for a source that
// can list an author's novels, the pages it returns. Both go into one list without repeats.
public class AuthorViewModel extends ViewModel {
    // repository callbacks arrive on a worker thread; the state below is only touched on the main thread
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<List<Novel>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> failed = new MutableLiveData<>(false);
    // fires once when a page fails while novels are already on screen (the screen offers a retry)
    private final MutableLiveData<Boolean> pageError = new MutableLiveData<>();
    // what is on screen, so a novel found twice (saved here and listed by the source) shows once
    private final Set<String> shownUrls = new HashSet<>();
    // what the source has returned, to tell when it has run out of pages
    private final Set<String> sourceUrls = new HashSet<>();

    private boolean opened = false;
    private String author;
    private String excludeUrl;
    private Repository repository;
    private boolean lookupSupported = false;
    private boolean endReached = false;
    private boolean sourceLoading = false;
    private int pendingWork = 0;
    private int page = 0;

    public LiveData<List<Novel>> getNovels() {
        return items;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    // whether the last page the source was asked for failed
    public LiveData<Boolean> hasFailed() {
        return failed;
    }

    public LiveData<Boolean> getPageError() {
        return pageError;
    }

    public boolean isEndReached() {
        return endReached;
    }

    // whether the source can list this author's novels; if not, only the saved ones are shown
    public boolean supportsLookup() {
        return lookupSupported;
    }

    // excludeUrl is the novel the reader came from, which isn't one of the author's *other* works.
    // The page keeps its state across the back stack, so opening it again changes nothing.
    public void open(String author, int sourceId, String excludeUrl) {
        if (opened) return;
        opened = true;
        this.author = author;
        this.excludeUrl = excludeUrl;
        repository = new Repository(sourceId);
        lookupSupported = repository.supportsAuthorWorks();
        endReached = !lookupSupported;

        loadSaved();
        loadMore();
    }

    // the next page from the source
    public void loadMore() {
        if (!lookupSupported || endReached || sourceLoading) return;
        sourceLoading = true;
        failed.setValue(false);
        startWork();
        int nextPage = page + 1;

        repository.authorWorks(author, nextPage, new Repository.Callback<List<Novel>>() {
            @Override
            public void onComplete(List<Novel> result) {
                main.post(() -> onPageLoaded(nextPage, result));
            }

            @Override
            public void onError(Exception e) {
                main.post(() -> onPageFailed());
            }
        });
    }

    public void clearPageError() {
        pageError.setValue(null);
    }

    private void loadSaved() {
        startWork();
        RanobeDatabase.databaseExecutor.execute(() -> {
            List<Novel> found = new ArrayList<>();
            try {
                for (Novel novel : RanobeDatabase.database().novels().listSync()) {
                    if (AuthorNames.matches(author, novel.authors)) found.add(novel);
                }
                for (NovelMetadata cached : RanobeDatabase.database().novelMetadata().listByAuthorText(author)) {
                    if (AuthorNames.matches(author, cached.authors)) found.add(NovelMapper.ToNovel(cached));
                }
                Collections.sort(found, (a, b) -> String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name)));
            } catch (RuntimeException ignored) {
                // what is saved is a bonus: the page still works from the source without it
            }
            main.post(() -> {
                add(found);
                finishWork();
            });
        });
    }

    private void onPageLoaded(int loadedPage, List<Novel> result) {
        sourceLoading = false;
        page = loadedPage;
        int fromSource = 0;
        for (Novel novel : result) {
            if (sourceUrls.add(novel.url)) fromSource++;
        }
        // an empty page, or one that only repeats earlier novels, means the source has no more
        endReached = fromSource == 0;
        add(result);
        finishWork();
    }

    private void onPageFailed() {
        sourceLoading = false;
        failed.setValue(true);
        if (!shownUrls.isEmpty()) pageError.setValue(true);
        finishWork();
    }

    // appends the novels that aren't on screen yet
    private void add(List<Novel> novels) {
        List<Novel> current = new ArrayList<>(items.getValue() == null ? new ArrayList<>() : items.getValue());
        for (Novel novel : novels) {
            if (novel.url == null || novel.url.equals(excludeUrl)) continue;
            if (shownUrls.add(novel.url)) current.add(novel);
        }
        items.setValue(current);
    }

    private void startWork() {
        pendingWork++;
        loading.setValue(true);
    }

    private void finishWork() {
        pendingWork--;
        if (pendingWork <= 0) {
            pendingWork = 0;
            loading.setValue(false);
        }
    }
}
