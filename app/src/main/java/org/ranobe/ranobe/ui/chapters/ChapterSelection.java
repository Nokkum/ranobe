package org.ranobe.ranobe.ui.chapters;

import org.ranobe.ranobe.models.Chapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The rules behind choosing downloaded chapters to delete. Sets are never changed, new ones are returned.
final class ChapterSelection {
    private ChapterSelection() {
    }

    // The chapters whose text is downloaded, in the same order as {@code all}.
    static List<Chapter> onlyDownloaded(List<Chapter> all, Set<String> downloadedUrls) {
        List<Chapter> kept = new ArrayList<>();
        for (Chapter chapter : all) {
            if (downloadedUrls.contains(chapter.url)) kept.add(chapter);
        }
        return kept;
    }

    static Set<String> toggled(Set<String> selected, String url) {
        Set<String> next = new HashSet<>(selected);
        if (!next.remove(url)) next.add(url);
        return next;
    }

    // True when there is something on screen and all of it is chosen.
    static boolean allSelected(List<Chapter> visible, Set<String> selected) {
        if (visible.isEmpty()) return false;
        for (Chapter chapter : visible) {
            if (!selected.contains(chapter.url)) return false;
        }
        return true;
    }

    // Chooses everything on screen, or, if it all is chosen already, clears it. Chapters off screen are left alone.
    static Set<String> toggleAll(List<Chapter> visible, Set<String> selected) {
        Set<String> next = new HashSet<>(selected);
        boolean everything = allSelected(visible, selected);
        for (Chapter chapter : visible) {
            if (everything) next.remove(chapter.url);
            else next.add(chapter.url);
        }
        return next;
    }

    // Drops anything no longer downloaded (just deleted, say) from the choice.
    static Set<String> keepDownloaded(Set<String> selected, Set<String> downloadedUrls) {
        Set<String> next = new HashSet<>(selected);
        next.retainAll(downloadedUrls);
        return next;
    }
}
