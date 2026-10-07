package org.ranobe.ranobe.sources;

import java.io.IOException;

/**
 * A source refused to serve a chapter because it is locked (for example behind a paywall or an
 * unlock system), as opposed to a network failure or a missing sign-in.
 */
public class ChapterLockedException extends IOException {
    public static final String PREFIX = "Chapter locked";

    public ChapterLockedException(String detail) {
        super(detail == null || detail.isEmpty() ? PREFIX : PREFIX + ": " + detail);
    }

    // Lets code that only has an error message (such as the reader) recognise this error.
    public static boolean isLocked(String message) {
        return message != null && message.startsWith(PREFIX);
    }
}
