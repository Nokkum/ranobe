package org.ranobe.ranobe.ui.reader;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.config.RanobeSettings;
import org.ranobe.ranobe.databinding.ActivityReaderBinding;
import org.ranobe.ranobe.models.Chapter;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;
import org.ranobe.ranobe.models.ReaderTheme;
import org.ranobe.ranobe.sources.ChallengeRequiredException;
import org.ranobe.ranobe.sources.ChapterLockedException;
import org.ranobe.ranobe.sources.en.WtrLab;
import org.ranobe.ranobe.ui.chapters.Chapters;
import org.ranobe.ranobe.ui.chapters.viewmodel.ChaptersViewModel;
import org.ranobe.ranobe.ui.history.viewmodel.HistoryViewModel;
import org.ranobe.ranobe.ui.reader.adapter.PageAdapter;
import org.ranobe.ranobe.ui.reader.sheet.CustomizeReader;
import org.ranobe.ranobe.ui.reader.viewmodel.ReaderViewModel;
import org.ranobe.ranobe.ui.settings.WtrLabSignInActivity;
import org.ranobe.ranobe.ui.settings.WtrLabVerifyActivity;
import org.ranobe.ranobe.util.ChapterLabel;
import org.ranobe.ranobe.util.ListUtils;
import org.ranobe.ranobe.util.ReaderProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReaderActivity extends AppCompatActivity implements CustomizeReader.OnOptionSelection, Chapters.OnChapterPicked {
    // start loading the next chapter while this many paragraphs are still ahead
    private static final int PREFETCH_ROWS = 12;
    private static final long CHROME_ANIMATION_MS = 180;
    private static final String CHAPTER_LIST_TAG = "chapter-list-sheet";

    private ActivityReaderBinding binding;
    private PageAdapter adapter;
    private ReaderViewModel readerViewModel;
    private HistoryViewModel historyViewModel;
    private List<Chapter> chapterItems = new ArrayList<>();
    private Chapter initialChapter;
    private Chapter failedChapter;
    private Novel currentNovel;
    private ChaptersViewModel chaptersViewModel;
    private boolean chaptersNeedRetry = false;
    private ReadHistory readHistory;
    private String openedChapterUrl;
    // index in chapterItems of the last chapter appended to the page; -1 until the chapter list arrives
    private int lastLoadedIndex = -1;
    private String lastLoadedUrl;
    private String visibleChapterUrl;
    // the chapter the menu describes: the one on screen, or the one being jumped to while it loads
    private Chapter currentChapter;
    // bumped for every chapter request, so a slow answer for a chapter the reader has left is ignored
    private int loadGeneration = 0;
    // while the progress bar is dragged the page follows it, not the other way round
    private boolean isSeeking = false;
    private int seekStart = -1;
    private int seekRows = 0;
    // after a jump to a chapter already on the page, that chapter stays current until the reader scrolls: at
    // the end of the page a short chapter can't be brought to the top, and tracking would hand back the old one
    private boolean jumpPinned = false;
    private boolean isLoading = false;
    private boolean endShown = false;
    private boolean chromeVisible = true;
    private LinearLayoutManager layoutManager;
    private boolean isVolumeKeyScroll = false;
    private int volumeScrollSpeed = Ranobe.DEFAULT_VOLUME_SCROLL_SPEED;
    private long lastVolumeScrollTime = 0;
    private final ActivityResultLauncher<Intent> wtrLabSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) retryAfterWtrLabSignIn();
            }
    );
    private final ActivityResultLauncher<Intent> wtrLabVerifyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) retryFailedChapter();
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityReaderBinding.inflate(getLayoutInflater());
        WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(getWindow(), binding.getRoot());
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reader_view), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        @SuppressWarnings("deprecation")
        Novel currentNovel = getIntent().getParcelableExtra(Ranobe.KEY_NOVEL);
        @SuppressWarnings("deprecation")
        Chapter chapter = getIntent().getParcelableExtra(Ranobe.KEY_CHAPTER);
        @SuppressWarnings("deprecation")
        ReadHistory history = getIntent().getParcelableExtra(Ranobe.KEY_READ_HISTORY);
        this.currentNovel = currentNovel;
        this.initialChapter = chapter;
        readHistory = history;
        openedChapterUrl = chapter.url;
        readerViewModel = new ViewModelProvider(this).get(ReaderViewModel.class);
        historyViewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        chaptersViewModel = new ViewModelProvider(this).get(ChaptersViewModel.class);
        // chapter requests go through the current source, so pin it to the novel being read
        int sourceId = readHistory != null ? readHistory.sourceId : currentNovel != null ? currentNovel.sourceId : 0;
        if (sourceId > 0) RanobeSettings.get().setCurrentSource(sourceId).save();

        isVolumeKeyScroll = Ranobe.isVolumeKeyScrollEnabled();
        volumeScrollSpeed = Ranobe.getVolumeScrollSpeed();

        setUpChrome(currentNovel);
        setUpPageList();
        applyReaderTheme(Ranobe.themes.get(Ranobe.getReaderTheme(this)));

        // the opened chapter loads right away; the full list is only needed to find the next one
        currentChapter = chapter;
        bindChapterInfo(chapter);
        requestChapter(chapter);
        readerViewModel.getError().observe(this, this::setChapterError);
        chaptersViewModel.getChapters(currentNovel).observe(this, this::setChapters);
        chaptersViewModel.getError().observe(this, this::setError);
    }

    private void setUpChrome(Novel novel) {
        binding.topBar.setNavigationOnClickListener(v -> finish());
        String novelName = novel != null && novel.name != null ? novel.name
                : readHistory != null ? readHistory.novelName : null;
        binding.topBar.setTitle(novelName);
        // opened from history the novel only has its url; the chapter list sheet titles itself with the name
        if (novel != null && novel.name == null) novel.name = novelName;
        setUpBottomBar();
    }

    // the reading menu: chapter, previous / progress / next, and settings / chapter list
    private void setUpBottomBar() {
        binding.settingsButton.setOnClickListener(v -> setUpCustomizeReader());
        binding.chapterListButton.setOnClickListener(v -> openChapterList());
        binding.previousChapter.setOnClickListener(v -> openAdjacentChapter(-1));
        binding.nextChapter.setOnClickListener(v -> openAdjacentChapter(1));
        // both need the chapter list, which arrives after the chapter itself
        binding.previousChapter.setEnabled(false);
        binding.nextChapter.setEnabled(false);

        binding.readingProgress.setLabelFormatter(value -> String.format(Locale.getDefault(), "%d%%", (int) value));
        binding.readingProgress.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) seekTo(value);
        });
        binding.readingProgress.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                isSeeking = true;
                captureSeekChapter();
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                isSeeking = false;
                seekStart = -1;
                updateReadingProgress();
                maybeLoadNext();
            }
        });
    }

    private void setUpPageList() {
        adapter = new PageAdapter();
        layoutManager = new LinearLayoutManager(this);
        binding.pageList.setLayoutManager(layoutManager);
        binding.pageList.setAdapter(adapter);
        // theme/font changes rebind the visible rows; the default cross-fade on all of them at once glitches
        if (binding.pageList.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) binding.pageList.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.pageList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) jumpPinned = false;
                if (dy != 0 && chromeVisible && !isSeeking) setChromeVisible(false);
                updateVisibleChapter();
                updateReadingProgress();
                if (dy > 0) maybeLoadNext();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1)) loadNextChapter();
            }
        });

        // a single tap on the page toggles the bars; long presses stay free for text selection
        GestureDetectorCompat tapDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                setChromeVisible(!chromeVisible);
                return true;
            }
        });
        binding.pageList.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                tapDetector.onTouchEvent(e);
                return false;
            }
        });
    }

    private void setChromeVisible(boolean visible) {
        chromeVisible = visible;
        animateBar(binding.topBar, visible, -1);
        animateBar(binding.bottomBar, visible, 1);
        if (visible) updateReadingProgress();
    }

    private void animateBar(View bar, boolean visible, int direction) {
        bar.animate().cancel();
        if (visible) {
            bar.setVisibility(View.VISIBLE);
            bar.animate().translationY(0).alpha(1f).setDuration(CHROME_ANIMATION_MS).setListener(null).start();
        } else {
            float distance = direction * (bar.getHeight() > 0 ? bar.getHeight() : 200);
            bar.animate().translationY(distance).alpha(0f).setDuration(CHROME_ANIMATION_MS)
                    .withEndAction(() -> bar.setVisibility(View.INVISIBLE)).start();
        }
    }

    // every chapter load goes through here; the number lets setChapter drop an answer for a chapter
    // the reader has since jumped away from, which would otherwise land after the new one
    private void requestChapter(Chapter chapter) {
        final int generation = ++loadGeneration;
        isLoading = true;
        failedChapter = chapter;
        binding.progress.show();
        readerViewModel.getChapter(chapter).observe(this, loaded -> {
            if (generation == loadGeneration) setChapter(loaded);
        });
    }

    // Moves the reader to a chapter. One that's already on the page is scrolled to; any other starts
    // the page over from it (the page only ever grows downwards, so there is nothing to scroll to).
    private void openChapter(Chapter target) {
        int position = adapter.positionOfChapter(target.url);
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0);
            showChapter(adapter.chapterAt(position));
            jumpPinned = true;
            binding.pageList.post(this::updateReadingProgress);
            return;
        }
        jumpPinned = false;
        adapter.clear();
        layoutManager.scrollToPositionWithOffset(0, 0);
        endShown = false;
        lastLoadedUrl = null;
        visibleChapterUrl = null;
        // a saved position belongs to the chapter the reader was opened with, not to this one
        readHistory = null;
        openedChapterUrl = target.url;
        lastLoadedIndex = indexOf(target.url);
        currentChapter = target;
        binding.topBar.setSubtitle(target.name);
        bindChapterInfo(target);
        binding.readingProgress.setValue(0);
        requestChapter(target);
    }

    private void openAdjacentChapter(int step) {
        if (currentChapter == null) return;
        int current = indexOf(currentChapter.url);
        int target = current + step;
        if (current < 0 || target < 0 || target >= chapterItems.size()) return;
        openChapter(chapterItems.get(target));
    }

    private void openChapterList() {
        if (currentNovel == null || chapterItems.isEmpty()) return;
        if (getSupportFragmentManager().findFragmentByTag(CHAPTER_LIST_TAG) != null) return;
        Bundle bundle = new Bundle();
        bundle.putParcelable(Ranobe.KEY_NOVEL, currentNovel);
        Chapters sheet = new Chapters();
        sheet.setArguments(bundle);
        sheet.show(getSupportFragmentManager(), CHAPTER_LIST_TAG);
    }

    @Override
    public void onChapterPicked(Chapter chapter) {
        openChapter(chapter);
        // land on the chapter rather than on the menu
        setChromeVisible(false);
    }

    // the menu's number and title lines, and whether there is a chapter before and after
    private void bindChapterInfo(Chapter chapter) {
        int index = indexOf(chapter.url);
        ChapterLabel label = ChapterLabel.of(chapter.id, index, chapter.name);
        binding.chapterNumber.setText(getString(R.string.reader_chapter_number, label.number));
        binding.chapterNumber.setVisibility(label.number.isEmpty() ? View.GONE : View.VISIBLE);
        binding.chapterTitle.setText(label.title);
        binding.chapterTitle.setVisibility(label.title.isEmpty() ? View.GONE : View.VISIBLE);
        binding.previousChapter.setEnabled(index > 0);
        binding.nextChapter.setEnabled(index >= 0 && index < chapterItems.size() - 1);
    }

    // Moves the progress bar to where the reader is in the chapter on screen.
    private void updateReadingProgress() {
        if (isSeeking || !chromeVisible || adapter.getItemCount() == 0) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        if (adapter.chapterAt(first) == null) return;
        int index = adapter.indexInChapter(first);
        int start = first - index;
        int rows = adapter.chapterRowCount(start);
        View row = layoutManager.findViewByPosition(first);
        float scrolledOff = row != null && row.getHeight() > 0
                ? (binding.pageList.getPaddingTop() - row.getTop()) / (float) row.getHeight() : 0f;
        boolean endVisible = layoutManager.findLastCompletelyVisibleItemPosition() >= start + rows - 1;
        int percent = ReaderProgress.percent(index, scrolledOff, rows, endVisible);
        if ((int) binding.readingProgress.getValue() != percent) binding.readingProgress.setValue(percent);
    }

    // The chapter on screen and its rows, fixed for the length of a drag so the bar keeps meaning the same chapter.
    private boolean captureSeekChapter() {
        int first = layoutManager.findFirstVisibleItemPosition();
        if (adapter.chapterAt(first) == null) return false;
        int start = first - adapter.indexInChapter(first);
        int rows = adapter.chapterRowCount(start);
        if (rows <= 1) return false;
        seekStart = start;
        seekRows = rows;
        return true;
    }

    private void seekTo(float percent) {
        boolean dragging = isSeeking;
        if (seekStart < 0 && !captureSeekChapter()) return;
        layoutManager.scrollToPositionWithOffset(seekStart + ReaderProgress.rowFor(percent, seekRows), 0);
        // a change that came without a touch (keyboard, screen reader) is a single step
        if (!dragging) seekStart = -1;
    }

    private void setChapters(List<Chapter> items) {
        chapterItems = ListUtils.sortById(items);
        binding.chapterListButton.setEnabled(!chapterItems.isEmpty());
        if (currentChapter != null) bindChapterInfo(currentChapter);
        lastLoadedIndex = indexOf(lastLoadedUrl != null ? lastLoadedUrl : openedChapterUrl);
        prefetchAfter(lastLoadedIndex);
        maybeLoadNext();
    }

    private int indexOf(String url) {
        if (url == null) return -1;
        for (int i = 0; i < chapterItems.size(); i++) {
            if (url.equals(chapterItems.get(i).url)) return i;
        }
        return -1;
    }

    private void setUpCustomizeReader() {
        CustomizeReader sheet = new CustomizeReader(this);
        sheet.show(getSupportFragmentManager(), "customize-sheet");
    }

    private void setChapter(Chapter chapter) {
        isLoading = false;
        failedChapter = null;
        binding.progress.hide();
        int start = adapter.appendChapter(chapter);
        lastLoadedUrl = chapter.url;
        if (!chapterItems.isEmpty()) lastLoadedIndex = indexOf(chapter.url);

        // reopening from history: return to the saved paragraph of the first chapter
        if (start == 0 && readHistory != null && chapter.url.equals(readHistory.url)) {
            int target = start + Math.max(0, Math.min(readHistory.position, adapter.chapterRowCount(start) - 1));
            layoutManager.scrollToPositionWithOffset(target, readHistory.readerOffset);
        }

        binding.pageList.post(this::updateVisibleChapter);
        prefetchAfter(lastLoadedIndex);
    }

    private void prefetchAfter(int index) {
        if (index >= 0 && index + 1 < chapterItems.size()) {
            readerViewModel.prefetch(chapterItems.get(index + 1));
        }
    }

    // tracks the chapter on screen: it's the one that gets marked read and whose position is saved
    private void updateVisibleChapter() {
        if (jumpPinned) return;
        int position = layoutManager.findFirstVisibleItemPosition();
        Chapter chapter = adapter.chapterAt(position);
        if (chapter == null || chapter.url.equals(visibleChapterUrl)) return;
        showChapter(chapter);
    }

    private void showChapter(Chapter chapter) {
        visibleChapterUrl = chapter.url;
        currentChapter = chapter;
        binding.topBar.setSubtitle(chapter.name);
        bindChapterInfo(chapter);
        historyViewModel.markAsRead(chapter);
    }

    private void maybeLoadNext() {
        if (adapter.getItemCount() == 0) return;
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        if (lastVisible >= adapter.getItemCount() - PREFETCH_ROWS) loadNextChapter();
    }

    // pages only cover the screen once a chapter loads, so the window and loader must match the reader theme too
    private void applyReaderTheme(ReaderTheme theme) {
        int background = theme != null ? theme.getBackground()
                : MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorSurface);
        int text = theme != null ? theme.getText()
                : MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurface);
        int indicator = theme != null ? theme.getText()
                : MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        getWindow().getDecorView().setBackgroundColor(background);
        binding.readerView.setBackgroundColor(background);
        binding.progress.setIndicatorColor(indicator);
        binding.progress.setTrackColor(ColorUtils.setAlphaComponent(indicator, 0x33));

        // bars sit on the page, so they take the page colors with a slight tint to stand apart
        int barColor = ColorUtils.blendARGB(background, text, 0.06f);
        Toolbar bar = binding.topBar;
        bar.setBackgroundColor(barColor);
        bar.setTitleTextColor(text);
        bar.setSubtitleTextColor(ColorUtils.setAlphaComponent(text, 0xB3));
        Drawable navigation = bar.getNavigationIcon();
        if (navigation != null) navigation.mutate().setTint(text);
        applyBottomBarTheme(barColor, text);
    }

    private void applyBottomBarTheme(int barColor, int text) {
        binding.bottomBar.setBackgroundColor(barColor);
        binding.chapterNumber.setTextColor(text);
        binding.chapterTitle.setTextColor(ColorUtils.setAlphaComponent(text, 0xB3));

        // a button that can't be used (no previous chapter, list still loading) fades
        ColorStateList content = new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{ColorUtils.setAlphaComponent(text, 0x61), text});
        ColorStateList ripple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(text, 0x1F));
        for (MaterialButton button : new MaterialButton[]{binding.previousChapter, binding.nextChapter,
                binding.settingsButton, binding.chapterListButton}) {
            button.setTextColor(content);
            button.setIconTint(content);
            button.setRippleColor(ripple);
        }

        ColorStateList faint = ColorStateList.valueOf(ColorUtils.setAlphaComponent(text, 0x33));
        binding.readingProgress.setTrackActiveTintList(ColorStateList.valueOf(text));
        binding.readingProgress.setTrackInactiveTintList(faint);
        binding.readingProgress.setThumbTintList(ColorStateList.valueOf(text));
        binding.readingProgress.setHaloTintList(faint);
    }

    private void setError(String msg) {
        if (msg == null || msg.isEmpty()) return;
        if (WtrLab.isSignInRequired(msg)) {
            chaptersNeedRetry = true;
            Snackbar.make(binding.getRoot(), R.string.wtr_lab_signin_needed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.wtr_lab_sign_in, v -> openWtrLabSignIn())
                    .show();
            return;
        }
        Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_LONG).show();
    }

    // a failed chapter load used to leave the spinner running forever
    private void setChapterError(String msg) {
        if (msg == null || msg.isEmpty()) return;
        isLoading = false;
        binding.progress.hide();
        if (ChapterLockedException.isLocked(msg)) {
            Snackbar.make(binding.getRoot(), R.string.chapter_locked, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (ChallengeRequiredException.isChallengeRequired(msg)) {
            Snackbar.make(binding.getRoot(), R.string.wtr_lab_challenge_needed, Snackbar.LENGTH_INDEFINITE)
                    .setTextMaxLines(4)
                    .setAction(R.string.wtr_lab_verify, v -> openWtrLabVerify())
                    .show();
            return;
        }
        boolean signInRequired = WtrLab.isSignInRequired(msg);
        String text = signInRequired
                ? getString(R.string.wtr_lab_signin_needed)
                : getString(R.string.chapter_load_failed_detail, msg);
        Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG)
                .setTextMaxLines(4)
                .setAction(signInRequired ? R.string.wtr_lab_sign_in : R.string.retry, v -> {
                    if (signInRequired) openWtrLabSignIn();
                    else retryFailedChapter();
                })
                .show();
    }

    private void openWtrLabVerify() {
        Chapter chapter = failedChapter != null ? failedChapter : initialChapter;
        wtrLabVerifyLauncher.launch(WtrLabVerifyActivity.intent(this, chapter == null ? null : chapter.url));
    }

    private void openWtrLabSignIn() {
        wtrLabSignInLauncher.launch(new Intent(this, WtrLabSignInActivity.class));
    }

    private void retryAfterWtrLabSignIn() {
        if (chaptersNeedRetry && chaptersViewModel != null && currentNovel != null) {
            chaptersNeedRetry = false;
            chaptersViewModel.getChapters(currentNovel).observe(this, this::setChapters);
        }
        retryFailedChapter();
    }

    private void retryFailedChapter() {
        Chapter chapter = failedChapter;
        if (chapter == null) {
            if (adapter.getItemCount() == 0) {
                chapter = initialChapter;
            } else if (lastLoadedIndex >= 0 && lastLoadedIndex + 1 < chapterItems.size()) {
                chapter = chapterItems.get(lastLoadedIndex + 1);
            }
        }
        if (chapter == null) return;
        requestChapter(chapter);
    }

    @Override
    public void setFontSize(float size) {
        Ranobe.storeReaderFont(this, size);
        adapter.setFontSize(size);
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    @Override
    public void setReaderTheme(String themeName) {
        ReaderTheme theme = Ranobe.themes.get(themeName);
        applyReaderTheme(theme);
        adapter.setTheme(theme);
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        Ranobe.storeReaderTheme(this, themeName);
    }

    @Override
    public void setBionicReading(boolean isBionicReading) {
        Ranobe.setBionicReader(this, isBionicReading);
        adapter.setBionicReading(isBionicReading);
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    @Override
    public void setVolumeKeyScroll(boolean isVolumeKeyScroll) {
        this.isVolumeKeyScroll = isVolumeKeyScroll;
        Ranobe.setVolumeKeyScroll(this, isVolumeKeyScroll);
    }

    @Override
    public void setVolumeScrollSpeed(int speed) {
        this.volumeScrollSpeed = speed;
        Ranobe.setVolumeScrollSpeed(this, speed);
    }

    private void scrollDown() {
        int scrollDistance = getScrollDistance();
        if (!binding.pageList.canScrollVertically(1)) {
            loadNextChapter();
        } else {
            binding.pageList.smoothScrollBy(0, scrollDistance, new LinearInterpolator(), getScrollDuration());
        }
    }

    private void scrollUp() {
        if (!binding.pageList.canScrollVertically(-1)) {
            return;
        }
        int scrollDistance = getScrollDistance();
        binding.pageList.smoothScrollBy(0, -scrollDistance, new LinearInterpolator(), getScrollDuration());
    }

    private int getScrollDistance() {
        int height = binding.pageList.getHeight();
        int effectiveHeight = (height > 0) ? height : getResources().getDisplayMetrics().heightPixels;
        int VOLUME_BTN_SCROLL_PERCENT = 60;
        return effectiveHeight * VOLUME_BTN_SCROLL_PERCENT / 100;
    }

    private int getScrollDuration() {
        switch (volumeScrollSpeed) {
            case 1:
                return 700;
            case 2:
                return 550;
            case 4:
                return 300;
            case 5:
                return 200;
            case 3:
            default:
                return 400;
        }
    }

    private void loadNextChapter() {
        if (isLoading || lastLoadedIndex < 0 || lastLoadedUrl == null) return;
        if (lastLoadedIndex + 1 < chapterItems.size()) {
            requestChapter(chapterItems.get(lastLoadedIndex + 1));
        } else if (!endShown) {
            endShown = true;
            adapter.appendEnd();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVolumeKeyScroll) {
            int action = event.getAction();
            int keyCode = event.getKeyCode();
            long throttle = Math.max(80, getScrollDuration() / 4);
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0 || System.currentTimeMillis() - lastVolumeScrollTime >= throttle) {
                        lastVolumeScrollTime = System.currentTimeMillis();
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            scrollDown();
                        } else {
                            scrollUp();
                        }
                    }
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isVolumeKeyScroll && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isVolumeKeyScroll && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isVolumeKeyScroll = Ranobe.isVolumeKeyScrollEnabled();
        volumeScrollSpeed = Ranobe.getVolumeScrollSpeed();
    }

    public void setShowImages(boolean showImages) {
        Ranobe.setShowImages(this, showImages);
        adapter.setShowImages(showImages);
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    // saved on pause rather than destroy, which isn't guaranteed to run; stores the paragraph
    // within the chapter that's on screen, not the last chapter that happened to be loaded
    @Override
    protected void onPause() {
        super.onPause();
        if (layoutManager == null) return;
        int position = layoutManager.findFirstVisibleItemPosition();
        Chapter chapter = adapter.chapterAt(position);
        if (chapter == null) return;
        View view = layoutManager.findViewByPosition(position);
        int offset = (view != null) ? view.getTop() - binding.pageList.getPaddingTop() : 0;
        historyViewModel.updateReadHistoryPosition(adapter.indexInChapter(position), offset, chapter.url);
    }
}
