package org.ranobe.ranobe.util;

import android.content.Context;

import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.database.dao.ChapterDao;
import org.ranobe.ranobe.service.DownloadService;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;

// Removes the downloaded chapters of one novel from the device.
public final class DownloadCleaner {
    private DownloadCleaner() {
    }

    // Deletes every downloaded chapter of the novel. Reading progress and history are kept.
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

        for (String path : candidates) {
            boolean stillUsed = chapters.countReferencing(path) > 0
                    || RanobeDatabase.database().readHistory().countReferencing(path) > 0;
            if (!stillUsed) {
                //noinspection ResultOfMethodCallIgnored
                new File(path).delete();
            }
        }
        return removed;
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
