package org.ranobe.ranobe.ui.details;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.config.RanobeSettings;
import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.databinding.FragmentDetailsBinding;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.sources.Source;
import org.ranobe.ranobe.sources.SourceManager;
import org.ranobe.ranobe.ui.chapters.Chapters;
import org.ranobe.ranobe.ui.details.viewmodel.DetailsViewModel;
import org.ranobe.ranobe.ui.error.Error;
import org.ranobe.ranobe.ui.history.viewmodel.HistoryViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Details extends Fragment {
    private static final int SUMMARY_LINES = 6;
    private static final int TITLE_LINES = 3;
    private static final long TITLE_ANIMATION_MS = 400L;
    private FragmentDetailsBinding binding;
    private DetailsViewModel viewModel;
    private HistoryViewModel historyViewModel;

    private Novel novel;
    private boolean inLibrary = false;
    private boolean summaryExpanded = false;
    private boolean titleExpanded = false;
    // while the title is open, the text column is pinned in place and the backdrop kept at its size
    private boolean titlePinned = false;
    private ValueAnimator titleAnimator;
    private String shownCover;

    public Details() {
        // Required
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            novel = getArguments().getParcelable(Ranobe.KEY_NOVEL);
        }
        viewModel = new ViewModelProvider(requireActivity()).get(DetailsViewModel.class);
        historyViewModel = new ViewModelProvider(requireActivity()).get(HistoryViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDetailsBinding.inflate(inflater, container, false);
        setUpListeners();
        showPreview();
        setUpObservers();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        // the chapter list / reader go through the current source; re-pin it when coming back
        // to this page after another novel's details were opened on top of it
        if (novel != null && novel.sourceId > 0)
            RanobeSettings.get().setCurrentSource(novel.sourceId).save();
    }

    // browse / search / library already hand over the title and cover (usually in Glide's
    // memory cache), so show those while the full details load
    private void showPreview() {
        if (novel == null) return;
        if (novel.name != null) {
            binding.novelName.setText(novel.name);
            updateTitleToggle();
        }
        if (novel.cover != null) loadCover(novel.cover);
    }

    private void loadCover(String cover) {
        shownCover = cover;
        Glide.with(binding.novelCover.getContext()).load(cover).centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade()).into(binding.novelCover);
        Glide.with(binding.novelCover.getContext()).load(cover).centerCrop().into(binding.novelCoverHeader);
    }

    private void setUpListeners() {
        binding.readChapter.setOnClickListener(v -> navigateToChapterList());
        binding.addToLib.setOnClickListener(v -> toggleLibrary());
        binding.share.setOnClickListener(v -> shareLink());
        binding.back.setOnClickListener(v -> Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main).navigateUp());
        // a long title is cut off after a few lines; tapping it shows all of it, and tapping again folds it
        titleExpanded = false;
        titlePinned = false;
        binding.novelName.setMaxLines(TITLE_LINES);
        binding.novelName.setOnClickListener(v -> toggleTitle());
        binding.summaryToggle.setOnClickListener(v -> {
            summaryExpanded = !summaryExpanded;
            binding.summary.setMaxLines(summaryExpanded ? Integer.MAX_VALUE : SUMMARY_LINES);
            binding.summaryToggle.setText(summaryExpanded ? R.string.show_less : R.string.show_more);
        });
        binding.progress.show();

        // backdrop fades into the page background so the header blends in
        int surface = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorSurface);
        binding.headerScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ColorUtils.setAlphaComponent(surface, 0x66), surface}));
    }

    private void shareLink() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TITLE, String.format(Locale.getDefault(), "Read %s light novel for free on Ranobe %s or visit %s", novel.name, Ranobe.RANOBE_GITHUB_LINK, novel.url));
        sendIntent.putExtra(Intent.EXTRA_TEXT, novel.url);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
    }

    private void setUpObservers() {
        viewModel.getError().observe(getViewLifecycleOwner(), this::setUpError);
        viewModel.getDetails(novel).observe(getViewLifecycleOwner(), this::setUpUi);
        historyViewModel.getLastReadByNovel(novel.url).observe(getViewLifecycleOwner(), readHistory ->
                binding.readChapter.setText(readHistory != null ? R.string.continue_reading : R.string.start_reading));
        RanobeDatabase.database().novels().getByUrl(novel.url).observe(getViewLifecycleOwner(), saved -> {
            inLibrary = saved != null;
            binding.addToLib.setIconResource(inLibrary ? R.drawable.ic_favorite : R.drawable.ic_add_to_lib);
            binding.addToLib.setContentDescription(getString(inLibrary ? R.string.remove_from_library : R.string.add_to_library));
        });
    }

    private void setUpError(String error) {
        binding.progress.hide();
        Error.navigateToErrorFragment(requireActivity(), error);
    }

    private void navigateToChapterList() {
        if (novel == null) return;
        Bundle bundle = new Bundle();
        bundle.putParcelable(Ranobe.KEY_NOVEL, novel);
        Chapters chapters = new Chapters();
        chapters.setArguments(bundle);
        chapters.show(getParentFragmentManager(), "chapters-sheet");
    }

    // Opens or folds the title with a short animation.
    private void toggleTitle() {
        if (binding == null || (titleAnimator != null && titleAnimator.isRunning())) return;
        TextView title = binding.novelName;
        boolean expanding = !titleExpanded;
        titleExpanded = expanding;

        int from = title.getHeight();
        int to = titleHeight(title, expanding ? Integer.MAX_VALUE : TITLE_LINES);
        if (title.getWidth() == 0 || from == to) {
            title.setMaxLines(expanding ? Integer.MAX_VALUE : TITLE_LINES);
            return;
        }

        pinTitle();
        // lay out the whole title now; the height decides how much of it shows while it moves
        title.setMaxLines(Integer.MAX_VALUE);
        ViewGroup.LayoutParams params = title.getLayoutParams();
        params.height = from;
        title.setLayoutParams(params);

        titleAnimator = ValueAnimator.ofInt(from, to);
        titleAnimator.setDuration(TITLE_ANIMATION_MS);
        titleAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        titleAnimator.addUpdateListener(animation -> {
            if (binding == null) return;
            params.height = (int) animation.getAnimatedValue();
            binding.novelName.setLayoutParams(params);
        });
        titleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishTitleAnimation(expanding);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                finishTitleAnimation(expanding);
            }
        });
        titleAnimator.start();
    }

    private void finishTitleAnimation(boolean expanded) {
        if (binding == null) return;
        binding.novelName.setMaxLines(expanded ? Integer.MAX_VALUE : TITLE_LINES);
        ViewGroup.LayoutParams params = binding.novelName.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.novelName.setLayoutParams(params);
        if (!expanded) unpinTitle();
    }

    // How tall the title is when limited to this many lines.
    private static int titleHeight(TextView title, int lines) {
        int before = title.getMaxLines();
        title.setMaxLines(lines);
        title.measure(
                View.MeasureSpec.makeMeasureSpec(title.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = title.getMeasuredHeight();
        title.setMaxLines(before);
        return height;
    }

    // The cover and the text column sit at the bottom of their row.
    private void pinTitle() {
        if (titlePinned) return;
        pinTop(binding.novelCoverLayout);
        pinTop(binding.infoColumn);
        binding.coverRow.setGravity(Gravity.TOP);

        int headerHeight = binding.headerLayout.getHeight();
        setHeight(binding.novelCoverHeader, headerHeight);
        setHeight(binding.headerScrim, headerHeight);
        titlePinned = true;
    }

    private void unpinTitle() {
        if (!titlePinned) return;
        setTopMargin(binding.novelCoverLayout, 0);
        setTopMargin(binding.infoColumn, 0);
        binding.coverRow.setGravity(Gravity.BOTTOM);

        setHeight(binding.novelCoverHeader, ViewGroup.LayoutParams.MATCH_PARENT);
        setHeight(binding.headerScrim, ViewGroup.LayoutParams.MATCH_PARENT);
        titlePinned = false;
    }

    // Holds a view where it is by giving it a top margin equal to its current top.
    private static void pinTop(View view) {
        setTopMargin(view, view.getTop());
    }

    private static void setTopMargin(View view, int margin) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.topMargin = margin;
        view.setLayoutParams(params);
    }

    private static void setHeight(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }

    /** The title only reacts to taps while it is cut off (or open), so a short title is not a dead button. */
    private void updateTitleToggle() {
        binding.novelName.post(() -> {
            if (binding == null) return;
            Layout layout = binding.novelName.getLayout();
            boolean cut = layout != null && layout.getLineCount() > 0
                    && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
            binding.novelName.setClickable(cut || titleExpanded);
        });
    }

    private void setUpUi(Novel novel) {
        this.novel = novel;
        // skip reloading (and re-fading) the cover the preview already shows
        if (novel.cover != null && !novel.cover.equals(shownCover)) loadCover(novel.cover);
        binding.novelName.setText(novel.name);
        updateTitleToggle();
        binding.summary.setText(novel.summary);
        addChips(novel.genres);

        boolean hasStatus = novel.status != null && !novel.status.trim().isEmpty();
        binding.status.setText(hasStatus ? novel.status.trim() : null);
        binding.status.setVisibility(hasStatus ? View.VISIBLE : View.GONE);

        boolean hasAuthors = novel.authors != null && !novel.authors.isEmpty();
        binding.authors.setText(hasAuthors ? String.join(", ", novel.authors) : null);
        binding.authors.setVisibility(hasAuthors ? View.VISIBLE : View.GONE);

        binding.rating.setText(metaLine(novel));

        // only offer "Show more" when the synopsis is actually cut off
        binding.summary.post(() -> {
            if (binding == null) return;
            Layout layout = binding.summary.getLayout();
            boolean cut = layout != null && layout.getLineCount() > 0
                    && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
            binding.summaryToggle.setVisibility(cut || summaryExpanded ? View.VISIBLE : View.GONE);
        });

        binding.progress.hide();
        binding.progress.setVisibility(View.GONE);
    }

    // "★ 4.3 · 2012 · Source": only the parts this source provides
    private String metaLine(Novel novel) {
        List<String> parts = new ArrayList<>();
        if (novel.rating > 0) parts.add(String.format(Locale.getDefault(), "★ %.1f", novel.rating));
        if (novel.year > 0) parts.add(String.valueOf(novel.year));
        Source source = SourceManager.getSource(novel.sourceId);
        if (source != null) parts.add(source.metadata().name);
        return String.join(" · ", parts);
    }

    private void addChips(List<String> genres) {
        binding.genresLayout.removeAllViews();
        if (genres == null) return;

        LayoutInflater inflater = LayoutInflater.from(binding.genresLayout.getContext());
        for (String genre : genres) {
            if (genre.trim().isEmpty()) continue;
            Chip chip = (Chip) inflater.inflate(R.layout.item_genre_chip, binding.genresLayout, false);
            chip.setText(genre.trim());
            binding.genresLayout.addView(chip);
        }
    }

    private void toggleLibrary() {
        if (novel == null) return;
        Novel target = novel;
        if (inLibrary) {
            RanobeDatabase.databaseExecutor.execute(() -> RanobeDatabase.database().novels().delete(target.url));
            Snackbar.make(binding.getRoot(), R.string.removed_from_library, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.undo, v -> RanobeDatabase.databaseExecutor.execute(() -> RanobeDatabase.database().novels().save(target)))
                    .show();
        } else {
            RanobeDatabase.databaseExecutor.execute(() -> RanobeDatabase.database().novels().save(target));
            Snackbar.make(binding.getRoot(), R.string.added_to_library, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (titleAnimator != null) titleAnimator.removeAllListeners();
        if (titleAnimator != null) titleAnimator.cancel();
        titleAnimator = null;
        binding = null;
        shownCover = null;
    }
}
