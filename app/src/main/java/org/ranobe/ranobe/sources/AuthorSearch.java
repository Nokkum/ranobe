package org.ranobe.ranobe.sources;

import org.ranobe.ranobe.models.Novel;

import java.util.List;

// Optional. A source that can list the novels one author wrote. A source that doesn't implement this has
// no author page: the app says so instead of guessing from a text search that would miss most of them.
public interface AuthorSearch {
    // One page of the author's novels, pages counted from 1; an empty list once there are no more. The
    // author is a name as the source itself lists it (Novel.authors).
    List<Novel> authorWorks(String author, int page) throws Exception;
}
