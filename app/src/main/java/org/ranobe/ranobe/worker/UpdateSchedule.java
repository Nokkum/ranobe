package org.ranobe.ranobe.worker;

import java.util.Calendar;

// The times of day, in the device's local time, when the app checks the library for new chapters.
final class UpdateSchedule {
    // Minutes after midnight, in ascending order: 00:30, 08:00, 10:30, 12:30, 20:00 and 22:30.
    static final int[] RUN_MINUTES = {30, 8 * 60, 10 * 60 + 30, 12 * 60 + 30, 20 * 60, 22 * 60 + 30};

    private UpdateSchedule() {
    }

    // The first run time strictly after {@code now}, today or tomorrow.
    static Calendar nextRun(Calendar now) {
        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            for (int minutes : RUN_MINUTES) {
                Calendar candidate = (Calendar) now.clone();
                candidate.set(Calendar.HOUR_OF_DAY, 0);
                candidate.set(Calendar.MINUTE, 0);
                candidate.set(Calendar.SECOND, 0);
                candidate.set(Calendar.MILLISECOND, 0);
                candidate.add(Calendar.DAY_OF_YEAR, dayOffset);
                // wall-clock time, so a change of clocks does not move a run to another hour
                candidate.set(Calendar.HOUR_OF_DAY, minutes / 60);
                candidate.set(Calendar.MINUTE, minutes % 60);
                if (candidate.getTimeInMillis() > now.getTimeInMillis()) return candidate;
            }
        }
        // unreachable: tomorrow's first run is always later than now
        throw new IllegalStateException("no run time found after " + now.getTime());
    }

    // How long to wait from {@code now} for the next run. Always positive.
    static long millisUntilNext(Calendar now) {
        return nextRun(now).getTimeInMillis() - now.getTimeInMillis();
    }
}
