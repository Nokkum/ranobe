package org.ranobe.ranobe.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.network.RateLimitedException;
import org.ranobe.ranobe.network.repository.Repository;
import org.ranobe.ranobe.sources.ChallengeRequiredException;
import org.ranobe.ranobe.sources.ChapterLockedException;
import org.ranobe.ranobe.sources.SignInRequiredException;
import org.ranobe.ranobe.ui.settings.WtrLabVerifyActivity;
import org.ranobe.ranobe.util.ChapterImages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    public static final String ACTION_DOWNLOAD_COMPLETE = "org.ranobe.ranobe.DOWNLOAD_COMPLETE";
    public static final String ACTION_DOWNLOAD_FAILED = "org.ranobe.ranobe.DOWNLOAD_FAILED";
    public static final String EXTRA_CHAPTER_URL = "chapter_url";

    public static final String CHANNEL_ID = "download_notification";
    private static final int NOTIFICATION_ID = 2001;
    private static final int SUMMARY_NOTIFICATION_ID = 2002;
    // When a server answers 429 we wait and retry, then slow the rest of the batch down. If it keeps
    // refusing, stop instead of failing every remaining chapter.
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final long RATE_LIMIT_FALLBACK_WAIT_MS = 20_000L;
    private static final long POLITE_GAP_MS = 500L;

    private static final Queue<DownloadItem> queue = new ConcurrentLinkedQueue<>();
    private static final Set<String> pendingUrls = Collections.synchronizedSet(new HashSet<>());
    private static boolean isProcessing = false;
    private static boolean resumeRequested = false;

    private ExecutorService executor;
    private NotificationManager notificationManager;
    private int completedCount = 0;
    private int lockedCount = 0;
    private int failedCount = 0;
    private int notAttemptedCount = 0;
    private boolean stoppedByRateLimit = false;
    private boolean stoppedBySignIn = false;
    private boolean stoppedByChallenge = false;
    private String challengeUrl = null;
    private DownloadItem challengeItem = null;
    private int pausedCount = 0;
    private boolean batchIsResume = false;
    private String lastError = null;

    public static void enqueue(Context context, Chapter chapter, int sourceId) {
        if (pendingUrls.contains(chapter.url)) return;
        pendingUrls.add(chapter.url);
        queue.add(new DownloadItem(chapter, sourceId));
        context.startService(new Intent(context, DownloadService.class));
    }

    // Queues many chapters with a single service start (a large "download all" used to start it per chapter).
    public static void enqueueAll(Context context, List<Chapter> chapters, int sourceId) {
        boolean added = false;
        for (Chapter chapter : chapters) {
            if (pendingUrls.add(chapter.url)) {
                queue.add(new DownloadItem(chapter, sourceId));
                added = true;
            }
        }
        if (added) context.startService(new Intent(context, DownloadService.class));
    }

    // Continues the chapters that stopped at a human check.
    public static int resumeAfterChallenge(Context context) {
        List<PausedDownloads.Entry> paused = PausedDownloads.take(context);
        int queued = 0;
        for (PausedDownloads.Entry entry : paused) {
            if (pendingUrls.add(entry.url)) {
                queue.add(new DownloadItem(entry.toChapter(), entry.sourceId));
                queued++;
            }
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(SUMMARY_NOTIFICATION_ID);
        if (queued > 0) {
            resumeRequested = true;
            context.startService(new Intent(context, DownloadService.class));
        }
        return queued;
    }

    // Drops the queued and paused chapters of one novel. Returns how many queued chapters were dropped.
    public static int cancelPending(Context context, String novelUrl) {
        int removed = 0;
        for (Iterator<DownloadItem> it = queue.iterator(); it.hasNext(); ) {
            DownloadItem item = it.next();
            if (novelUrl.equals(item.chapter.novelUrl)) {
                it.remove();
                pendingUrls.remove(item.chapter.url);
                removed++;
            }
        }
        PausedDownloads.dropNovel(context, novelUrl);
        return removed;
    }

    public static boolean isPending(String chapterUrl) {
        return pendingUrls.contains(chapterUrl);
    }

    public static int getPendingCount() {
        return pendingUrls.size();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Starting downloads…"));
        if (!isProcessing) {
            isProcessing = true;
            completedCount = 0;
            lockedCount = 0;
            failedCount = 0;
            notAttemptedCount = 0;
            stoppedByRateLimit = false;
            stoppedBySignIn = false;
            stoppedByChallenge = false;
            challengeUrl = null;
            challengeItem = null;
            pausedCount = 0;
            batchIsResume = resumeRequested;
            resumeRequested = false;
            lastError = null;
            processQueue(startId);
        }
        return START_NOT_STICKY;
    }

    private void processQueue(int startId) {
        executor.execute(() -> {
            long gapMs = 0;
            while (!queue.isEmpty()) {
                DownloadItem item = queue.poll();
                if (item == null) continue;

                int remaining = queue.size() + 1;
                updateNotification("Downloading: " + item.chapter.name, remaining);
                long sourceGapMs = 0;
                try {
                    sourceGapMs = new Repository(item.sourceId).requestGapMillis();
                } catch (Exception ignored) {
                    // no pause hint for this source
                }

                try {
                    Chapter result = downloadWithBackoff(item, remaining);
                    if (rateLimitedDuringItem) gapMs = POLITE_GAP_MS;
                    if (result != null && result.content != null && !result.content.isEmpty()) {
                        if (Ranobe.getShowImages()) {
                            result.content = ChapterImages.saveLocally(this, result.content);
                        }
                        RanobeDatabase.database().chapters().save(result);
                        completedCount++;
                        Intent broadcast = new Intent(ACTION_DOWNLOAD_COMPLETE);
                        broadcast.setPackage(getPackageName());
                        broadcast.putExtra(EXTRA_CHAPTER_URL, item.chapter.url);
                        sendBroadcast(broadcast);
                    } else {
                        failedCount++;
                        broadcastFailed(item.chapter.url);
                    }
                } catch (ChapterLockedException e) {
                    lockedCount++;
                    broadcastFailed(item.chapter.url);
                } catch (ChallengeRequiredException e) {
                    // Every remaining chapter would hit the same human check. Pause the batch; this
                    // chapter is not a failure, it is retried when the check has been passed.
                    stoppedByChallenge = true;
                    challengeUrl = item.chapter.url;
                    challengeItem = item;
                } catch (SignInRequiredException e) {
                    // Every remaining chapter would be refused the same way, so end the batch.
                    failedCount++;
                    stoppedBySignIn = true;
                    broadcastFailed(item.chapter.url);
                } catch (RateLimitedException e) {
                    failedCount++;
                    lastError = e.getMessage();
                    stoppedByRateLimit = true;
                    broadcastFailed(item.chapter.url);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failedCount++;
                    broadcastFailed(item.chapter.url);
                } catch (Exception e) {
                    failedCount++;
                    lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    broadcastFailed(item.chapter.url);
                } finally {
                    pendingUrls.remove(item.chapter.url);
                }

                if (stoppedByChallenge) {
                    pauseQueueForChallenge();
                    break;
                }
                if (stoppedByRateLimit || stoppedBySignIn || Thread.currentThread().isInterrupted()) {
                    abandonQueue();
                    break;
                }
                long waitMs = Math.max(gapMs, sourceGapMs);
                if (waitMs > 0 && !queue.isEmpty()) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        abandonQueue();
                        break;
                    }
                }
            }

            // A batch that ran to the end makes any older paused list stale.
            if (!stoppedByChallenge && !stoppedByRateLimit && !stoppedBySignIn) {
                PausedDownloads.clear(this);
            }
            isProcessing = false;
            stopForeground(true);
            showSummary();
            stopSelf(startId);
        });
    }

    private boolean rateLimitedDuringItem = false;

    // Downloads one chapter, waiting and retrying when the server says to slow down (HTTP 429).
    private Chapter downloadWithBackoff(DownloadItem item, int remaining) throws Exception {
        rateLimitedDuringItem = false;
        int hits = 0;
        while (true) {
            try {
                return new Repository(item.sourceId).chapterSync(item.chapter);
            } catch (RateLimitedException e) {
                rateLimitedDuringItem = true;
                if (++hits > MAX_RATE_LIMIT_RETRIES) throw e;
                updateNotification(getString(R.string.download_waiting_rate_limit), remaining);
                Thread.sleep(e.retryAfterMillis(RATE_LIMIT_FALLBACK_WAIT_MS * hits));
            }
        }
    }

    // Keeps the chapter that hit the check and everything still queued, to continue after the check.
    private void pauseQueueForChallenge() {
        List<PausedDownloads.Entry> paused = new ArrayList<>();
        if (challengeItem != null) {
            paused.add(PausedDownloads.Entry.of(challengeItem));
            broadcastFailed(challengeItem.chapter.url); // clears its spinner; it is queued again on resume
        }
        DownloadItem rest;
        while ((rest = queue.poll()) != null) {
            paused.add(PausedDownloads.Entry.of(rest));
            pendingUrls.remove(rest.chapter.url);
            broadcastFailed(rest.chapter.url);
        }
        pausedCount = paused.size();
        PausedDownloads.save(this, paused);
    }

    // Drops everything still queued so the batch ends cleanly instead of failing chapter by chapter.
    private void abandonQueue() {
        DownloadItem rest;
        while ((rest = queue.poll()) != null) {
            notAttemptedCount++;
            pendingUrls.remove(rest.chapter.url);
            broadcastFailed(rest.chapter.url);
        }
    }

    // Failures used to be silent, so a "download all" that skipped locked chapters looked broken.
    private void showSummary() {
        if (lockedCount + failedCount + notAttemptedCount == 0 && completedCount <= 1 && !stoppedByChallenge) return;
        String text = getString(R.string.download_summary, completedCount, lockedCount, failedCount);
        StringBuilder details = new StringBuilder(text);
        if (stoppedByChallenge) {
            int message = batchIsResume && completedCount == 0
                    ? R.string.download_challenge_again
                    : R.string.download_challenge_needed;
            details.append('\n').append(getString(message, pausedCount));
        }
        if (stoppedBySignIn) {
            details.append('\n').append(getString(R.string.download_sign_in_needed, notAttemptedCount));
        }
        if (stoppedByRateLimit) {
            details.append('\n').append(getString(R.string.download_rate_limited, notAttemptedCount));
        }
        if (lockedCount > 0) {
            details.append('\n').append(getString(R.string.download_locked_hint));
        }
        if (failedCount > 0 && lastError != null && !lastError.isEmpty()) {
            details.append('\n').append(getString(R.string.download_last_error, lastError));
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.download_summary_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(details.toString()))
                .setSmallIcon(R.drawable.ic_download)
                .setAutoCancel(true)
                .setSilent(true);
        if (stoppedByChallenge) {
            PendingIntent verify = PendingIntent.getActivity(
                    this,
                    0,
                    WtrLabVerifyActivity.intent(this, challengeUrl),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            builder.setContentIntent(verify).addAction(0, getString(R.string.wtr_lab_verify), verify);
        }
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, builder.build());
    }

    private void broadcastFailed(String url) {
        Intent broadcast = new Intent(ACTION_DOWNLOAD_FAILED);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(EXTRA_CHAPTER_URL, url);
        sendBroadcast(broadcast);
    }

    private void updateNotification(String text, int remaining) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text + " (" + remaining + " left)"));
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading chapters")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Chapter Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows progress while downloading chapters for offline reading");
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static class DownloadItem {
        final Chapter chapter;
        final int sourceId;

        DownloadItem(Chapter chapter, int sourceId) {
            this.chapter = chapter;
            this.sourceId = sourceId;
        }
    }
}
