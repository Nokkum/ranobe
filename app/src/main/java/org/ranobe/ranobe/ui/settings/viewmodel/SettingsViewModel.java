package org.ranobe.ranobe.ui.settings.viewmodel;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.ranobe.ranobe.App;
import org.ranobe.ranobe.network.repository.GithubRepo;
import org.ranobe.ranobe.util.BackupManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsViewModel extends ViewModel {
    // file and database work for backups; one at a time, off the main thread
    private static final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();

    private MutableLiveData<GithubRepo.GithubRelease> update;
    private final MutableLiveData<Boolean> backupBusy = new MutableLiveData<>(false);
    // fires once per finished backup job; the screen clears it after showing the outcome
    private final MutableLiveData<BackupManager.Result> backupResult = new MutableLiveData<>();

    public MutableLiveData<GithubRepo.GithubRelease> getUpdate() {
        if (update == null) {
            update = new MutableLiveData<>();
        }
        return update;
    }

    public LiveData<Boolean> isBackupBusy() {
        return backupBusy;
    }

    public LiveData<BackupManager.Result> getBackupResult() {
        return backupResult;
    }

    public void clearBackupResult() {
        backupResult.setValue(null);
    }

    // Exports to, or imports from, the file the person picked. Only one job runs at a time.
    public void runBackup(BackupManager.Kind kind, Uri uri) {
        if (Boolean.TRUE.equals(backupBusy.getValue())) return;
        backupBusy.setValue(true);
        // the application's resolver: the screen may be gone before the job finishes
        ContentResolver resolver = App.getContext().getContentResolver();
        backupExecutor.execute(() -> {
            BackupManager.Result result = BackupManager.run(kind, resolver, uri);
            backupResult.postValue(result);
            backupBusy.postValue(false);
        });
    }

    public void checkForUpdate() {
        new GithubRepo().getLatestRelease(new GithubRepo.Callback<GithubRepo.GithubRelease>() {
            @Override
            public void onComplete(GithubRepo.GithubRelease result) {
                update.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
            }
        });
    }
}
