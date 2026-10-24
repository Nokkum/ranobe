package org.ranobe.ranobe.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.databinding.SheetNovelOptionsBinding;
import org.ranobe.ranobe.models.Novel;

// The sheet that opens from the three dots on a novel in the library. It only reports what was chosen; the
// fragment that showed it (as a child fragment) does the work.
public class NovelOptions extends BottomSheetDialogFragment {
    public static final String TAG = "NovelOptions";

    public interface Listener {
        void onDownloadAll(Novel novel);

        void onDeleteAllChapters(Novel novel);

        void onRemoveBookmark(Novel novel);
    }

    public static NovelOptions newInstance(Novel novel) {
        NovelOptions sheet = new NovelOptions();
        Bundle args = new Bundle();
        args.putParcelable(Ranobe.KEY_NOVEL, novel);
        sheet.setArguments(args);
        return sheet;
    }

    public NovelOptions() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return SheetNovelOptionsBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Novel novel = getArguments() == null ? null : getArguments().getParcelable(Ranobe.KEY_NOVEL);
        if (novel == null) {
            dismissAllowingStateLoss();
            return;
        }
        SheetNovelOptionsBinding binding = SheetNovelOptionsBinding.bind(view);
        binding.novelTitle.setText(novel.name);

        binding.optionDownloadAll.setOnClickListener(v -> choose(listener -> listener.onDownloadAll(novel)));
        binding.optionDeleteChapters.setOnClickListener(v -> choose(listener -> listener.onDeleteAllChapters(novel)));
        binding.optionRemoveBookmark.setOnClickListener(v -> choose(listener -> listener.onRemoveBookmark(novel)));
    }

    private interface Choice {
        void run(Listener listener);
    }

    private void choose(Choice choice) {
        Listener listener = getParentFragment() instanceof Listener ? (Listener) getParentFragment() : null;
        dismissAllowingStateLoss();
        if (listener != null) choice.run(listener);
    }

    // the sheet is short; open it fully rather than at the default peek height, which would hide the last row
    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
