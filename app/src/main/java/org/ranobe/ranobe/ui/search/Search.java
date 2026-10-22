package org.ranobe.ranobe.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.databinding.DialogSearchFiltersBinding;
import org.ranobe.ranobe.databinding.FragmentSearchBinding;
import org.ranobe.ranobe.databinding.ItemSearchResultBinding;
import org.ranobe.ranobe.models.DataSource;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.ui.browse.adapter.NovelAdapter;
import org.ranobe.ranobe.ui.search.viewmodel.SearchViewModel;
import org.ranobe.ranobe.ui.views.SpacingDecorator;
import org.ranobe.ranobe.util.ChapterRanges;
import org.ranobe.ranobe.util.SearchFilters;
import org.ranobe.ranobe.util.SearchHistory;
import org.ranobe.ranobe.util.SearchPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Search extends Fragment implements NovelAdapter.OnNovelItemClickListener {
    // ask for the next page while this many covers are still to the right of the screen
    private static final int PREFETCH_ITEMS = 4;
    private static final int NO_SOURCE = Integer.MIN_VALUE;
    // search while typing: starts as soon as this many letters are typed, and not for one or two
    private static final int LIVE_MIN_CHARS = 3;

    private final List<SearchViewModel.Row> rows = new ArrayList<>();
    private FragmentSearchBinding binding;
    private SearchViewModel viewModel;
    private SearchResultAdapter resultAdapter;
    private OptionSection sourcesSection;
    private OptionSection chaptersSection;
    private OptionSection statusSection;

    public Search() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SearchViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.searchView.setEndIconOnClickListener(v -> searchNovels());
        binding.searchField.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            // a hardware enter sends both down and up; only search once
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (enter && event.getAction() == KeyEvent.ACTION_DOWN)) {
                searchNovels();
                return true;
            }
            return enter;
        });
        binding.searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateRecentVisibility();
                String typed = s == null ? "" : s.toString().trim();
                if (typed.isEmpty()) {
                    // the field was emptied: the results of the old search go too
                    viewModel.clear();
                } else if (typed.length() >= LIVE_MIN_CHARS && SearchPreferences.isLiveSearch(requireContext())) {
                    viewModel.search(s.toString());
                }
            }
        });
        binding.failedRetry.setOnClickListener(v -> viewModel.retryFailed());

        binding.filtersChip.setOnClickListener(v -> showFiltersDialog());
        setUpOptionSections();
        updateFiltersChipLabel();

        binding.resultList.setLayoutManager(new LinearLayoutManager(requireContext()));
        resultAdapter = new SearchResultAdapter();
        binding.resultList.setAdapter(resultAdapter);

        showRecent(SearchHistory.load(requireContext()));

        viewModel.getRows().observe(getViewLifecycleOwner(), this::setRows);
        viewModel.getFailedSources().observe(getViewLifecycleOwner(), this::setFailedSources);
        viewModel.getSkippedSources().observe(getViewLifecycleOwner(), this::setSkippedSources);
        viewModel.isLoading().observe(getViewLifecycleOwner(), loading -> updateState());
        viewModel.hasTimedOut().observe(getViewLifecycleOwner(), timedOut -> updateState());
    }

    private void searchNovels() {
        CharSequence text = binding.searchField.getText();
        if (text == null || text.toString().trim().isEmpty()) return;
        binding.searchField.clearFocus();
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(binding.searchField.getWindowToken(), 0);
        showRecent(SearchHistory.add(requireContext(), text.toString()));
        viewModel.search(text.toString());
    }

    // option chips: sources, chapters, status

    // Each of the three chips starts on ("all" / "any") and opens out its choices when it is switched off.
    // What is chosen lives in the view model, so it is still there after a visit to a novel's page.
    private void setUpOptionSections() {
        // the saved view state is by id; the view model holds the truth, and it is shown below
        binding.sourceOptions.setSaveFromParentEnabled(false);
        binding.chapterOptions.setSaveFromParentEnabled(false);
        binding.statusOptions.setSaveFromParentEnabled(false);

        sourcesSection = new OptionSection(binding.sourcesChip, binding.sourceOptions,
                sectionListener(SearchViewModel.PICKER_SOURCES));
        chaptersSection = new OptionSection(binding.chaptersChip, binding.chapterOptions,
                sectionListener(SearchViewModel.PICKER_CHAPTERS));
        statusSection = new OptionSection(binding.statusChip, binding.statusOptions,
                sectionListener(SearchViewModel.PICKER_STATUS));

        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (DataSource source : viewModel.availableSources()) {
            ids.add(String.valueOf(source.sourceId));
            labels.add(source.name);
        }
        sourcesSection.setOptions(getLayoutInflater(), ids, labels);

        ids = new ArrayList<>();
        for (ChapterRanges.Range range : ChapterRanges.ALL) ids.add(range.id);
        chaptersSection.setOptions(getLayoutInflater(), ids, ids);

        ids = new ArrayList<>();
        labels = new ArrayList<>();
        for (String status : SearchFilters.STATUSES) {
            ids.add(status);
            labels.add(statusLabel(status));
        }
        statusSection.setOptions(getLayoutInflater(), ids, labels);

        Set<String> pickedSources = new LinkedHashSet<>();
        for (Integer id : viewModel.getSourceSelection()) pickedSources.add(String.valueOf(id));
        sourcesSection.show(pickedSources, viewModel.isPickerOpen(SearchViewModel.PICKER_SOURCES));
        chaptersSection.show(viewModel.getChapterRanges(), viewModel.isPickerOpen(SearchViewModel.PICKER_CHAPTERS));
        statusSection.show(viewModel.getStatuses(), viewModel.isPickerOpen(SearchViewModel.PICKER_STATUS));
    }

    private OptionSection.Listener sectionListener(String picker) {
        return new OptionSection.Listener() {
            @Override
            public void onSelectionChanged(Set<String> selected) {
                applyOptions();
            }

            @Override
            public void onOpenChanged(boolean open) {
                viewModel.setPickerOpen(picker, open);
            }
        };
    }

    // Hands what the three sections hold, and the genre, to the view model.
    private void applyOptions() {
        Set<Integer> sources = new LinkedHashSet<>();
        for (String id : sourcesSection.selected()) sources.add(Integer.parseInt(id));
        viewModel.setOptions(sources, statusSection.selected(), chaptersSection.selected(), viewModel.getGenre());
    }

    // filters

    private void showFiltersDialog() {
        DialogSearchFiltersBinding dialog = DialogSearchFiltersBinding.inflate(getLayoutInflater());

        dialog.liveSwitch.setChecked(SearchPreferences.isLiveSearch(requireContext()));

        List<String> genres = new ArrayList<>();
        genres.add(getString(R.string.search_any_genre));
        genres.addAll(SearchFilters.GENRES);
        dialog.genreInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, genres));
        String currentGenre = viewModel.getGenre();
        dialog.genreInput.setText(currentGenre.isEmpty() ? genres.get(0) : currentGenre, false);

        dialog.filterNote.setText(getString(R.string.search_genre_support, joinOrNone(viewModel.genreSupportingSources())));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.search_filters)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.search_apply, (d, which) -> {
                    boolean live = dialog.liveSwitch.isChecked();
                    SearchPreferences.setLiveSearch(requireContext(), live);
                    String picked = String.valueOf(dialog.genreInput.getText());
                    String newGenre = picked.equals(genres.get(0)) ? SearchFilters.ANY : picked;
                    setGenre(newGenre);
                })
                // clears the genre; "search as you type" is a setting, not a filter, so it stays as it is
                .setNeutralButton(R.string.search_reset, (d, which) -> setGenre(SearchFilters.ANY))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setGenre(String genre) {
        viewModel.setOptions(viewModel.getSourceSelection(), viewModel.getStatuses(), viewModel.getChapterRanges(), genre);
        updateFiltersChipLabel();
    }

    private String joinOrNone(List<String> names) {
        return names.isEmpty() ? getString(R.string.search_filter_none) : TextUtils.join(", ", names);
    }

    private String statusLabel(String status) {
        switch (status) {
            case SearchFilters.ONGOING:
                return getString(R.string.search_status_ongoing);
            case SearchFilters.COMPLETED:
                return getString(R.string.search_status_completed);
            case SearchFilters.HIATUS:
                return getString(R.string.search_status_hiatus);
            case SearchFilters.DROPPED:
                return getString(R.string.search_status_dropped);
            default:
                return status;
        }
    }

    private void updateFiltersChipLabel() {
        if (binding == null) return;
        String genre = viewModel.getGenre();
        binding.filtersChip.setText(genre.isEmpty()
                ? getString(R.string.search_filters)
                : getString(R.string.search_filters_chip_genre, genre));
    }

    private void setSkippedSources(List<String> names) {
        if (binding == null) return;
        boolean any = names != null && !names.isEmpty();
        binding.skippedNote.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any) binding.skippedNote.setText(getString(R.string.search_filter_skipped, TextUtils.join(", ", names)));
    }

    // recent searches

    private void showRecent(List<String> entries) {
        binding.recentChips.removeAllViews();
        for (String entry : entries) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_recent_search_chip, binding.recentChips, false);
            chip.setText(entry);
            chip.setOnClickListener(v -> {
                binding.searchField.setText(entry);
                binding.searchField.setSelection(entry.length());
                searchNovels();
            });
            chip.setOnCloseIconClickListener(v -> showRecent(SearchHistory.remove(requireContext(), entry)));
            binding.recentChips.addView(chip);
        }
        updateRecentVisibility();
    }

    // The recent searches are offered while the search field is empty.
    private void updateRecentVisibility() {
        if (binding == null) return;
        CharSequence text = binding.searchField.getText();
        boolean empty = text == null || text.toString().trim().isEmpty();
        boolean any = binding.recentChips.getChildCount() > 0;
        binding.recentScroll.setVisibility(empty && any ? View.VISIBLE : View.GONE);
    }

    // results

    private void setRows(List<SearchViewModel.Row> next) {
        List<SearchViewModel.Row> old = new ArrayList<>(rows);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return old.get(oldPosition).sourceId == next.get(newPosition).sourceId;
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return sameContents(old.get(oldPosition), next.get(newPosition));
            }
        });
        rows.clear();
        rows.addAll(next);
        diff.dispatchUpdatesTo(resultAdapter);
        updateState();
    }

    private static boolean sameContents(SearchViewModel.Row a, SearchViewModel.Row b) {
        if (a.loadingMore != b.loadingMore || a.endReached != b.endReached) return false;
        if (a.novels.size() != b.novels.size()) return false;
        if (a.novels.isEmpty()) return true;
        int last = a.novels.size() - 1;
        return Objects.equals(a.novels.get(last).url, b.novels.get(last).url);
    }

    private void setFailedSources(List<String> names) {
        if (binding == null) return;
        boolean any = names != null && !names.isEmpty();
        binding.failedBanner.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any) binding.failedText.setText(getString(R.string.search_failed_sources, TextUtils.join(", ", names)));
    }

    private void updateState() {
        if (binding == null) return;
        boolean loading = Boolean.TRUE.equals(viewModel.isLoading().getValue());
        // the wait set in Settings ran out with nothing found, though some sources haven't answered yet
        boolean timedOut = Boolean.TRUE.equals(viewModel.hasTimedOut().getValue());
        boolean searched = viewModel.getKeyword() != null;
        if (loading && !timedOut) binding.progress.show();
        else binding.progress.hide();
        boolean noResults = searched && rows.isEmpty() && (!loading || timedOut);
        binding.empty.setVisibility(noResults ? View.VISIBLE : View.GONE);
        // still waiting on some sources: a late answer will still show up
        binding.emptySlow.setVisibility(noResults && loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onNovelItemClick(Novel item) {
        NavController controller = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);

        Bundle bundle = new Bundle();
        bundle.putParcelable(Ranobe.KEY_NOVEL, item);
        controller.navigate(R.id.details_fragment, bundle);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.MyViewHolder> {
        private final SpacingDecorator spacingDecorator = new SpacingDecorator(10);
        // every row is the same horizontal cover list, so let them share recycled tiles
        private final RecyclerView.RecycledViewPool novelPool = new RecyclerView.RecycledViewPool();

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemSearchResultBinding resultBinding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new MyViewHolder(resultBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
            holder.bindRow(rows.get(position));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        public class MyViewHolder extends RecyclerView.ViewHolder {
            private final ItemSearchResultBinding binding;
            private final LinearLayoutManager layoutManager;
            private final NovelAdapter novelAdapter = new NovelAdapter(new ArrayList<>(), Search.this);
            private int sourceId = NO_SOURCE;
            private boolean endReached = true;

            public MyViewHolder(@NonNull ItemSearchResultBinding binding) {
                super(binding.getRoot());
                this.binding = binding;

                layoutManager = new LinearLayoutManager(binding.getRoot().getContext(), LinearLayoutManager.HORIZONTAL, false);
                layoutManager.setRecycleChildrenOnDetach(true);
                binding.searchResults.setLayoutManager(layoutManager);
                binding.searchResults.setRecycledViewPool(novelPool);
                binding.searchResults.addItemDecoration(spacingDecorator);
                binding.searchResults.setAdapter(novelAdapter);
                binding.searchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        if (dx > 0) maybeLoadMore();
                    }
                });
            }

            void bindRow(SearchViewModel.Row row) {
                binding.sourceName.setText(row.isLibrary() ? getString(R.string.search_in_library) : row.title);
                binding.rowProgress.setVisibility(row.loadingMore ? View.VISIBLE : View.GONE);
                boolean otherRow = sourceId != row.sourceId;
                sourceId = row.sourceId;
                endReached = row.endReached;
                // the holder is reused for other rows, so the adapter is kept and only its items change
                novelAdapter.submit(row.novels);
                if (otherRow) layoutManager.scrollToPosition(0);
            }

            private void maybeLoadMore() {
                if (sourceId == NO_SOURCE || endReached) return;
                int total = novelAdapter.getItemCount();
                if (total > 0 && layoutManager.findLastVisibleItemPosition() >= total - PREFETCH_ITEMS) {
                    viewModel.loadMore(sourceId);
                }
            }
        }
    }
}
