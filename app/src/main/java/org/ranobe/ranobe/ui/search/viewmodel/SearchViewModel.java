package org.ranobe.ranobe.ui.search.viewmodel;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.ranobe.ranobe.App;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.models.DataSource;
import org.ranobe.ranobe.models.Filter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.network.repository.Repository;
import org.ranobe.ranobe.sources.SearchFilterSupport;
import org.ranobe.ranobe.sources.Source;
import org.ranobe.ranobe.sources.SourceManager;
import org.ranobe.ranobe.util.ChapterRanges;
import org.ranobe.ranobe.util.SearchFilters;
import org.ranobe.ranobe.util.SearchPlan;
import org.ranobe.ranobe.util.SearchPreferences;
import org.ranobe.ranobe.util.SearchRanking;
import org.ranobe.ranobe.util.SearchTimeouts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearchViewModel extends ViewModel {
    // Id of the row that lists matches from the user's own library.
    public static final int LIBRARY_ID = -1;
    private static final int LIBRARY_LIMIT = 30;
    // names for the three option sections that can be opened out (see isPickerOpen)
    public static final String PICKER_SOURCES = "sources";
    public static final String PICKER_CHAPTERS = "chapters";
    public static final String PICKER_STATUS = "status";
    // When a filter the app applies itself empties a whole page, look at this many following pages
    // before giving up for now.
    private static final int MAX_AUTO_PAGES = 5;

    // repository callbacks arrive on worker threads; state is only touched on the main thread
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<List<Row>> rows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<List<String>> failedSources = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<String>> skippedSources = new MutableLiveData<>(Collections.emptyList());
    // set when a search has waited the configured time with nothing to show; cleared as soon as a result comes
    private final MutableLiveData<Boolean> timedOut = new MutableLiveData<>(false);
    private final Runnable timeoutTask = this::onTimeout;
    private boolean timeoutArmed = false;
    private final LinkedHashMap<Integer, SourceState> states = new LinkedHashMap<>();
    // sources in the order their first results arrived, so rows already on screen never move
    private final List<Integer> arrival = new ArrayList<>();
    private List<Novel> libraryResults = Collections.emptyList();
    private Filter filter = new Filter();
    // bumped on every new search so late answers from an older one are dropped
    private int generation = 0;

    // What to search. For the sources, statuses and chapter ranges an empty set means "all" / "any": the
    // person has narrowed nothing. A set with members is the narrowing.
    private final Set<Integer> selectedSources = new LinkedHashSet<>();
    private final Set<String> statuses = new LinkedHashSet<>();
    private final Set<String> chapterRanges = new LinkedHashSet<>();
    private String genre = SearchFilters.ANY;
    // The option sections the person has opened out with nothing chosen yet. A section with a choice is open
    // regardless; this only remembers the open-but-empty state across screens.
    private final Set<String> openPickers = new HashSet<>();

    // One source's results so far, and where its paging stands.
    private static final class SourceState {
        final DataSource source;
        // The source cannot filter status itself but reports it, so the app drops what does not match.
        final boolean filterStatusHere;
        // The source filters status itself, one status per request: the statuses to ask it about, one request
        // each. Empty when the source isn't asked to filter status that way.
        final List<String> nativeStatuses;
        // Likewise for genres.
        final boolean filterGenreHere;
        // The source reports chapter counts, so the app drops the novels outside the chosen ranges.
        final boolean filterChaptersHere;
        final List<Novel> novels = new ArrayList<>();
        final Set<String> seenUrls = new HashSet<>();
        // every address the source has sent, shown or not: a page that adds none of them ends the source
        final Set<String> rawUrls = new HashSet<>();
        int autoPages = 0; // pages fetched in a row without anything to show
        int page = 0; // last page that loaded; 0 while the first one has not
        boolean pending;
        boolean endReached;
        boolean failed;

        SourceState(DataSource source, boolean filterStatusHere, List<String> nativeStatuses,
                    boolean filterGenreHere, boolean filterChaptersHere) {
            this.source = source;
            this.filterStatusHere = filterStatusHere;
            this.nativeStatuses = nativeStatuses;
            this.filterGenreHere = filterGenreHere;
            this.filterChaptersHere = filterChaptersHere;
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

    // True once a search has waited the configured time with nothing to show and sources still pending.
    public LiveData<Boolean> hasTimedOut() {
        return timedOut;
    }

    public String getKeyword() {
        return filter.getKeyword();
    }

    // The statuses chosen; empty means any.
    public Set<String> getStatuses() {
        return new LinkedHashSet<>(statuses);
    }

    // The chapter ranges chosen (their ids); empty means all.
    public Set<String> getChapterRanges() {
        return new LinkedHashSet<>(chapterRanges);
    }

    public String getGenre() {
        return genre;
    }

    // Whether an option section is opened out. It is whenever something is chosen in it.
    public boolean isPickerOpen(String name) {
        if (PICKER_SOURCES.equals(name) && !selectedSources.isEmpty()) return true;
        if (PICKER_CHAPTERS.equals(name) && !chapterRanges.isEmpty()) return true;
        if (PICKER_STATUS.equals(name) && !statuses.isEmpty()) return true;
        return openPickers.contains(name);
    }

    public void setPickerOpen(String name, boolean open) {
        if (open) openPickers.add(name);
        else openPickers.remove(name);
    }

    // The sources the user can choose between: the enabled ones.
    public List<DataSource> availableSources() {
        return enabledSources();
    }

    // The sources the person has picked; empty means they haven't narrowed it, so a search covers them all.
    public Set<Integer> getSourceSelection() {
        return new LinkedHashSet<>(selectedSources);
    }

    // Ids of the sources a search covers right now: the picked ones, or every enabled one when none is picked.
    private Set<Integer> effectiveSourceIds() {
        Set<Integer> enabled = new LinkedHashSet<>();
        for (DataSource source : enabledSources()) enabled.add(source.sourceId);
        Set<Integer> picked = new LinkedHashSet<>(selectedSources);
        // a picked source may have been switched off in Settings since
        picked.retainAll(enabled);
        return picked.isEmpty() ? enabled : picked;
    }

    // Names of the enabled sources that can filter by genre, for the filter dialog's hint.
    public List<String> genreSupportingSources() {
        List<String> names = new ArrayList<>();
        for (DataSource dataSource : enabledSources()) {
            SearchFilterSupport support = supportOf(SourceManager.getSource(dataSource.sourceId));
            if (support != null && (support.supportsGenreFilter() || support.reportsGenresInResults())) {
                names.add(dataSource.name);
            }
        }
        return names;
    }

    // Applies the source selection and the filters. A change to the filters searches again; a change to the
    // selection only adds or drops the sources concerned, so the others keep what they loaded. An empty set
    // means "all" for the sources, statuses and chapter ranges.
    public void setOptions(Set<Integer> selection, Set<String> newStatuses, Set<String> newRanges, String newGenre) {
        Set<String> wantedStatuses = new LinkedHashSet<>(SearchFilters.orderedStatuses(newStatuses));
        Set<String> wantedRanges = new LinkedHashSet<>(ChapterRanges.orderedIds(newRanges));
        String wantedGenre = newGenre == null ? SearchFilters.ANY : newGenre;

        Set<Integer> coveredBefore = effectiveSourceIds();
        boolean filtersChanged = !wantedStatuses.equals(statuses) || !wantedRanges.equals(chapterRanges)
                || !wantedGenre.equals(genre);
        statuses.clear();
        statuses.addAll(wantedStatuses);
        chapterRanges.clear();
        chapterRanges.addAll(wantedRanges);
        genre = wantedGenre;
        selectedSources.clear();
        if (selection != null) selectedSources.addAll(selection);
        boolean selectionChanged = !coveredBefore.equals(effectiveSourceIds());

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

    // Forgets the current search, as when the search field is emptied: no rows, no notes, no wait. Answers
    // that are still on their way are dropped, because the generation moves on. The chosen sources and
    // filters stay; they apply to the next search.
    public void clear() {
        if (filter.getKeyword() == null && states.isEmpty() && libraryResults.isEmpty()) return;
        generation++;
        filter = new Filter();
        states.clear();
        arrival.clear();
        libraryResults = Collections.emptyList();
        cancelTimeout();
        skippedSources.setValue(Collections.emptyList());
        rows.setValue(Collections.emptyList());
        failedSources.setValue(Collections.emptyList());
        loading.setValue(false);
    }

    // What identifies a search: the same keyword with the same filters is the same search, so coming back to
    // the screen keeps its results. It is never sent to a source; each request builds its own (see requestFilter).
    private Filter buildFilter(String keyword) {
        Filter next = new Filter();
        next.addFilter(Filter.FILTER_KEYWORD, keyword);
        if (!statuses.isEmpty()) next.addFilter(Filter.FILTER_STATUS, TextUtils.join(",", SearchFilters.orderedStatuses(statuses)));
        if (!genre.isEmpty()) next.addFilter(Filter.FILTER_GENRE, genre);
        if (!chapterRanges.isEmpty()) next.addFilter("chapters", TextUtils.join(",", ChapterRanges.orderedIds(chapterRanges)));
        return next;
    }

    // What a source is asked: the keyword, the genre, and at most one status. Statuses it
    // filters itself, when there are several, go out as separate requests (see requestPage); the ones it
    // only reports are applied to what comes back.
    private Filter requestFilter(SourceState state, String status) {
        Filter request = new Filter();
        request.addFilter(Filter.FILTER_KEYWORD, filter.getKeyword());
        if (!genre.isEmpty()) request.addFilter(Filter.FILTER_GENRE, genre);
        if (status != null && !status.isEmpty()) request.addFilter(Filter.FILTER_STATUS, status);
        return request;
    }

    private void restart(String keyword) {
        filter = buildFilter(keyword);
        int current = ++generation;
        states.clear();
        arrival.clear();
        libraryResults = Collections.emptyList();
        cancelTimeout();

        List<String> skipped = new ArrayList<>();
        for (SourceState state : stateForSelectedSources(skipped)) states.put(state.source.sourceId, state);
        skippedSources.setValue(skipped);
        // the library has no status, genre or chapter range to filter by, so it only answers an unfiltered search
        if (statuses.isEmpty() && genre.isEmpty() && chapterRanges.isEmpty()) searchLibrary(keyword, current);
        for (SourceState state : states.values()) requestPage(state, 1, current);
        publish();
    }

    private List<SourceState> stateForSelectedSources(List<String> skippedNames) {
        List<SourceState> list = new ArrayList<>();
        Set<Integer> covered = effectiveSourceIds();
        for (DataSource dataSource : enabledSources()) {
            if (!covered.contains(dataSource.sourceId)) continue;
            // a source that can't apply every filter that is set is skipped (and named in the screen's note)
            SearchPlan plan = SearchPlan.forSource(supportOf(SourceManager.getSource(dataSource.sourceId)),
                    statuses, genre, chapterRanges);
            if (!plan.honored) {
                skippedNames.add(dataSource.name);
                continue;
            }
            list.add(new SourceState(dataSource, plan.filterStatusHere, plan.nativeStatuses,
                    plan.filterGenreHere, plan.filterChaptersHere));
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
        boolean added = false;
        for (SourceState state : wanted) {
            if (states.containsKey(state.source.sourceId)) continue;
            states.put(state.source.sourceId, state);
            requestPage(state, 1, generation);
            added = true;
        }
        // new sources get their own full wait before "No Results Found"
        if (added) cancelTimeout();
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
        Repository.Callback<List<Novel>> callback = new Repository.Callback<List<Novel>>() {
            @Override
            public void onComplete(List<Novel> result) {
                main.post(() -> onPage(searchGeneration, state, page, result));
            }

            @Override
            public void onError(Exception e) {
                // one broken source shouldn't hide the others' results
                main.post(() -> onPageFailed(searchGeneration, state));
            }
        };
        Repository repository = new Repository(state.source.sourceId);
        if (state.nativeStatuses.size() > 1) {
            // the source filters one status per request, and several are wanted: ask once for each
            repository.searchByStatuses(requestFilter(state, null), state.nativeStatuses, page, callback);
        } else {
            String status = state.nativeStatuses.isEmpty() ? null : state.nativeStatuses.get(0);
            repository.search(requestFilter(state, status), page, callback);
        }
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
        if (state.filterStatusHere) shown = SearchFilters.filterByStatuses(statuses, shown);
        if (state.filterGenreHere) shown = SearchFilters.filterByGenre(genre, shown);
        if (state.filterChaptersHere) shown = ChapterRanges.filter(chapterRanges, shown);

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

        // Waiting with nothing to show starts the clock for "No Results Found". Anything to show, or nothing
        // left to wait for (the screen then says so itself), stops it.
        if (!list.isEmpty() || !firstPagePending) cancelTimeout();
        else if (!timeoutArmed) armTimeout();
    }

    private void armTimeout() {
        timeoutArmed = true;
        int seconds = SearchPreferences.getTimeoutSeconds(App.getContext());
        main.postDelayed(timeoutTask, SearchTimeouts.millis(seconds));
    }

    private void cancelTimeout() {
        main.removeCallbacks(timeoutTask);
        timeoutArmed = false;
        if (Boolean.TRUE.equals(timedOut.getValue())) timedOut.setValue(false);
    }

    // The wait is over. If there is still nothing to show, say so; the sources stay pending and a result that
    // arrives later still appears (and clears the message).
    private void onTimeout() {
        timeoutArmed = false;
        List<Row> current = rows.getValue();
        if ((current == null || current.isEmpty()) && Boolean.TRUE.equals(loading.getValue())) {
            timedOut.setValue(true);
        }
    }

    @Override
    protected void onCleared() {
        main.removeCallbacks(timeoutTask);
    }
}
