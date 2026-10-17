package org.ranobe.ranobe.util;

import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// What an import changes. Importing only ever adds or updates: nothing already on the device is deleted,
// so importing the same file twice, or an old file over newer data, can't lose anything.
public final class BackupMerge {
    private BackupMerge() {
    }

    public static final class LibraryPlan {
        public final List<Novel> toSave;
        // in the file but already in the library (or listed twice in the file); the saved copy is kept
        public final int alreadySaved;

        LibraryPlan(List<Novel> toSave, int alreadySaved) {
            this.toSave = toSave;
            this.alreadySaved = alreadySaved;
        }
    }

    public static final class HistoryPlan {
        public final List<ReadHistory> toSave;
        public final int added;
        public final int updated;
        // already on the device with the same or a later time, so the file's copy is ignored
        public final int kept;

        HistoryPlan(List<ReadHistory> toSave, int added, int updated, int kept) {
            this.toSave = toSave;
            this.added = added;
            this.updated = updated;
            this.kept = kept;
        }
    }

    // Novels not in the library yet. A novel already there keeps its saved details.
    public static LibraryPlan planLibrary(Set<String> existingUrls, List<Novel> incoming) {
        Set<String> seen = new HashSet<>(existingUrls);
        List<Novel> toSave = new ArrayList<>();
        int alreadySaved = 0;
        for (Novel novel : incoming) {
            if (seen.add(novel.url)) {
                toSave.add(novel);
            } else {
                alreadySaved++;
            }
        }
        return new LibraryPlan(toSave, alreadySaved);
    }

    // Chapters read per the file. For a chapter on both sides, the one read last wins, so where you stopped
    // follows your most recent reading wherever it happened. existing maps chapter url -> its timestamp.
    public static HistoryPlan planHistory(Map<String, Long> existing, List<ReadHistory> incoming) {
        // a chapter listed twice in the file counts once, as its latest entry
        Map<String, ReadHistory> latest = new LinkedHashMap<>();
        for (ReadHistory entry : incoming) {
            ReadHistory known = latest.get(entry.url);
            if (known == null || entry.timestamp > known.timestamp) latest.put(entry.url, entry);
        }

        List<ReadHistory> toSave = new ArrayList<>();
        int added = 0;
        int updated = 0;
        int kept = 0;
        for (ReadHistory entry : latest.values()) {
            Long onDevice = existing.get(entry.url);
            if (onDevice == null) {
                toSave.add(entry);
                added++;
            } else if (entry.timestamp > onDevice) {
                toSave.add(entry);
                updated++;
            } else {
                kept++;
            }
        }
        return new HistoryPlan(toSave, added, updated, kept);
    }
}
