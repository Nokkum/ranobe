package org.ranobe.ranobe.worker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.sources.Source;
import org.ranobe.ranobe.sources.SourceManager;
import org.ranobe.ranobe.ui.main.MainActivity;

import java.util.List;

public class ChapterUpdateWorker extends Worker {
    private static final int NOTIFICATION_ID_BASE = 20_000;

    public ChapterUpdateWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!Ranobe.isPro() || !Ranobe.isNewChapterUpdatesEnabled()) {
            return Result.success();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && getApplicationContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }

        RanobeDatabase database = RanobeDatabase.database();
        List<Novel> novels;
        try {
            novels = database.novels().listSync();
        } catch (Exception e) {
            return Result.retry();
        }

        for (Novel novel : novels) {
            checkNovel(database, novel);
        }

        return Result.success();
    }

    private void checkNovel(RanobeDatabase database, Novel novel) {
        if (novel == null || novel.url == null || novel.url.isEmpty()) {
            return;
        }

        try {
            Source source = SourceManager.getSource(novel.sourceId);
            List<Chapter> chapters = source.chapters(novel);
            if (chapters == null) {
                return;
            }

            int currentCount = chapters.size();
            int previousCount = novel.lastKnownChapterCount;

            database.novels().updateLastKnownChapterCount(currentCount, novel.url);

            if (previousCount > 0 && currentCount > previousCount) {
                notifyNewChapters(novel, currentCount - previousCount);
            }
        } catch (Exception ignored) {
            // A failed source should not prevent the remaining library from being checked.
        }
    }

    @SuppressLint("MissingPermission")
    private void notifyNewChapters(Novel novel, int newChapterCount) {
        Context context = getApplicationContext();
        String title = novel.name == null || novel.name.trim().isEmpty()
                ? "New chapters available"
                : novel.name;
        String message = newChapterCount == 1
                ? "1 new chapter is available"
                : newChapterCount + " new chapters are available";

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (int) (novel.id ^ (novel.id >>> 32)),
                new android.content.Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                context,
                Ranobe.NOTIF_CHAPTER_UPDATE_CHANNEL_ID
        )
                .setSmallIcon(R.drawable.ic_notifications_active)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_BASE + stableNotificationId(novel.id),
                notification.build()
        );
    }

    private int stableNotificationId(long id) {
        int value = (int) (id ^ (id >>> 32));
        return value & 0x7FFF;
    }
}
