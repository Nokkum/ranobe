package org.ranobe.ranobe.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

// Checks the library for new chapters at fixed times of day (see {@link UpdateSchedule}). Each run queues
// the next one when it finishes. Android may start a run a little late (for example while the phone is
// dozing); it never starts one early.
public final class ChapterUpdateScheduler {
    private static final String WORK_NAME = "chapter_update_slots";
    // the earlier schedule, a job that repeated every 12 hours
    private static final String LEGACY_WORK_NAME = "chapter_update_check";

    private ChapterUpdateScheduler() {
        throw new AssertionError("No instances");
    }

    // Queues a run for the next fixed time, replacing any run that is waiting. Used when updates are switched on.
    public static void schedule(@NonNull Context context) {
        enqueue(context, ExistingWorkPolicy.REPLACE);
    }

    // Makes sure a run is waiting, without disturbing one that already is. Called when the app starts, which
    // also moves a phone that still has the old every-12-hours job over to the new times.
    public static void ensureScheduled(@NonNull Context context) {
        enqueue(context, ExistingWorkPolicy.KEEP);
    }

    // Called by a run that is finishing: the next one goes behind it.
    static void scheduleNext(@NonNull Context context) {
        enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    public static void cancel(@NonNull Context context) {
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.cancelUniqueWork(WORK_NAME);
        manager.cancelUniqueWork(LEGACY_WORK_NAME);
    }

    private static void enqueue(@NonNull Context context, @NonNull ExistingWorkPolicy policy) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ChapterUpdateWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(UpdateSchedule.millisUntilNext(Calendar.getInstance()), TimeUnit.MILLISECONDS)
                .build();

        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        manager.cancelUniqueWork(LEGACY_WORK_NAME);
        manager.enqueueUniqueWork(WORK_NAME, policy, request);
    }
}
