package org.ranobe.ranobe.ui.details.viewmodel;

import android.os.Parcel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.database.mapper.NovelMapper;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.NovelMetadata;
import org.ranobe.ranobe.network.repository.Repository;
import org.ranobe.ranobe.ui.chapters.viewmodel.ChaptersViewModel;

public class DetailsViewModel extends ViewModel {
    private static final long CACHE_EXPIRY_HOURS = 24;
    private MutableLiveData<String> error = new MutableLiveData<>();

    private static Novel copy(Novel novel) {
        Parcel parcel = Parcel.obtain();
        try {
            novel.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return Novel.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    public MutableLiveData<String> getError() {
        return error = new MutableLiveData<>();
    }

    // What a pull-to-refresh came back with.
    public static final class Refresh {
        // the refreshed details, or null when they couldn't be fetched (the page keeps what it shows)
        public final Novel novel;
        // chapters that weren't in the saved list; -1 when that can't be told
        public final int newChapters;
        // whether the chapter list itself was fetched and saved
        public final boolean chaptersRefreshed;
        // why the chapter list wasn't refreshed
        public final String error;

        Refresh(Novel novel, int newChapters, boolean chaptersRefreshed, String error) {
            this.novel = novel;
            this.newChapters = newChapters;
            this.chaptersRefreshed = chaptersRefreshed;
            this.error = error;
        }
    }

    // Fetches the details and the chapter list again, ignoring the 24 hour caches, and saves both. They run one
    // after the other: sources rate-limit, and the chapter list is the part that matters most.
    public LiveData<Refresh> refresh(Novel novel) {
        MutableLiveData<Refresh> result = new MutableLiveData<>();
        // sources fill in the novels they're given on a worker thread, so each call gets its own copy
        Novel forDetails = copy(novel);
        Novel forChapters = copy(novel);
        new Repository(novel.sourceId).details(forDetails, new Repository.Callback<Novel>() {
            @Override
            public void onComplete(Novel details) {
                refreshChapters(forChapters, saveDetails(novel, details), result);
            }

            @Override
            public void onError(Exception e) {
                // the chapters are still worth fetching
                refreshChapters(forChapters, null, result);
            }
        });
        return result;
    }

    private static void refreshChapters(Novel novel, Novel details, MutableLiveData<Refresh> result) {
        ChaptersViewModel.refreshChapters(novel, new Repository.Callback<Integer>() {
            @Override
            public void onComplete(Integer added) {
                result.postValue(new Refresh(details, added, true, null));
            }

            @Override
            public void onError(Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                result.postValue(new Refresh(details, -1, false, message));
            }
        });
    }

    // Saves fetched details as the cached copy. A block / error page parses into a nameless novel; that is
    // dropped (null) instead of replacing a good copy.
    private static Novel saveDetails(Novel requested, Novel details) {
        if (details == null || details.name == null || details.name.trim().isEmpty()) return null;
        NovelMetadata metadata = NovelMapper.ToNovelMetadata(details);
        metadata.cachedDate = System.currentTimeMillis();
        RanobeDatabase.database().runInTransaction(() -> {
            RanobeDatabase.database().novelMetadata().delete(requested.url);
            RanobeDatabase.database().novelMetadata().save(metadata);
        });
        return details;
    }

    public LiveData<Novel> getDetails(Novel novel) {
        MutableLiveData<Novel> details = new MutableLiveData<>();

        RanobeDatabase.databaseExecutor.execute(() -> {
            NovelMetadata cachedMetadata = RanobeDatabase.database()
                    .novelMetadata()
                    .get(novel.url);

            if (cachedMetadata == null || isCacheExpired(cachedMetadata.cachedDate)) {
                fetchFromNetwork(novel, details, error, cachedMetadata);
            } else {
                Novel cachedNovel = NovelMapper.ToNovel(cachedMetadata);
                details.postValue(cachedNovel);
            }
        });

        return details;
    }

    private boolean isCacheExpired(long cachedDate) {
        long expiryTime = cachedDate + (CACHE_EXPIRY_HOURS * 60 * 60 * 1000);
        return System.currentTimeMillis() > expiryTime;
    }

    private void fetchFromNetwork(Novel novel,
                                  MutableLiveData<Novel> details,
                                  MutableLiveData<String> error,
                                  NovelMetadata staleCache) {
        // sources fill in the novel they're given on a worker thread; don't hand them the
        // instance the details page is showing
        new Repository(novel.sourceId).details(copy(novel), new Repository.Callback<Novel>() {
            @Override
            public void onComplete(Novel result) {
                // a block / error page parses into a nameless novel; don't let it replace or pin a good copy
                if (result == null || result.name == null || result.name.trim().isEmpty()) {
                    if (staleCache != null) details.postValue(NovelMapper.ToNovel(staleCache));
                    else if (result != null) details.postValue(result);
                    else error.postValue("Couldn't load novel details");
                    return;
                }
                NovelMetadata newMetadata = NovelMapper.ToNovelMetadata(result);
                details.postValue(result);

                // Update cache
                newMetadata.cachedDate = System.currentTimeMillis();
                RanobeDatabase.database().runInTransaction(() -> {
                    if (staleCache != null) {
                        RanobeDatabase.database().novelMetadata().delete(novel.url);
                    }
                    RanobeDatabase.database().novelMetadata().save(newMetadata);
                });
            }

            @Override
            public void onError(Exception e) {
                // an expired copy is better than nothing when the source is unreachable
                if (staleCache != null) {
                    details.postValue(NovelMapper.ToNovel(staleCache));
                } else {
                    error.postValue(e.getLocalizedMessage());
                }
            }
        });
    }
}
