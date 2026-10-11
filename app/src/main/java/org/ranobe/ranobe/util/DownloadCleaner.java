package org.ranobe.ranobe.util;

import android.content.Context;

import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.database.dao.ChapterDao;
import org.ranobe.ranobe.service.DownloadService;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

// Removes the downloaded chapters of one novel from this device.
public final class DownloadCleaner {
    private static final int BATCH_SIZE = 400;

    private DownloadCleaner() {
    }

    // Deletes every downloaded chapter of the novel, then the locally saved images that nothing else
    // uses any more. Reading progress and history are kept. Run it off the main thread.

    // @return how many chapters were removed
    public static int deleteNovelDownloads(Context context, String novelUrl) {
        DownloadService.cancelPending(context, novelUrl);

        ChapterDao chapters = RanobeDatabase.database().chapters();
        File imageDir = new File(context.getFilesDir(), Ranobe.CHAPTER_IMAGES_DIR);

        // Work out which saved images these chapters use before their text is gone.
        Set<String> candidates = new LinkedHashSet<>();
        for (String content : chapters.getContentsWithImages(novelUrl)) {
            candidates.addAll(imagePathsIn(content, imageDir.getAbsolutePath()));
        }

        int removed = chapters.deleteByNovel(novelUrl);
        deleteUnusedImages(chapters, candidates);
        return removed;
    }

    // Deletes the chosen downloaded chapters (by address), then the saved images nothing else uses. Reading
    // progress and history are kept. Run it off the main thread.

    // @return how many chapters were removed
    public static int deleteChapters(Context context, List<String> urls) {
        if (urls == null || urls.isEmpty()) return 0;
        ChapterDao chapters = RanobeDatabase.database().chapters();
        File imageDir = new File(context.getFilesDir(), Ranobe.CHAPTER_IMAGES_DIR);

        Set<String> candidates = new LinkedHashSet<>();
        int removed = 0;
        // SQLite takes a limited number of values in one query, so do a few hundred at a time
        for (List<String> batch : partition(urls, BATCH_SIZE)) {
            for (String content : chapters.getImageContentsOf(batch)) {
                candidates.addAll(imagePathsIn(content, imageDir.getAbsolutePath()));
            }
            removed += chapters.deleteByUrls(batch);
        }
        deleteUnusedImages(chapters, candidates);
        return removed;
    }

    private static void deleteUnusedImages(ChapterDao chapters, Set<String> candidates) {
        for (String path : candidates) {
            boolean stillUsed = chapters.countReferencing(path) > 0
                    || RanobeDatabase.database().readHistory().countReferencing(path) > 0;
            if (!stillUsed) {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            }
        }
    }

    // Consecutive slices of at most {@code size} items, in order.
    static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < items.size(); start += size) {
            batches.add(new ArrayList<>(items.subList(start, Math.min(start + size, items.size()))));
        }
        return batches;
    }

    // Paths of images in {@code content} that are stored under {@code imageDirPath}.
    static Set<String> imagePathsIn(String content, String imageDirPath) {
        Set<String> paths = new LinkedHashSet<>();
        if (content == null || content.isEmpty()) return paths;
        String prefix = imageDirPath.endsWith(File.separator) ? imageDirPath : imageDirPath + File.separator;
        Matcher matcher = SourceUtils.IMAGE_TAG.matcher(content);
        while (matcher.find()) {
            String path = matcher.group(1);
            // Only files inside the app's own image folder are ever deleted, never a web address.
            if (path.startsWith(prefix) && !path.substring(prefix.length()).contains("..")) paths.add(path);
        }
        return paths;
    }
}
