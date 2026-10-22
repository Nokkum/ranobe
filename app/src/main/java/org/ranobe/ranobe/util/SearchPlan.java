package org.ranobe.ranobe.util;

import org.ranobe.ranobe.sources.SearchFilterSupport;

import java.util.Collections;
import java.util.List;
import java.util.Set;

// How one source takes part in a search with the filters that are set. Each filter is applied in one of three
// ways: the source does it itself, the app does it on the results (the source reports what is needed), or the
// source can't, and is left out so it doesn't return results the filters would have dropped.
public final class SearchPlan {
    // false: the source can't apply every filter that is set, so it is skipped
    public final boolean honored;
    // the app drops the results whose status isn't one of the chosen ones
    public final boolean filterStatusHere;
    // The source filters status itself, one status per request: the statuses to ask it about, in the standard
    // order, a request for each. Empty when it isn't asked to filter status.
    public final List<String> nativeStatuses;
    // the app drops the results that lack the genre
    public final boolean filterGenreHere;
    // the app drops the results outside the chosen chapter ranges
    public final boolean filterChaptersHere;

    private SearchPlan(boolean honored, boolean filterStatusHere, List<String> nativeStatuses,
                       boolean filterGenreHere, boolean filterChaptersHere) {
        this.honored = honored;
        this.filterStatusHere = filterStatusHere;
        this.nativeStatuses = nativeStatuses;
        this.filterGenreHere = filterGenreHere;
        this.filterChaptersHere = filterChaptersHere;
    }

    // support is null for a source that says nothing about filters. statuses and chapterRanges are what is
    // chosen (empty means any / all); genre is empty for any.
    public static SearchPlan forSource(SearchFilterSupport support, Set<String> statuses, String genre,
                                       Set<String> chapterRanges) {
        boolean anyStatus = !statuses.isEmpty();
        boolean anyGenre = genre != null && !genre.isEmpty();
        boolean anyChapters = !chapterRanges.isEmpty();

        boolean honored = true;
        for (String status : statuses) {
            if (support == null || !support.supportsStatus(status)) honored = false;
        }
        if (anyGenre && (support == null || !(support.supportsGenreFilter() || support.reportsGenresInResults()))) {
            honored = false;
        }
        if (anyChapters && (support == null || !support.reportsChapterCountInResults())) honored = false;
        if (!honored) {
            return new SearchPlan(false, false, Collections.<String>emptyList(), false, false);
        }

        boolean statusHere = anyStatus && support != null && !support.filtersStatusItself() && support.reportsStatusInResults();
        List<String> nativeStatuses = anyStatus && support != null && support.filtersStatusItself()
                ? SearchFilters.orderedStatuses(statuses) : Collections.<String>emptyList();
        boolean genreHere = anyGenre && support != null && !support.supportsGenreFilter() && support.reportsGenresInResults();
        return new SearchPlan(true, statusHere, nativeStatuses, genreHere, anyChapters);
    }
}
