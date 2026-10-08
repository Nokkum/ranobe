package org.ranobe.ranobe.ui.search.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.models.DataSource;
import org.ranobe.ranobe.models.Filter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.network.repository.Repository;
import org.ranobe.ranobe.sources.SearchFilterSupport;
import org.ranobe.ranobe.sources.Source;
import org.ranobe.ranobe.sources.SourceManager;
import org.ranobe.ranobe.util.SearchFilters;
import org.ranobe.ranobe.util.SearchRanking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SearchViewModel extends ViewModel {
    // Id of the row that lists matches from the user's own library.
    public static final int LIBRARY_ID = -1;
    private static final int LIBRARY_LIMIT = 30;
    // When a filter the app applies itself empties a whole page, look at this many following pages
    // before giving up for now.
    private static final int MAX_AUTO_PAGES = 5;

    // repository callbacks arrive on worker threads; state is only touched on the main thread
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<List<Row>> rows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> failedSources = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<String>> skippedSources = new MutableLiveData<>(Collections.emptyList());
    private final LinkedHashMap<Integer, SourceState> states = new LinkedHashMap<>();
    // sources in the order their first results arrived, so rows already on screen never move
    private final List<Integer> arrival = new ArrayList<>();
    private List<Novel> libraryResults = Collections.emptyList();
    private Filter filter = new Filter();
    // bumped on every new search so late answers from an older one are dropped
    private int generation = 0;

    // what to search: null means every enabled source
    private Set<Integer> selectedSources = null;
    private String status = SearchFilters.ANY;
    private String genre = SearchFilters.ANY;

    // One source's results so far, and where its paging stands.
    private static final class SourceState {
        final DataSource source;
        // The source cannot filter status itself but reports it, so the app drops what does not match.
        final boolean filterStatusHere;
        // Likewise for genres.
        final boolean filterGenreHere;
        final List<Novel> novels = new ArrayList<>();
        final Set<String> seenUrls = new HashSet<>();
        // every address the source has sent, shown or not: a page that adds none of them ends the source
        final Set<String> rawUrls = new HashSet<>();
        int autoPages = 0; // pages fetched in a row without anything to show
        int page = 0; // last page that loaded; 0 while the first one has not
        boolean pending;
        boolean endReached;
        boolean failed;

        SourceState(DataSource source, boolean filterStatusHere, boolean filterGenreHere) {
            this.source = source;
            this.filterStatusHere = filterStatusHere;
            this.filterGenreHere = filterGenreHere;
        }
    }

    // What the screen shows for one source (or the library): a snapshot, safe to keep.
    public static final class Row {
        public final int sourceId;
        // The source's name; null for the library row, whose title the screen translates.
        public final String title;
        public final List<Novel> novels;
        public final boolean loadingMore;
        public final boolean endReached;

        Row(int sourceId, String title, List<Novel> novels, boolean loadingMore, boolean endReached) {
            this.sourceId = sourceId;
            this.title = title;
            this.novels = novels;
            this.loadingMore = loadingMore;
            this.endReached = endReached;
        }

        public boolean isLibrary() {
            return sourceId == LIBRARY_ID;
        }
    }

    // Which sources can use which filter, for the filter dialog's hint.
    public static final class FilterSupportSummary {
        // Sources that can filter every status.
        public final List<String> status = new ArrayList<>();
        // Sources that can only tell some statuses apart.
        public final List<String> statusPartial = new ArrayList<>();
        public final List<String> genre = new ArrayList<>();
    }

    private static List<DataSource> enabledSources() {
        List<DataSource> sources = new ArrayList<>();
        for (Integer id : SourceManager.getSources().keySet()) {
            if (!Ranobe.isSourceEnabled(id)) continue;
            DataSource dataSource = SourceManager.getSource(id).metadata();
            if (dataSource.isActive) sources.add(dataSource);
        }
        return sources;
    }

    private static SearchFilterSupport supportOf(Source source) {
        return source instanceof SearchFilterSupport ? (SearchFilterSupport) source : null;
    }

    private boolean canHonorFilters(Source source) {
        SearchFilterSupport support = supportOf(source);
        if (!status.isEmpty() && (support == null || !support.supportsStatus(status))) return false;
        return genre.isEmpty()
                || (support != null && (support.supportsGenreFilter() || support.reportsGenresInResults()));
    }

    private boolean filterGenreHere(Source source) {
        SearchFilterSupport support = supportOf(source);
        return !genre.isEmpty() && support != null && !support.supportsGenreFilter() && support.reportsGenresInResults();
    }

    private boolean filterStatusHere(Source source) {
        SearchFilterSupport support = supportOf(source);
        return !status.isEmpty() && support != null && !support.filtersStatusItself() && support.reportsStatusInResults();
    }

    // for the screen

    public LiveData<List<Row>> getRows() {
        return rows;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    // Names of the sources whose last request failed. Empty when nothing is wrong.
    public LiveData<List<String>> getFailedSources() {
        return failedSources;
    }

    // Names of the selected sources that were left out because they cannot apply the active filters.
    public LiveData<List<String>> getSkippedSources() {
        return skippedSources;
    }

    public String getKeyword() {
        return filter.getKeyword();
    }

    public String getStatus() {
        return status;
    }

    public String getGenre() {
        return genre;
    }

    // The sources the user can choose between: the enabled ones.
    public List<DataSource> availableSources() {
        return enabledSources();
    }

    // Ids of the sources a search covers right now.
    public Set<Integer> getSelectedIds() {
        if (selectedSources != null) return new HashSet<>(selectedSources);
        Set<Integer> all = new HashSet<>();
        for (DataSource source : enabledSources()) all.add(source.sourceId);
        return all;
    }

    public FilterSupportSummary describeFilterSupport() {
        FilterSupportSummary summary = new FilterSupportSummary();
        for (DataSource dataSource : enabledSources()) {
            SearchFilterSupport support = supportOf(SourceManager.getSource(dataSource.sourceId));
            if (support == null) continue;
            boolean anyStatus = false;
            boolean everyStatus = true;
            for (String candidate : SearchFilters.STATUSES) {
                if (support.supportsStatus(candidate)) anyStatus = true;
                else everyStatus = false;
            }
            if (everyStatus) summary.status.add(dataSource.name);
            else if (anyStatus) summary.statusPartial.add(dataSource.name);
            if (support.supportsGenreFilter() || support.reportsGenresInResults()) summary.genre.add(dataSource.name);
        }
        return summary;
    }

    // Applies the source selection and the filters. A change to the filters searches again; a change to
    // the selection only adds or drops the sources concerned, so the others keep what they loaded.
    public void setOptions(Set<Integer> selection, String newStatus, String newGenre) {
        String wantedStatus = newStatus == null ? SearchFilters.ANY : newStatus;
        String wantedGenre = newGenre == null ? SearchFilters.ANY : newGenre;

        Set<Integer> enabled = new HashSet<>();
        for (DataSource source : enabledSources()) enabled.add(source.sourceId);
        // choosing every enabled source is the same as no choice, so sources enabled later are included
        Set<Integer> normalized = selection == null || selection.containsAll(enabled) ? null : new HashSet<>(selection);

        boolean filtersChanged = !wantedStatus.equals(status) || !wantedGenre.equals(genre);
        boolean selectionChanged = !Objects.equals(normalized, selectedSources);
        status = wantedStatus;
        genre = wantedGenre;
        selectedSources = normalized;

        String keyword = filter.getKeyword();
        if (keyword == null) return; // nothing searched yet; the options apply to the next search
        if (filtersChanged) {
            restart(keyword);
        } else if (selectionChanged) {
            reconcileSelection();
        }
    }

    public void search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;
        Filter next = buildFilter(keyword.trim());
        // same query again (e.g. back from details): keep the results / the search in flight
        if (next.equals(filter) && !states.isEmpty()) return;
        restart(keyword.trim());
    }

    private Filter buildFilter(String keyword) {
        Filter next = new Filter();
        next.addFilter(Filter.FILTER_KEYWORD, keyword);
        if (!status.isEmpty()) next.addFilter(Filter.FILTER_STATUS, status);
        if (!genre.isEmpty()) next.addFilter(Filter.FILTER_GENRE, genre);
        return next;
    }

    private void restart(String keyword) {
        filter = buildFilter(keyword);
        int current = ++generation;
        states.clear();
        arrival.clear();
        libraryResults = Collections.emptyList();

        List<String> skipped = new ArrayList<>();
        for (SourceState state : stateForSelectedSources(skipped)) states.put(state.source.sourceId, state);
        skippedSources.setValue(skipped);
        // the library has no status or genre to filter by, so it only answers an unfiltered search
        if (status.isEmpty() && genre.isEmpty()) searchLibrary(keyword, current);
        for (SourceState state : states.values()) requestPage(state, 1, current);
        publish();
    }

    private List<SourceState> stateForSelectedSources(List<String> skippedNames) {
        List<SourceState> list = new ArrayList<>();
        for (DataSource dataSource : enabledSources()) {
            if (selectedSources != null && !selectedSources.contains(dataSource.sourceId)) continue;
            Source source = SourceManager.getSource(dataSource.sourceId);
            if (!canHonorFilters(source)) {
                skippedNames.add(dataSource.name);
                continue;
            }
            list.add(new SourceState(dataSource, filterStatusHere(source), filterGenreHere(source)));
        }
        return list;
    }

    // The selection changed but not the filters: drop the sources no longer chosen, add the new ones.
    private void reconcileSelection() {
        List<String> skipped = new ArrayList<>();
        List<SourceState> wanted = stateForSelectedSources(skipped);
        Set<Integer> wantedIds = new HashSet<>();
        for (SourceState state : wanted) wantedIds.add(state.source.sourceId);

        for (Iterator<Integer> it = states.keySet().iterator(); it.hasNext(); ) {
            if (!wantedIds.contains(it.next())) it.remove();
        }
        arrival.retainAll(wantedIds);
        for (SourceState state : wanted) {
            if (states.containsKey(state.source.sourceId)) continue;
            states.put(state.source.sourceId, state);
            requestPage(state, 1, generation);
        }
        skippedSources.setValue(skipped);
        publish();
    }

    // Fetches the next page for one source. Called as the user nears the end of its row.
    public void loadMore(int sourceId) {
        SourceState state = states.get(sourceId);
        if (state == null || state.page == 0 || state.pending || state.endReached || state.failed) return;
        requestPage(state, state.page + 1, generation);
        publish();
    }

    // Asks again every source whose last request failed.
    public void retryFailed() {
        for (SourceState state : states.values()) {
            if (state.failed && !state.pending) requestPage(state, state.page + 1, generation);
        }
        publish();
    }

    private void requestPage(SourceState state, int page, int searchGeneration) {
        state.pending = true;
        state.failed = false;
        new Repository(state.source.sourceId).search(filter, page, new Repository.Callback<List<Novel>>() {
            @Override
            public void onComplete(List<Novel> result) {
                main.post(() -> onPage(searchGeneration, state, page, result));
            }

            @Override
            public void onError(Exception e) {
                // one broken source shouldn't hide the others' results
                main.post(() -> onPageFailed(searchGeneration, state));
            }
        });
    }

    private void onPage(int searchGeneration, SourceState state, int page, List<Novel> result) {
        if (searchGeneration != generation || states.get(state.source.sourceId) != state) return;
        state.pending = false;
        List<Novel> pageItems = result == null ? Collections.<Novel>emptyList() : result;

        // novels this source had not sent before, whatever the filters below then drop
        int fresh = 0;
        for (Novel novel : pageItems) {
            if (novel.url != null && state.rawUrls.add(novel.url)) fresh++;
        }

        List<Novel> shown = pageItems;
        if (state.filterStatusHere) shown = SearchFilters.filterByStatus(status, shown);
        if (state.filterGenreHere) shown = SearchFilters.filterByGenre(genre, shown);

        int added = 0;
        // best title matches first within the page; earlier pages stay where they are
        for (Novel novel : SearchRanking.rank(filter.getKeyword(), shown)) {
            if (novel.url != null && state.seenUrls.add(novel.url)) {
                state.novels.add(novel);
                added++;
            }
        }
        state.page = page;
        // an empty page, or one that only repeats earlier novels, means the source has no more
        state.endReached = fresh == 0;
        if (added > 0) state.autoPages = 0;
        if (page == 1 && added > 0 && !arrival.contains(state.source.sourceId)) arrival.add(state.source.sourceId);

        // a filter applied here can empty a whole page while later pages would match: look further
        if (added == 0 && fresh > 0 && state.autoPages < MAX_AUTO_PAGES) {
            state.autoPages++;
            requestPage(state, page + 1, searchGeneration);
        }
        publish();
    }

    private void onPageFailed(int searchGeneration, SourceState state) {
        if (searchGeneration != generation || states.get(state.source.sourceId) != state) return;
        state.pending = false;
        state.failed = true;
        publish();
    }

    // Matches from the user's own library, shown first.
    private void searchLibrary(String keyword, int searchGeneration) {
        RanobeDatabase.databaseExecutor.execute(() -> {
            List<Novel> matches = new ArrayList<>();
            try {
                for (Novel novel : RanobeDatabase.database().novels().listSync()) {
                    if (matchesLibrary(keyword, novel)) matches.add(novel);
                }
            } catch (RuntimeException e) {
                // no library results rather than a broken search
                matches.clear();
            }
            List<Novel> ranked = SearchRanking.rank(keyword, matches);
            List<Novel> shown = ranked.size() > LIBRARY_LIMIT ? new ArrayList<>(ranked.subList(0, LIBRARY_LIMIT)) : ranked;
            main.post(() -> {
                if (searchGeneration != generation) return;
                libraryResults = shown;
                publish();
            });
        });
    }

    private static boolean matchesLibrary(String keyword, Novel novel) {
        if (SearchRanking.matchesAllWords(keyword, novel.name)) return true;
        if (novel.alternateNames != null) {
            for (String alternate : novel.alternateNames) {
                if (SearchRanking.matchesAllWords(keyword, alternate)) return true;
            }
        }
        return false;
    }

    private void publish() {
        List<Row> list = new ArrayList<>();
        if (!libraryResults.isEmpty()) {
            list.add(new Row(LIBRARY_ID, null, new ArrayList<>(libraryResults), false, true));
        }
        for (Integer id : arrival) {
            SourceState state = states.get(id);
            if (state == null) continue;
            list.add(new Row(id, state.source.name, new ArrayList<>(state.novels),
                    state.pending && state.page > 0, state.endReached));
        }
        List<String> failed = new ArrayList<>();
        boolean firstPagePending = false;
        for (SourceState state : states.values()) {
            if (state.failed) failed.add(state.source.name);
            // still searching and nothing to show yet, which also covers pages emptied by a filter
            if (state.pending && state.novels.isEmpty()) firstPagePending = true;
        }
        rows.setValue(list);
        failedSources.setValue(failed);
        loading.setValue(firstPagePending);
    }
}
