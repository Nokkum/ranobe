package org.ranobe.ranobe.sources;

// Optional. What a source can do with the search filters. A source that does not implement this is
// skipped while a filter is set, because it could not honour it.
public interface SearchFilterSupport {
    // search(Filter, page) applies Filter.FILTER_STATUS itself.
    boolean filtersStatusItself();

    // Search results carry Novel.status, so the app can drop the novels that do not match.
    boolean reportsStatusInResults();

    // search(Filter, page) applies Filter.FILTER_GENRE.
    boolean supportsGenreFilter();

    // Search results carry Novel.genres, so the app can drop the novels that do not have the genre.
    default boolean reportsGenresInResults() {
        return false;
    }

    // Whether this particular status can be filtered. Most sources handle all of them or none; a source
    // that can only tell some apart (say, completed from the rest) overrides this.
    default boolean supportsStatus(String status) {
        return filtersStatusItself() || reportsStatusInResults();
    }
}
