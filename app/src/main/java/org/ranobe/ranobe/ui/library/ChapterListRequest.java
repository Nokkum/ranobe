package org.ranobe.ranobe.ui.library;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.ui.chapters.viewmodel.ChaptersViewModel;

import java.util.Collections;
import java.util.List;

// Asks for a novel's chapter list the way the chapter sheet does (the saved list while it is fresh, otherwise
// the source) and passes on the first answer, the list or the error, exactly once. Unlike observing it from
// a screen, it is not cancelled when the screen goes away, so a download that was asked for still gets queued.
// Create it on the main thread; the callback runs there too.
final class ChapterListRequest {
    interface Callback {
        void onList(List<Chapter> chapters);

        void onFailed(@Nullable String message);
    }

    private final MutableLiveData<List<Chapter>> chapters;
    private final MutableLiveData<String> error;
    private final Callback callback;
    private final Observer<List<Chapter>> onChapters = this::gotChapters;
    private final Observer<String> onError = this::gotError;
    private boolean done = false;

    ChapterListRequest(ChaptersViewModel viewModel, Novel novel, Callback callback) {
        this.callback = callback;
        // getError() hands out the channel that the getChapters call right after it reports failures on
        this.error = viewModel.getError();
        this.chapters = viewModel.getChapters(novel);
        error.observeForever(onError);
        chapters.observeForever(onChapters);
    }

    private void gotChapters(List<Chapter> list) {
        if (finish()) callback.onList(list == null ? Collections.<Chapter>emptyList() : list);
    }

    private void gotError(String message) {
        if (finish()) callback.onFailed(message);
    }

    // true the first time only
    private boolean finish() {
        if (done) return false;
        done = true;
        chapters.removeObserver(onChapters);
        error.removeObserver(onError);
        return true;
    }
}
