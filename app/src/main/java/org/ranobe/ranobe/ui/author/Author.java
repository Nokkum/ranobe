package org.ranobe.ranobe.ui.author;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.config.RanobeSettings;
import org.ranobe.ranobe.databinding.FragmentAuthorBinding;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.sources.SourceManager;
import org.ranobe.ranobe.ui.author.viewmodel.AuthorViewModel;
import org.ranobe.ranobe.ui.browse.adapter.NovelAdapter;
import org.ranobe.ranobe.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;

// The page of one author: their works, as a grid of covers like Browse. Opened from the author's name on a
// novel's page.
public class Author extends Fragment implements NovelAdapter.OnNovelItemClickListener {
    // start the next page while this many rows are still below the screen
    private static final int PREFETCH_ROWS = 2;

    private final List<Novel> list = new ArrayList<>();
    private FragmentAuthorBinding binding;

    private AuthorViewModel viewModel;
    private NovelAdapter adapter;
    private GridLayoutManager layoutManager;

    private String author;
    private int sourceId;
    private String excludeUrl;
    private String sourceName = "";

    public Author() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments != null) {
            author = arguments.getString(Ranobe.KEY_AUTHOR);
            sourceId = arguments.getInt(Ranobe.KEY_SOURCE_ID);
            excludeUrl = arguments.getString(Ranobe.KEY_EXCLUDE_URL);
        }
        // this page's own view model: it stays with the page on the back stack, so coming back from one of the
        // novels shows the list as it was, and a second author page doesn't take its state
        viewModel = new ViewModelProvider(this).get(AuthorViewModel.class);
        viewModel.open(author, sourceId, excludeUrl);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAuthorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setUpToolbar();

        adapter = new NovelAdapter(list, this).asGrid();
        layoutManager = DisplayUtils.applyNovelGrid(binding.novelList);
        binding.novelList.setAdapter(adapter);
        binding.novelList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) maybeLoadMore();
            }
        });
        binding.retry.setOnClickListener(v -> viewModel.loadMore());

        viewModel.getNovels().observe(getViewLifecycleOwner(), this::setNovels);
        viewModel.isLoading().observe(getViewLifecycleOwner(), loading -> updateState());
        viewModel.hasFailed().observe(getViewLifecycleOwner(), failed -> updateState());
        viewModel.getPageError().observe(getViewLifecycleOwner(), this::setUpPageError);
    }

    private void setUpToolbar() {
        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main).navigateUp());
        binding.toolbar.setTitle(author);

        // say where the list comes from, so a short one isn't taken for everything the author wrote
        int effectiveSourceId = sourceId > 0 ? sourceId : RanobeSettings.get().getCurrentSource();
        sourceName = SourceManager.getSource(effectiveSourceId).metadata().name;
        binding.toolbar.setSubtitle(viewModel.supportsLookup()
                ? getString(R.string.author_subtitle_source, sourceName)
                : getString(R.string.author_subtitle_saved));
    }

    private void setNovels(List<Novel> novels) {
        int old = list.size();
        list.clear();
        list.addAll(novels);
        if (novels.size() >= old) {
            adapter.notifyItemRangeInserted(old, novels.size() - old);
        } else {
            adapter.notifyDataSetChanged();
        }
        updateState();
        // a short first page can't be scrolled, so keep loading until the screen is filled
        binding.novelList.post(() -> {
            if (binding != null && !binding.novelList.canScrollVertically(1)) maybeLoadMore();
        });
    }

    private void maybeLoadMore() {
        if (layoutManager == null || viewModel.isEndReached()) return;
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        if (lastVisible >= list.size() - layoutManager.getSpanCount() * PREFETCH_ROWS) {
            viewModel.loadMore();
        }
    }

    private void updateState() {
        if (binding == null) return;
        boolean loading = Boolean.TRUE.equals(viewModel.isLoading().getValue());
        boolean failed = Boolean.TRUE.equals(viewModel.hasFailed().getValue());
        boolean empty = list.isEmpty();
        binding.initialProgress.setVisibility(loading && empty ? View.VISIBLE : View.GONE);
        binding.progress.setVisibility(loading && !empty ? View.VISIBLE : View.GONE);

        // what to say when there is nothing to show: it failed, the source can't look it up, or there is none
        boolean failedEmpty = !loading && empty && failed;
        boolean doneEmpty = !loading && empty && !failed && viewModel.isEndReached();
        if (failedEmpty) {
            binding.empty.setText(R.string.author_load_failed);
        } else if (doneEmpty) {
            binding.empty.setText(viewModel.supportsLookup()
                    ? getString(R.string.author_empty)
                    : getString(R.string.author_unsupported, sourceName));
        }
        binding.state.setVisibility(failedEmpty || doneEmpty ? View.VISIBLE : View.GONE);
        binding.retry.setVisibility(failedEmpty ? View.VISIBLE : View.GONE);
    }

    private void setUpPageError(Boolean error) {
        if (error == null) return;
        viewModel.clearPageError();
        Snackbar.make(binding.getRoot(), R.string.author_page_failed, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry, v -> viewModel.loadMore())
                .show();
    }

    @Override
    public void onNovelItemClick(Novel item) {
        NavController controller = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);

        Bundle bundle = new Bundle();
        bundle.putParcelable(Ranobe.KEY_NOVEL, item);
        controller.navigate(R.id.author_fragment_to_details, bundle);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
