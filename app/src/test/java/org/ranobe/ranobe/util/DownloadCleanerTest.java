package org.ranobe.ranobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.Set;

public class DownloadCleanerTest {
    private static final String DIR = File.separator + "data" + File.separator + "files" + File.separator + "chapter-images";

    private static String path(String name) {
        return DIR + File.separator + name;
    }

    @Test
    public void findsOnlyImagesStoredInTheAppImageFolder() {
        String content = "Intro [img]" + path("111") + "[/img] text [img]https://example.net/a.png[/img]"
                + " more [img]" + path("222") + "[/img] and [img]" + File.separator + "data" + File.separator + "other" + File.separator + "x[/img]";

        Set<String> found = DownloadCleaner.imagePathsIn(content, DIR);

        assertEquals(2, found.size());
        assertTrue(found.contains(path("111")));
        assertTrue(found.contains(path("222")));
    }

    @Test
    public void repeatedImagesAreListedOnce() {
        String content = "[img]" + path("111") + "[/img][img]" + path("111") + "[/img]";

        assertEquals(1, DownloadCleaner.imagePathsIn(content, DIR).size());
    }

    @Test
    public void pathsThatEscapeTheFolderAreIgnored() {
        String content = "[img]" + DIR + File.separator + ".." + File.separator + "databases" + File.separator + "ranobe.db[/img]";

        assertTrue(DownloadCleaner.imagePathsIn(content, DIR).isEmpty());
    }

    @Test
    public void emptyOrMissingContentHasNoImages() {
        assertTrue(DownloadCleaner.imagePathsIn(null, DIR).isEmpty());
        assertTrue(DownloadCleaner.imagePathsIn("", DIR).isEmpty());
        assertTrue(DownloadCleaner.imagePathsIn("plain text", DIR).isEmpty());
    }
}
