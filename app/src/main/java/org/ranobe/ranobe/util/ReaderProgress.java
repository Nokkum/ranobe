package org.ranobe.ranobe.util;

// How far through a chapter the reader is, and where a progress value points to. A chapter is a list
// of rows: the heading is row 0, then one row per paragraph or image.
public final class ReaderProgress {
    private ReaderProgress() {
    }

    // 0..100. rowFraction is how much of the top visible row is already scrolled off (0..1);
    // endVisible means the chapter's last row is fully on screen, which counts as finished.
    public static int percent(int rowIndex, float rowFraction, int rowCount, boolean endVisible) {
        if (rowCount <= 1) return 0;
        if (endVisible) return 100;
        float fraction = (rowIndex + clamp(rowFraction)) / (rowCount - 1);
        return Math.round(clamp(fraction) * 100f);
    }

    // The row to put at the top of the screen for a progress value of 0..100.
    public static int rowFor(float percent, int rowCount) {
        if (rowCount <= 1) return 0;
        float fraction = clamp(percent / 100f);
        return Math.round(fraction * (rowCount - 1));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
