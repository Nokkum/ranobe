package org.ranobe.ranobe.ui.library;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.config.RanobeSettings;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.databinding.FragmentLibraryBinding;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.service.DownloadService;
import org.ranobe.ranobe.ui.browse.adapter.NovelAdapter;
import org.ranobe.ranobe.ui.chapters.viewmodel.ChaptersViewModel;
import org.ranobe.ranobe.ui.views.GetPro;
import org.ranobe.ranobe.util.DisplayUtils;
import org.ranobe.ranobe.util.DownloadCleaner;
import org.ranobe.ranobe.util.ListUtils;
import org.ranobe.ranobe.util.NumberUtils;
import org.ranobe.ranobe.worker.ChapterUpdateScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Library extends Fragment implements NovelAdapter.OnNovelItemClickListener, NovelAdapter.OnNovelMenuClickListener, NovelOptions.Listener, MaterialToolbar.OnMenuItemClickListener {
    private static final int SORT_DEFAULT = 0;
    private static final int SORT_AZ = 1;
    private static final int SORT_ZA = 2;

    private FragmentLibraryBinding binding;
    private List<Novel> allNovels = new ArrayList<>();
    private NovelAdapter adapter;
    private int currentSort = SORT_DEFAULT;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    // "Download All" waits here while the notification permission is asked for
    private Novel pendingDownloadNovel = null;
    private final ActivityResultLauncher<String> downloadPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                Novel novel = pendingDownloadNovel;
                pendingDownloadNovel = null;
                if (granted && novel != null) {
                    queueAllChapters(novel);
                } else if (binding != null) {
                    Snackbar.make(binding.getRoot(), R.string.library_download_permission_denied, Snackbar.LENGTH_SHORT).show();
                }
            });

    public Library() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        enableNewChapterUpdates();
                    } else {
                        View root = getView();
                        if (root != null) {
                            Snackbar.make(root, "Notification permission denied", Snackbar.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setOnMenuItemClickListener(this);
        syncNewChaptersIcon();
        maybeShowChapterUpdatesBanner();

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilterAndSort(s.toString());
            }
        });

        DisplayUtils.applyNovelGrid(binding.novelList);
        adapter = new NovelAdapter(new ArrayList<>(), this, this).asGrid();
        binding.novelList.setAdapter(adapter);

        RanobeDatabase.database().novels().list().observe(getViewLifecycleOwner(), novels -> {
            allNovels = novels;
            applyFilterAndSort(getCurrentQuery());
        });
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.search) {
            toggleSearchView();
            return true;
        } else if (id == R.id.sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.new_chapters) {
            handleNewChaptersToggle();
            return true;
        }
        return false;
    }

    private void handleNewChaptersToggle() {
        if (!Ranobe.isPro()) {
            GetPro pro = new GetPro();
            pro.show(getParentFragmentManager(), GetPro.TAG);
            return;
        }

        boolean enabled = Ranobe.isNewChapterUpdatesEnabled();
        if (enabled) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Disable Notifications")
                    .setMessage("Disable new chapter notifications?")
                    .setPositiveButton("Disable", (dialog, i) -> disableNewChapterUpdates())
                    .setNegativeButton("Cancel", (dialog, i) -> dialog.dismiss())
                    .show();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Enable Notifications")
                    .setMessage("Get notified when new chapters are available for novels in your library?")
                    .setPositiveButton("Enable", (dialog, i) -> requestNotificationPermissionAndEnable())
                    .setNegativeButton("Cancel", (dialog, i) -> dialog.dismiss())
                    .show();
        }
    }

    private void requestNotificationPermissionAndEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                enableNewChapterUpdates();
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            enableNewChapterUpdates();
        }
    }

    private void enableNewChapterUpdates() {
        Ranobe.setNewChapterUpdatesEnabled(true);
        ChapterUpdateScheduler.schedule(requireContext());
        Snackbar.make(binding.getRoot(), "New chapter notifications enabled", Snackbar.LENGTH_SHORT).show();
        syncNewChaptersIcon();
    }

    private void disableNewChapterUpdates() {
        Ranobe.setNewChapterUpdatesEnabled(false);
        ChapterUpdateScheduler.cancel(requireContext());
        Snackbar.make(binding.getRoot(), "New chapter notifications disabled", Snackbar.LENGTH_SHORT).show();
        syncNewChaptersIcon();
    }

    private void maybeShowChapterUpdatesBanner() {
        if (Ranobe.isChapterUpdateBannerShown() || Ranobe.isNewChapterUpdatesEnabled()) {
            return;
        }
        binding.chapterUpdatesBanner.setVisibility(View.VISIBLE);
        binding.enableUpdatesButton.setOnClickListener(v -> {
            Ranobe.setChapterUpdateBannerShown(true);
            binding.chapterUpdatesBanner.setVisibility(View.GONE);
            handleNewChaptersToggle();
        });
    }

    private void syncNewChaptersIcon() {
        MenuItem item = binding.toolbar.getMenu().findItem(R.id.new_chapters);
        if (item == null) return;
        boolean enabled = Ranobe.isPro() && Ranobe.isNewChapterUpdatesEnabled();
        item.setIcon(enabled ? R.drawable.ic_notifications_active : R.drawable.ic_disabled);
    }

    private void toggleSearchView() {
        int visibility = binding.searchView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE;
        binding.searchView.setVisibility(visibility);
        if (visibility == View.GONE) {
            binding.searchInput.setText(null);
        }
    }

    private String getCurrentQuery() {
        Editable text = binding.searchInput.getText();
        return text != null ? text.toString() : "";
    }

    private void applyFilterAndSort(String query) {
        binding.progress.hide();
        List<Novel> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.getDefault()).trim();

        for (Novel novel : allNovels) {
            if (lowerQuery.isEmpty() || (novel.name != null && novel.name.toLowerCase(Locale.getDefault()).contains(lowerQuery))) {
                filtered.add(novel);
            }
        }

        if (currentSort == SORT_AZ) {
            filtered = ListUtils.sortByName(filtered, true);
        } else if (currentSort == SORT_ZA) {
            filtered = ListUtils.sortByName(filtered, false);
        }

        if (filtered.isEmpty() && allNovels.isEmpty()) {
            showNoNovels();
        } else if (filtered.isEmpty()) {
            // library has novels, just none matching the search
            binding.emoji.setText(null);
            binding.error.setText(R.string.no_results_err);
        } else {
            binding.emoji.setText(null);
            binding.error.setText(null);
        }

        adapter.submit(filtered);
    }

    private void showSortDialog() {
        String[] options = {"Default", "A → Z", "Z → A"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort)
                .setSingleChoiceItems(options, currentSort, (dialog, which) -> {
                    currentSort = which;
                    applyFilterAndSort(getCurrentQuery());
                    dialog.dismiss();
                })
                .show();
    }

    private void showNoNovels() {
        binding.error.setText(R.string.no_novels_error);
        binding.emoji.setText(Ranobe.SILLY_EMOJI[NumberUtils.getRandom(Ranobe.SILLY_EMOJI.length)]);
    }

    @Override
    public void onNovelItemClick(Novel item) {
        RanobeSettings.get().setCurrentSource(item.sourceId).save();
        NavController controller = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);

        Bundle bundle = new Bundle();
        bundle.putParcelable(Ranobe.KEY_NOVEL, item);
        controller.navigate(R.id.library_fragment_to_details, bundle);
    }

    // the three dots on a novel
    @Override
    public void onNovelMenuClick(Novel novel) {
        NovelOptions.newInstance(novel).show(getChildFragmentManager(), NovelOptions.TAG);
    }

    // Download All

    @Override
    public void onDownloadAll(Novel novel) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.download_all)
                .setMessage(R.string.downloading_all_chapters_confirmation)
                .setPositiveButton(android.R.string.ok, (dialog, i) -> {
                    if (hasNotifPermission()) {
                        queueAllChapters(novel);
                    } else {
                        pendingDownloadNovel = novel;
                        downloadPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.dismiss())
                .show();
    }

    private boolean hasNotifPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // A novel in the library may never have had its chapter list opened, so the list is fetched first (or read
    // from the saved copy), then every chapter that is not downloaded yet is queued.
    private void queueAllChapters(Novel novel) {
        final Context appContext = requireContext().getApplicationContext();
        Snackbar.make(binding.getRoot(), R.string.library_getting_chapters, Snackbar.LENGTH_SHORT).show();
        ChaptersViewModel chaptersViewModel = new ViewModelProvider(requireActivity()).get(ChaptersViewModel.class);
        new ChapterListRequest(chaptersViewModel, novel, new ChapterListRequest.Callback() {
            @Override
            public void onList(List<Chapter> chapters) {
                enqueueMissing(appContext, novel, chapters);
            }

            @Override
            public void onFailed(@Nullable String message) {
                if (binding == null) return;
                Snackbar.make(binding.getRoot(),
                        appContext.getString(R.string.library_download_failed, message == null ? "" : message),
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void enqueueMissing(Context appContext, Novel novel, List<Chapter> chapters) {
        final View root = binding == null ? null : binding.getRoot();
        RanobeDatabase.databaseExecutor.execute(() -> {
            Set<String> downloaded = new HashSet<>(RanobeDatabase.database().chapters().getDownloadedUrls(novel.url));
            List<Chapter> wanted = new ArrayList<>();
            for (Chapter chapter : chapters) {
                if (!downloaded.contains(chapter.url)) wanted.add(chapter);
            }
            // Oldest first, as in the chapter list: sources such as WTR-LAB unlock the first chapters for
            // everyone, so those should be fetched before anything that may be locked.
            Collections.sort(wanted, (a, b) -> Float.compare(a.id, b.id));
            if (!wanted.isEmpty()) {
                try {
                    DownloadService.enqueueAll(appContext, wanted, novel.sourceId);
                } catch (IllegalStateException e) {
                    // the app went to the background while the list loaded, so the service may not start now;
                    // the chapters stay queued and are picked up by the next download that starts it
                }
            }
            final int count = wanted.size();
            if (root == null) return;
            root.post(() -> {
                if (binding == null) return;
                Snackbar.make(binding.getRoot(), count == 0
                                ? appContext.getString(R.string.library_nothing_to_download)
                                : appContext.getString(R.string.library_download_queued, count),
                        Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    // Delete All Chapters

    @Override
    public void onDeleteAllChapters(Novel novel) {
        final View root = binding.getRoot();
        RanobeDatabase.databaseExecutor.execute(() -> {
            int count = RanobeDatabase.database().chapters().getDownloadedUrls(novel.url).size();
            root.post(() -> {
                if (binding == null) return;
                if (count == 0) {
                    Snackbar.make(binding.getRoot(), R.string.library_no_downloads, Snackbar.LENGTH_SHORT).show();
                } else {
                    confirmDeleteAllChapters(novel, count);
                }
            });
        });
    }

    private void confirmDeleteAllChapters(Novel novel, int count) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_all_chapters)
                .setMessage(getString(R.string.delete_downloads_confirmation, count))
                .setPositiveButton(R.string.delete_downloads_confirm, (dialog, i) -> deleteAllChapters(novel))
                .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.dismiss())
                .show();
    }

    private void deleteAllChapters(Novel novel) {
        final Context appContext = requireContext().getApplicationContext();
        final View root = binding.getRoot();
        RanobeDatabase.databaseExecutor.execute(() -> {
            int removed = DownloadCleaner.deleteNovelDownloads(appContext, novel.url);
            root.post(() -> {
                if (binding == null) return;
                Snackbar.make(binding.getRoot(), appContext.getString(R.string.downloads_deleted, removed), Snackbar.LENGTH_SHORT).show();
            });
        });
    }

    // Remove Bookmark

    @Override
    public void onRemoveBookmark(Novel novel) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.remove_bookmark)
                .setMessage(R.string.remove_bookmark_confirmation)
                .setPositiveButton(R.string.remove, (dialog, i) -> removeFromLib(novel))
                .setNegativeButton(android.R.string.cancel, (dialog, i) -> dialog.dismiss())
                .show();
    }

    private void removeFromLib(Novel novel) {
        RanobeDatabase.databaseExecutor.execute(() -> RanobeDatabase.database().novels().delete(novel.url));
        Snackbar.make(binding.getRoot(), R.string.library_removing_bookmark, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        adapter = null;
    }
}
