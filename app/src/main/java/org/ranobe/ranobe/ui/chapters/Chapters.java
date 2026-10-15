package org.ranobe.ranobe.ui.chapters;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.databinding.FragmentChaptersBinding;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;
import org.ranobe.ranobe.service.DownloadService;
import org.ranobe.ranobe.ui.chapters.adapter.ChapterAdapter;
import org.ranobe.ranobe.ui.chapters.viewmodel.ChaptersViewModel;
import org.ranobe.ranobe.ui.error.Error;
import org.ranobe.ranobe.ui.history.viewmodel.HistoryViewModel;
import org.ranobe.ranobe.ui.reader.ReaderActivity;
import org.ranobe.ranobe.ui.views.RecyclerSwipeHelper;
import org.ranobe.ranobe.util.DownloadCleaner;
import org.ranobe.ranobe.util.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Chapters extends BottomSheetDialogFragment implements ChapterAdapter.OnChapterItemClickListener, ChapterAdapter.OnChapterDownloadClickListener, ChapterAdapter.OnChapterSelectionListener, Toolbar.OnMenuItemClickListener {
    private final List<Chapter> originalItems = new ArrayList<>();
    private final List<ReadHistory> readHistoryList = new ArrayList<>();
    private final Set<String> downloadedUrls = new HashSet<>();
    private FragmentChaptersBinding binding;
    private ChaptersViewModel viewModel;
    private HistoryViewModel historyViewModel;
    private Novel novel;
    private ChapterAdapter adapter;
    // choosing downloaded chapters to delete: the list then shows only the downloaded ones
    private boolean selectionMode = false;
    private Set<String> selectedUrls = new HashSet<>();
    private final List<Chapter> selectionItems = new ArrayList<>();
    private ChapterAdapter selectionAdapter;
    private OnBackPressedCallback selectionBackCallback;
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String url = intent.getStringExtra(DownloadService.EXTRA_CHAPTER_URL);
            if (url != null && DownloadService.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                downloadedUrls.add(url);
                adapter.setDownloadedUrls(new HashSet<>(downloadedUrls));
                updateDeleteDownloadsVisibility();
                refreshSelectionList();
            } else if (url != null) {
                // failed — just refresh to remove the pending spinner
                adapter.notifyDataSetChanged();
            }
        }
    };
    private boolean scrolledToLastRead = false;
    // Pending chapter/action to start after permission is granted
    private Chapter pendingDownloadChapter = null;
    private boolean pendingDownloadAll = false;
    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    if (pendingDownloadAll) {
                        doDownloadAll();
                    } else if (pendingDownloadChapter != null) {
                        doEnqueue(pendingDownloadChapter);
                    }
                }
                pendingDownloadChapter = null;
                pendingDownloadAll = false;
            });

    public Chapters() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            novel = getArguments().getParcelable(Ranobe.KEY_NOVEL);
        }
        viewModel = new ViewModelProvider(requireActivity()).get(ChaptersViewModel.class);
        historyViewModel = new ViewModelProvider(requireActivity()).get(HistoryViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentChaptersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setUpUi();
        setUpObservers();
        loadDownloadedUrls();
        registerDownloadReceiver();
        setUpSelectionBackHandling();
    }

    // open as a tall sheet straight away; a half-height list of chapters is hard to scan
    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.9f);
            sheet.requestLayout();
        }
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireContext().unregisterReceiver(downloadReceiver);
        binding = null;
    }

    private void registerDownloadReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_FAILED);
        ContextCompat.registerReceiver(requireContext(), downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void loadDownloadedUrls() {
        RanobeDatabase.databaseExecutor.execute(() -> {
            List<String> urls = RanobeDatabase.database().chapters().getDownloadedUrls(novel.url);
            downloadedUrls.addAll(urls);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                adapter.setDownloadedUrls(new HashSet<>(downloadedUrls));
                updateDeleteDownloadsVisibility();
            });
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setUpObservers() {
        viewModel.getError().observe(getViewLifecycleOwner(), this::setUpError);
        viewModel.getChapters(novel).observe(getViewLifecycleOwner(), this::setChapter);
        historyViewModel.getReadHistoriesByNovel(novel.url).observe(getViewLifecycleOwner(), readHistories -> {
            readHistoryList.clear();
            readHistoryList.addAll(readHistories);
            adapter.setHistory(readHistoryList);
            RecyclerView.Adapter<?> current = binding.chapterList.getAdapter();
            if (current instanceof ChapterAdapter && current != adapter) {
                ((ChapterAdapter) current).setHistory(readHistoryList);
            }
            updateSubtitle();
            scrollToLastReadOnce();
        });
    }

    private void setUpUi() {
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        binding.searchField.addTextChangedListener(new SearchBarTextWatcher());

        adapter = new ChapterAdapter(originalItems, readHistoryList, this);
        adapter.setDownloadListener(this);
        adapter.setSelectionListener(this);
        binding.toolbar.setNavigationOnClickListener(v -> exitSelectionMode());
        binding.chapterList.setLayoutManager(new LinearLayoutManager(requireActivity()));
        binding.chapterList.setAdapter(adapter);

        RecyclerSwipeHelper swipeHelper = new RecyclerSwipeHelper(
                getResources().getColor(R.color.success),
                getResources().getColor(R.color.danger),
                R.drawable.ic_mark_read,
                R.drawable.ic_mark_unread,
                requireContext()
        ) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                // while searching the list shows a filtered adapter, so read the chapter from it
                ChapterAdapter current = (ChapterAdapter) binding.chapterList.getAdapter();
                if (current == null || position == RecyclerView.NO_POSITION) return;
                Chapter chapter = current.getItem(position);
                current.notifyItemChanged(position);
                if (direction == ItemTouchHelper.LEFT) {
                    historyViewModel.markAsUnread(chapter);
                } else {
                    historyViewModel.markAsRead(chapter);
                }
            }
        };
        ItemTouchHelper touchHelper = new ItemTouchHelper(swipeHelper);
        touchHelper.attachToRecyclerView(binding.chapterList);
    }

    private void setUpError(String error) {
        binding.progress.hide();
        if (originalItems.size() == 0) {
            if (requireActivity() instanceof OnChapterPicked) {
                // the reader has no error screen to navigate to, and reports a failed list itself
                dismiss();
            } else {
                Error.navigateToErrorFragment(requireActivity(), error);
            }
        }
    }

    private void searchResults(String keyword) {
        if (!keyword.isEmpty()) {
            // while choosing, only the downloaded chapters are searched
            List<Chapter> base = selectionMode ? selectionItems : originalItems;
            List<Chapter> filtered = ListUtils.searchByName(keyword.toLowerCase(), base);
            // same read state and downloads as the full list
            ChapterAdapter searchAdapter = new ChapterAdapter(filtered, readHistoryList, this);
            searchAdapter.setDownloadListener(this);
            searchAdapter.setSelectionListener(this);
            searchAdapter.setDownloadedUrls(new HashSet<>(downloadedUrls));
            searchAdapter.setSelectionMode(selectionMode, selectedUrls);
            binding.chapterList.setAdapter(searchAdapter);
        } else if (selectionMode && selectionAdapter != null) {
            selectionAdapter.setSelected(selectedUrls);
            binding.chapterList.setAdapter(selectionAdapter);
        } else {
            binding.chapterList.setAdapter(adapter);
        }
    }

    private void setSearchView() {
        boolean show = binding.searchView.getVisibility() != View.VISIBLE;
        binding.searchView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.searchField.requestFocus();
        } else {
            // closing search returns to the full list
            binding.searchField.setText(null);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void setChapter(List<Chapter> chapters) {
        originalItems.clear();
        originalItems.addAll(chapters);
        adapter.notifyDataSetChanged();
        updateToolbarForMode();
        refreshSelectionList();
        binding.progress.hide();
        binding.progress.setVisibility(View.GONE);
        scrollToLastReadOnce();

        if (!chapters.isEmpty() && novel.url != null) {
            RanobeDatabase.databaseExecutor.execute(() -> RanobeDatabase.database().novels().updateLastKnownChapterCount(chapters.size(), novel.url));
        }
    }

    private void updateSubtitle() {
        if (selectionMode || originalItems.isEmpty()) return;
        binding.toolbar.setSubtitle(getString(R.string.chapters_summary, originalItems.size(), adapter.readCount()));
    }

    // jump to where the reader stopped, once both chapters and history have arrived
    private void scrollToLastReadOnce() {
        if (scrolledToLastRead || originalItems.isEmpty() || adapter.getLastReadUrl() == null)
            return;
        for (int i = 0; i < originalItems.size(); i++) {
            if (originalItems.get(i).url.equals(adapter.getLastReadUrl())) {
                scrolledToLastRead = true;
                LinearLayoutManager manager = (LinearLayoutManager) binding.chapterList.getLayoutManager();
                if (manager != null)
                    manager.scrollToPositionWithOffset(i, binding.chapterList.getHeight() / 3);
                return;
            }
        }
    }

    private void sort() {
        Collections.reverse(originalItems);
        adapter.notifyItemRangeChanged(0, originalItems.size());
    }

    @Override
    public void onChapterItemClick(Chapter item) {
        // opened from the reader: it is already on screen, so it moves to the chapter instead of stacking another reader
        FragmentActivity host = requireActivity();
        if (host instanceof OnChapterPicked) {
            dismiss();
            ((OnChapterPicked) host).onChapterPicked(item);
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putParcelable(Ranobe.KEY_NOVEL, novel);
        bundle.putParcelable(Ranobe.KEY_CHAPTER, item);

        requireActivity().startActivity(new Intent(requireActivity(), ReaderActivity.class).putExtras(bundle));
    }

    @Override
    public void onDownloadClick(Chapter chapter) {
        if (hasNotifPermission()) {
            doEnqueue(chapter);
        } else {
            pendingDownloadChapter = chapter;
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void doEnqueue(Chapter chapter) {
        DownloadService.enqueue(requireContext(), chapter, novel.sourceId);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.sort) {
            sort();
        } else if (id == R.id.search) {
            setSearchView();
        } else if (id == R.id.download_all) {
            downloadAll();
        } else if (id == R.id.delete_downloads) {
            confirmDeleteDownloads();
        } else if (id == R.id.select_downloads) {
            enterSelectionMode(null);
        } else if (id == R.id.select_all) {
            toggleSelectAll();
        } else if (id == R.id.delete_selected) {
            confirmDeleteSelected();
        }
        return true;
    }

    // Kept under its old name: the delete and select buttons only exist while there are downloaded chapters.
    private void updateDeleteDownloadsVisibility() {
        updateToolbarForMode();
    }

    // choosing chapters to delete

    private void setUpSelectionBackHandling() {
        selectionBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                exitSelectionMode();
            }
        };
        // apps targeting Android 16 no longer get onBackPressed(), so use the dialog's back dispatcher
        if (requireDialog() instanceof ComponentDialog) {
            ((ComponentDialog) requireDialog()).getOnBackPressedDispatcher()
                    .addCallback(getViewLifecycleOwner(), selectionBackCallback);
        }
    }

    // Starts choosing. Only possible while something is downloaded; {@code first} is chosen at once.
    @SuppressLint("NotifyDataSetChanged")
    private void enterSelectionMode(@Nullable Chapter first) {
        if (selectionMode || downloadedUrls.isEmpty()) return;
        selectionMode = true;
        selectedUrls = new HashSet<>();
        if (first != null && downloadedUrls.contains(first.url)) selectedUrls.add(first.url);

        refreshSelectionItems();
        selectionAdapter = new ChapterAdapter(selectionItems, readHistoryList, this);
        selectionAdapter.setDownloadListener(this);
        selectionAdapter.setSelectionListener(this);
        selectionAdapter.setDownloadedUrls(new HashSet<>(downloadedUrls));
        selectionAdapter.setSelectionMode(true, selectedUrls);
        searchResults(currentKeyword());
        updateToolbarForMode();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void exitSelectionMode() {
        if (!selectionMode || binding == null) return;
        selectionMode = false;
        selectedUrls = new HashSet<>();
        selectionAdapter = null;
        selectionItems.clear();
        adapter.setDownloadedUrls(new HashSet<>(downloadedUrls));
        adapter.notifyDataSetChanged();
        searchResults(currentKeyword());
        updateToolbarForMode();
    }

    private String currentKeyword() {
        CharSequence text = binding.searchField.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void refreshSelectionItems() {
        selectionItems.clear();
        selectionItems.addAll(ChapterSelection.onlyDownloaded(originalItems, downloadedUrls));
    }

    // The downloaded set or the chapter list changed while choosing: rebuild what is shown.
    @SuppressLint("NotifyDataSetChanged")
    private void refreshSelectionList() {
        if (!selectionMode || selectionAdapter == null || binding == null) return;
        refreshSelectionItems();
        selectedUrls = ChapterSelection.keepDownloaded(selectedUrls, downloadedUrls);
        selectionAdapter.setSelected(selectedUrls);
        selectionAdapter.notifyDataSetChanged();
        searchResults(currentKeyword());
        updateToolbarForMode();
    }

    @Nullable
    private ChapterAdapter currentAdapter() {
        RecyclerView.Adapter<?> current = binding.chapterList.getAdapter();
        return current instanceof ChapterAdapter ? (ChapterAdapter) current : null;
    }

    // Shows the new choice on the rows and in the toolbar.
    private void onSelectionChanged() {
        ChapterAdapter current = currentAdapter();
        if (current != null) current.setSelected(selectedUrls);
        if (selectionAdapter != null && selectionAdapter != current) selectionAdapter.setSelected(selectedUrls);
        updateToolbarForMode();
    }

    @Override
    public void onToggleSelection(Chapter chapter) {
        if (!selectionMode) return;
        selectedUrls = ChapterSelection.toggled(selectedUrls, chapter.url);
        onSelectionChanged();
    }

    @Override
    public boolean onChapterLongClick(Chapter chapter) {
        // a long press on a downloaded chapter starts choosing, with that chapter chosen
        if (selectionMode || !downloadedUrls.contains(chapter.url)) return false;
        enterSelectionMode(chapter);
        return true;
    }

    // Chooses everything on screen, or clears it if it all is chosen already.
    private void toggleSelectAll() {
        ChapterAdapter current = currentAdapter();
        List<Chapter> visible = current == null ? selectionItems : current.getItems();
        selectedUrls = ChapterSelection.toggleAll(visible, selectedUrls);
        onSelectionChanged();
    }

    private void confirmDeleteSelected() {
        if (selectedUrls.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.select_chapters_first, Snackbar.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_selected_chapters)
                .setMessage(getString(R.string.delete_chapters_confirmation, selectedUrls.size()))
                .setPositiveButton(R.string.delete_downloads_confirm, (dialog, i) -> deleteSelected())
                .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.dismiss())
                .show();
    }

    private void deleteSelected() {
        final Context appContext = requireContext().getApplicationContext();
        final List<String> urls = new ArrayList<>(selectedUrls);
        RanobeDatabase.databaseExecutor.execute(() -> {
            int removed = DownloadCleaner.deleteChapters(appContext, urls);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> onChaptersDeleted(urls, removed));
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onChaptersDeleted(List<String> urls, int removed) {
        if (!isAdded() || binding == null) return;
        downloadedUrls.removeAll(urls);
        adapter.setDownloadedUrls(new HashSet<>(downloadedUrls));
        adapter.notifyDataSetChanged();
        selectedUrls = ChapterSelection.keepDownloaded(selectedUrls, downloadedUrls);
        // nothing downloaded is left to choose from, so leave choosing
        if (downloadedUrls.isEmpty()) exitSelectionMode();
        else refreshSelectionList();
        updateToolbarForMode();
        Snackbar.make(binding.getRoot(), getString(R.string.chapters_deleted, removed), Snackbar.LENGTH_SHORT).show();
    }

    // The toolbar's buttons, title and back arrow follow the mode: normal, or choosing chapters.
    private void updateToolbarForMode() {
        if (binding == null) return;
        Menu menu = binding.toolbar.getMenu();
        boolean anyDownloaded = !downloadedUrls.isEmpty();
        setMenuVisible(menu, R.id.search, !selectionMode);
        setMenuVisible(menu, R.id.sort, !selectionMode);
        setMenuVisible(menu, R.id.download_all, !selectionMode);
        setMenuVisible(menu, R.id.delete_downloads, !selectionMode && anyDownloaded);
        setMenuVisible(menu, R.id.select_downloads, !selectionMode && anyDownloaded);
        setMenuVisible(menu, R.id.select_all, selectionMode);
        setMenuVisible(menu, R.id.delete_selected, selectionMode);

        if (selectionMode) {
            binding.toolbar.setTitle(getString(R.string.chapters_selected, selectedUrls.size()));
            binding.toolbar.setSubtitle(null);
            binding.toolbar.setNavigationIcon(R.drawable.ic_close);
            binding.toolbar.setNavigationIconTint(MaterialColors.getColor(
                    binding.toolbar, com.google.android.material.R.attr.colorOnSurface));
            binding.toolbar.setNavigationContentDescription(R.string.close_selection);
        } else {
            binding.toolbar.setTitle(novel.name);
            binding.toolbar.setNavigationIcon(null);
            updateSubtitle();
        }
        if (selectionBackCallback != null) selectionBackCallback.setEnabled(selectionMode);
    }

    private static void setMenuVisible(Menu menu, int id, boolean visible) {
        MenuItem item = menu.findItem(id);
        if (item != null) item.setVisible(visible);
    }

    private void confirmDeleteDownloads() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_downloads)
                .setMessage(getString(R.string.delete_downloads_confirmation, downloadedUrls.size()))
                .setPositiveButton(R.string.delete_downloads_confirm, (dialog, i) -> deleteDownloads())
                .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.dismiss())
                .show();
    }

    private void deleteDownloads() {
        final Context appContext = requireContext().getApplicationContext();
        final String novelUrl = novel.url;
        RanobeDatabase.databaseExecutor.execute(() -> {
            int removed = DownloadCleaner.deleteNovelDownloads(appContext, novelUrl);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> onDownloadsDeleted(removed));
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onDownloadsDeleted(int removed) {
        if (!isAdded() || binding == null) return;
        downloadedUrls.clear();
        adapter.setDownloadedUrls(new HashSet<>());
        adapter.notifyDataSetChanged();
        updateDeleteDownloadsVisibility();
        Snackbar.make(binding.getRoot(), getString(R.string.downloads_deleted, removed), Snackbar.LENGTH_SHORT).show();
    }

    private void downloadAll() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.download_all)
                .setMessage(R.string.downloading_all_chapters_confirmation)
                .setPositiveButton(android.R.string.ok, (dialog, i) -> {
                    if (hasNotifPermission()) {
                        doDownloadAll();
                    } else {
                        pendingDownloadAll = true;
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.dismiss())
                .show();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void doDownloadAll() {
        // Oldest first, whatever order the list is shown in: sources such as WTR-LAB unlock the first
        // chapters for everyone, so those should be fetched before anything that may be locked.
        List<Chapter> wanted = new ArrayList<>();
        for (Chapter chapter : originalItems) {
            if (!downloadedUrls.contains(chapter.url)) wanted.add(chapter);
        }
        Collections.sort(wanted, (a, b) -> Float.compare(a.id, b.id));
        DownloadService.enqueueAll(requireContext(), wanted, novel.sourceId);
        adapter.notifyDataSetChanged();
    }

    private boolean hasNotifPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Implemented by an activity that wants to open the chosen chapter itself (the reader does).
    public interface OnChapterPicked {
        void onChapterPicked(Chapter chapter);
    }

    public class SearchBarTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            searchResults(charSequence.toString());
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    }
}
