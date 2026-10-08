package org.ranobe.ranobe.ui.search;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import org.ranobe.ranobe.util.SearchFilters;
import org.ranobe.ranobe.util.SearchHistory;
import org.ranobe.ranobe.util.SearchPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Search extends Fragment implements NovelAdapter.OnNovelItemClickListener {
    // ask for the next page while this many covers are still to the right of the screen
    private static final int PREFETCH_ITEMS = 4;
    private static final int NO_SOURCE = Integer.MIN_VALUE;
    // search while typing: wait for a pause, and not for one or two letters, so slow typing does not
    // send a request to every source on each key
    private static final int LIVE_MIN_CHARS = 3;
    private static final long LIVE_DELAY_MS = 900L;

    private final List<SearchViewModel.Row> rows = new ArrayList<>();
    private FragmentSearchBinding binding;
    private SearchViewModel viewModel;
    private SearchResultAdapter resultAdapter;
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private final Runnable liveSearch = this::runLiveSearch;
    private Chip allSourcesChip;
    private boolean updatingSourceChips;

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
                debounce.removeCallbacks(liveSearch);
                if (binding.liveChip.isChecked() && s != null && s.toString().trim().length() >= LIVE_MIN_CHARS) {
                    debounce.postDelayed(liveSearch, LIVE_DELAY_MS);
                }
            }
        });
        binding.failedRetry.setOnClickListener(v -> viewModel.retryFailed());

        binding.liveChip.setChecked(SearchPreferences.isLiveSearch(requireContext()));
        binding.liveChip.setOnCheckedChangeListener((chip, checked) -> {
            SearchPreferences.setLiveSearch(requireContext(), checked);
            if (!checked) debounce.removeCallbacks(liveSearch);
        });
        binding.filtersChip.setOnClickListener(v -> showFiltersDialog());
        buildSourceChips();
        updateFiltersChipLabel();

        binding.resultList.setLayoutManager(new LinearLayoutManager(requireContext()));
        resultAdapter = new SearchResultAdapter();
        binding.resultList.setAdapter(resultAdapter);

        showRecent(SearchHistory.load(requireContext()));

        viewModel.getRows().observe(getViewLifecycleOwner(), this::setRows);
        viewModel.getFailedSources().observe(getViewLifecycleOwner(), this::setFailedSources);
        viewModel.getSkippedSources().observe(getViewLifecycleOwner(), this::setSkippedSources);
        viewModel.isLoading().observe(getViewLifecycleOwner(), loading -> updateState());
    }

    private void runLiveSearch() {
        if (binding == null) return;
        CharSequence text = binding.searchField.getText();
        if (text != null && text.toString().trim().length() >= LIVE_MIN_CHARS) viewModel.search(text.toString());
    }

    private void searchNovels() {
        debounce.removeCallbacks(liveSearch);
        CharSequence text = binding.searchField.getText();
        if (text == null || text.toString().trim().isEmpty()) return;
        binding.searchField.clearFocus();
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(binding.searchField.getWindowToken(), 0);
        showRecent(SearchHistory.add(requireContext(), text.toString()));
        viewModel.search(text.toString());
    }

    // source chips

    // One chip per enabled source, all checked unless the user narrowed the search earlier.
    private void buildSourceChips() {
        binding.sourceChips.removeAllViews();
        Set<Integer> selected = viewModel.getSelectedIds();
        updatingSourceChips = true;

        allSourcesChip = newSourceChip(getString(R.string.search_all_sources));
        allSourcesChip.setOnClickListener(v -> {
            // switch every source on without each chip applying the selection on its own
            updatingSourceChips = true;
            for (int i = 1; i < binding.sourceChips.getChildCount(); i++) {
                ((Chip) binding.sourceChips.getChildAt(i)).setChecked(true);
            }
            refreshAllSourcesChip();
            updatingSourceChips = false;
            applySelection();
        });
        binding.sourceChips.addView(allSourcesChip);

        for (DataSource source : viewModel.availableSources()) {
            Chip chip = newSourceChip(source.name);
            chip.setTag(source.sourceId);
            chip.setChecked(selected.contains(source.sourceId));
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (!updatingSourceChips) onSourceToggled((Chip) button);
            });
            binding.sourceChips.addView(chip);
        }
        refreshAllSourcesChip();
        updatingSourceChips = false;
    }

    private Chip newSourceChip(String label) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_source_chip, binding.sourceChips, false);
        chip.setText(label);
        return chip;
    }

    private void onSourceToggled(Chip chip) {
        if (checkedSourceIds().isEmpty()) {
            // a search needs at least one source, so the last one cannot be switched off
            updatingSourceChips = true;
            chip.setChecked(true);
            updatingSourceChips = false;
            return;
        }
        refreshAllSourcesChip();
        applySelection();
    }

    private Set<Integer> checkedSourceIds() {
        Set<Integer> ids = new java.util.HashSet<>();
        for (int i = 1; i < binding.sourceChips.getChildCount(); i++) {
            Chip chip = (Chip) binding.sourceChips.getChildAt(i);
            if (chip.isChecked() && chip.getTag() instanceof Integer) ids.add((Integer) chip.getTag());
        }
        return ids;
    }

    private void refreshAllSourcesChip() {
        boolean every = binding.sourceChips.getChildCount() > 1;
        for (int i = 1; i < binding.sourceChips.getChildCount(); i++) {
            if (!((Chip) binding.sourceChips.getChildAt(i)).isChecked()) every = false;
        }
        allSourcesChip.setChecked(every);
    }

    private void applySelection() {
        viewModel.setOptions(checkedSourceIds(), viewModel.getStatus(), viewModel.getGenre());
    }

    // filters

    private void showFiltersDialog() {
        DialogSearchFiltersBinding dialog = DialogSearchFiltersBinding.inflate(getLayoutInflater());

        List<String> statusOptions = new ArrayList<>();
        statusOptions.add(SearchFilters.ANY);
        statusOptions.addAll(SearchFilters.STATUSES);
        int checkedId = View.NO_ID;
        for (String option : statusOptions) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_source_chip, dialog.statusGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(option.isEmpty() ? getString(R.string.search_any) : statusLabel(option));
            chip.setTag(option);
            dialog.statusGroup.addView(chip);
            if (option.equals(viewModel.getStatus())) checkedId = chip.getId();
        }
        dialog.statusGroup.check(checkedId);

        List<String> genres = new ArrayList<>();
        genres.add(getString(R.string.search_any_genre));
        genres.addAll(SearchFilters.GENRES);
        dialog.genreInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, genres));
        String currentGenre = viewModel.getGenre();
        dialog.genreInput.setText(currentGenre.isEmpty() ? genres.get(0) : currentGenre, false);

        SearchViewModel.FilterSupportSummary support = viewModel.describeFilterSupport();
        dialog.filterNote.setText(getString(R.string.search_filter_support,
                joinOrNone(support.status), joinOrNone(support.statusPartial), joinOrNone(support.genre)));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.search_filters)
                .setView(dialog.getRoot())
                .setPositiveButton(R.string.search_apply, (d, which) -> {
                    Chip checked = dialog.statusGroup.findViewById(dialog.statusGroup.getCheckedChipId());
                    String newStatus = checked == null ? SearchFilters.ANY : (String) checked.getTag();
                    String picked = String.valueOf(dialog.genreInput.getText());
                    String newGenre = picked.equals(genres.get(0)) ? SearchFilters.ANY : picked;
                    viewModel.setOptions(viewModel.getSelectedIds(), newStatus, newGenre);
                    updateFiltersChipLabel();
                })
                .setNeutralButton(R.string.search_reset, (d, which) -> {
                    viewModel.setOptions(viewModel.getSelectedIds(), SearchFilters.ANY, SearchFilters.ANY);
                    updateFiltersChipLabel();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        List<String> parts = new ArrayList<>();
        if (!viewModel.getStatus().isEmpty()) parts.add(statusLabel(viewModel.getStatus()));
        if (!viewModel.getGenre().isEmpty()) parts.add(viewModel.getGenre());
        binding.filtersChip.setText(parts.isEmpty()
                ? getString(R.string.search_filters)
                : getString(R.string.search_filters_active, TextUtils.join(", ", parts)));
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
        boolean searched = viewModel.getKeyword() != null;
        if (loading) binding.progress.show();
        else binding.progress.hide();
        binding.empty.setVisibility(!loading && searched && rows.isEmpty() ? View.VISIBLE : View.GONE);
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
        debounce.removeCallbacks(liveSearch);
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
