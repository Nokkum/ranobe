package org.ranobe.ranobe.ui.settings;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.ranobe.ranobe.BuildConfig;
import org.ranobe.ranobe.R;
import org.ranobe.ranobe.config.Ranobe;
import org.ranobe.ranobe.databinding.FragmentSettingsBinding;
import org.ranobe.ranobe.network.repository.GithubRepo;
import org.ranobe.ranobe.network.WtrLabSession;
import org.ranobe.ranobe.sources.en.WtrLab;
import org.ranobe.ranobe.ui.settings.viewmodel.SettingsViewModel;
import org.ranobe.ranobe.ui.views.GetPro;
import org.ranobe.ranobe.util.BackupManager;
import org.ranobe.ranobe.util.SearchPreferences;
import org.ranobe.ranobe.util.SearchTimeouts;
import org.ranobe.ranobe.worker.ChapterUpdateScheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Settings extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    // the file picker for each backup action; each one knows what to do with the file it returns
    private ActivityResultLauncher<Intent> libraryExportPicker;
    private ActivityResultLauncher<Intent> libraryImportPicker;
    private ActivityResultLauncher<Intent> historyExportPicker;
    private ActivityResultLauncher<Intent> historyImportPicker;

    public Settings() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);
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
        libraryExportPicker = registerPicker(uri -> viewModel.runBackup(BackupManager.Kind.EXPORT_LIBRARY, uri));
        libraryImportPicker = registerPicker(uri -> viewModel.runBackup(BackupManager.Kind.IMPORT_LIBRARY, uri));
        historyExportPicker = registerPicker(uri -> viewModel.runBackup(BackupManager.Kind.EXPORT_HISTORY, uri));
        historyImportPicker = registerPicker(uri -> viewModel.runBackup(BackupManager.Kind.IMPORT_HISTORY, uri));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean isPro = Ranobe.isPro();
        binding.proApp.setVisibility(isPro ? View.GONE : View.VISIBLE);
        binding.editionBadge.setText(isPro ? R.string.edition_pro : R.string.edition_free);
        binding.appVersion.setText(BuildConfig.VERSION_NAME);

        // the row only labels the segmented control below it
        binding.themeModeOption.setClickable(false);
        binding.themeModeToggle.check(themeButtonFor(Ranobe.getThemeMode(requireActivity().getApplicationContext())));
        binding.themeModeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.theme_light) selectTheme(AppCompatDelegate.MODE_NIGHT_NO);
            else if (checkedId == R.id.theme_dark) selectTheme(AppCompatDelegate.MODE_NIGHT_YES);
            else selectTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        });

        binding.chapterUpdatesOption.setOnClickListener(v -> handleChapterUpdatesClick());
        syncChapterUpdatesToggle();
        binding.wtrLabAccountOption.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), WtrLabSignInActivity.class)));
        binding.wtrLabSignOutOption.setOnClickListener(v -> confirmWtrLabSignOut());

        boolean volumeScrollEnabled = Ranobe.isVolumeKeyScrollEnabled();
        binding.volumeScrollOption.setChecked(volumeScrollEnabled);
        binding.scrollSpeedSetting.getRoot().setVisibility(volumeScrollEnabled ? View.VISIBLE : View.GONE);
        binding.volumeScrollOption.setOnClickListener(v -> {
            boolean newState = !Ranobe.isVolumeKeyScrollEnabled();
            Ranobe.setVolumeKeyScroll(requireContext(), newState);
            binding.volumeScrollOption.setChecked(newState);
            binding.scrollSpeedSetting.getRoot().setVisibility(newState ? View.VISIBLE : View.GONE);
        });

        int currentSpeed = Ranobe.getVolumeScrollSpeed();
        binding.scrollSpeedSetting.speedSlider.setValue(currentSpeed);
        binding.scrollSpeedSetting.speedLabel.setText(Ranobe.getSpeedLabel(requireContext(), currentSpeed));
        binding.scrollSpeedSetting.speedSlider.setLabelFormatter(value -> Ranobe.getSpeedLabel(requireContext(), (int) value));
        binding.scrollSpeedSetting.speedSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            int speed = (int) value;
            Ranobe.setVolumeScrollSpeed(requireContext(), speed);
            binding.scrollSpeedSetting.speedLabel.setText(Ranobe.getSpeedLabel(requireContext(), speed));
        });

        setUpSearchTimeout();
        setUpBackup();

        binding.projectLink.setOnClickListener(v -> openLink(Ranobe.RANOBE_GITHUB_LINK));
        binding.musicPlayerLink.setOnClickListener(v -> openLink(Ranobe.MP_LITE_GITHUB_LINK));
        binding.discordLink.setOnClickListener(v -> openLink(Ranobe.DISCORD_INVITE_LINK));
        binding.ranobemLink.setOnClickListener(v -> openLink(Ranobe.RANOBE_M_LINK));
        binding.proApp.setOnClickListener(v -> openLink(Ranobe.RANOBE_PRO_APP_LINK));

        setCurrentThemeMode();

        viewModel.getUpdate().observe(getViewLifecycleOwner(), this::release);
        viewModel.checkForUpdate();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncChapterUpdatesToggle();
        boolean volumeScrollEnabled = Ranobe.isVolumeKeyScrollEnabled();
        binding.volumeScrollOption.setChecked(volumeScrollEnabled);
        binding.scrollSpeedSetting.getRoot().setVisibility(volumeScrollEnabled ? View.VISIBLE : View.GONE);
        int speed = Ranobe.getVolumeScrollSpeed();
        binding.scrollSpeedSetting.speedSlider.setValue(speed);
        binding.scrollSpeedSetting.speedLabel.setText(Ranobe.getSpeedLabel(requireContext(), speed));
        refreshWtrLabStatus();
    }

    // How long Search waits, with nothing found, before it shows "No Results Found": one of four values.
    private void setUpSearchTimeout() {
        // the row only labels the buttons under it
        binding.searchTimeoutOption.setClickable(false);

        // the buttons are the values in SearchTimeouts.OPTIONS, in order
        for (int i = 0; i < SearchTimeouts.OPTIONS.length; i++) {
            View button = binding.searchTimeoutToggle.getChildAt(i);
            ((TextView) button).setText(getString(R.string.search_timeout_seconds, SearchTimeouts.OPTIONS[i]));
        }
        int current = SearchPreferences.getTimeoutSeconds(requireContext());
        binding.searchTimeoutToggle.check(binding.searchTimeoutToggle.getChildAt(SearchTimeouts.indexOf(current)).getId());
        binding.searchTimeoutToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int index = group.indexOfChild(group.findViewById(checkedId));
            if (index >= 0 && index < SearchTimeouts.OPTIONS.length) {
                SearchPreferences.setTimeoutSeconds(requireContext(), SearchTimeouts.OPTIONS[index]);
            }
        });
    }

    private interface OnFilePicked {
        void onPicked(Uri uri);
    }

    private ActivityResultLauncher<Intent> registerPicker(OnFilePicked callback) {
        return registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() != Activity.RESULT_OK || data == null || data.getData() == null) return;
            callback.onPicked(data.getData());
        });
    }

    // Export and Import for the library bookmarks and the reading history.
    private void setUpBackup() {
        // the rows only label the buttons under them
        binding.libraryBackupOption.setClickable(false);
        binding.historyBackupOption.setClickable(false);

        binding.libraryExport.setOnClickListener(v -> pickExportFile(libraryExportPicker, "library"));
        binding.libraryImport.setOnClickListener(v -> pickImportFile(libraryImportPicker));
        binding.historyExport.setOnClickListener(v -> pickExportFile(historyExportPicker, "history"));
        binding.historyImport.setOnClickListener(v -> pickImportFile(historyImportPicker));

        // one job at a time: the buttons wait while one runs
        viewModel.isBackupBusy().observe(getViewLifecycleOwner(), busy -> {
            boolean enabled = !Boolean.TRUE.equals(busy);
            binding.libraryExport.setEnabled(enabled);
            binding.libraryImport.setEnabled(enabled);
            binding.historyExport.setEnabled(enabled);
            binding.historyImport.setEnabled(enabled);
        });
        viewModel.getBackupResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            viewModel.clearBackupResult();
            showBackupResult(result);
        });
    }

    // the system's "save as" screen, with a name to start from
    private void pickExportFile(ActivityResultLauncher<Intent> picker, String what) {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "ranobe-" + what + "-" + day + ".json");
        launchPicker(picker, intent);
    }

    // Any file may be chosen: file managers label a .json file in different ways, and the content is checked
    // before anything is imported.
    private void pickImportFile(ActivityResultLauncher<Intent> picker) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        launchPicker(picker, intent);
    }

    private void launchPicker(ActivityResultLauncher<Intent> picker, Intent intent) {
        try {
            picker.launch(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.backup_no_file_picker, Snackbar.LENGTH_LONG).show();
        }
    }

    // A success is a passing note; a problem stays until it is read.
    private void showBackupResult(BackupManager.Result result) {
        if (!result.succeeded()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.backup_problem_title)
                    .setMessage(problemMessage(result.problem))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        String message;
        switch (result.kind) {
            case EXPORT_LIBRARY:
                message = getResources().getQuantityString(R.plurals.backup_exported_library, result.added, result.added);
                break;
            case EXPORT_HISTORY:
                message = getResources().getQuantityString(R.plurals.backup_exported_history, result.added, result.added);
                break;
            case IMPORT_LIBRARY:
                message = getString(R.string.backup_imported_library, result.added, result.kept);
                break;
            default:
                message = getString(R.string.backup_imported_history, result.added, result.updated, result.kept);
                break;
        }
        if (result.invalid > 0) {
            message += " " + getResources().getQuantityString(R.plurals.backup_skipped_invalid, result.invalid, result.invalid);
        }
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).setTextMaxLines(5).show();
    }

    private int problemMessage(BackupManager.Problem problem) {
        switch (problem) {
            case READ_FAILED:
                return R.string.backup_error_read;
            case WRITE_FAILED:
                return R.string.backup_error_write;
            case NOT_BACKUP:
                return R.string.backup_error_not_backup;
            case FILE_IS_LIBRARY_BACKUP:
                return R.string.backup_error_is_library;
            case FILE_IS_HISTORY_BACKUP:
                return R.string.backup_error_is_history;
            case NEWER_VERSION:
                return R.string.backup_error_newer;
            case TOO_LARGE:
                return R.string.backup_error_too_large;
            default:
                return R.string.backup_error_failed;
        }
    }

    private void confirmWtrLabSignOut() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wtr_lab_sign_out_title)
                .setMessage(R.string.wtr_lab_sign_out_message)
                .setPositiveButton(R.string.wtr_lab_sign_out_confirm, (dialog, i) -> signOutOfWtrLab())
                .setNegativeButton(R.string.cancel, (dialog, i) -> dialog.dismiss())
                .show();
    }

    private void signOutOfWtrLab() {
        binding.wtrLabSignOutOption.setEnabled(false);
        new Thread(() -> {
            WtrLab.endSessionOnServer();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                WtrLabSession.clearLocal();
                if (binding == null || !isAdded()) return;
                binding.wtrLabSignOutOption.setEnabled(true);
                Snackbar.make(binding.getRoot(), R.string.wtr_lab_signed_out, Snackbar.LENGTH_SHORT).show();
                refreshWtrLabStatus();
            });
        }).start();
    }

    // Shows what WTR-LAB says about the session the app's own requests carry (not just the WebView's).
    private void refreshWtrLabStatus() {
        new Thread(() -> {
            WtrLab.SessionStatus status = WtrLab.checkSession();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (binding == null || !isAdded()) return;
                String text;
                if (status.signedIn == null) {
                    text = getString(R.string.wtr_lab_account_sub);
                } else if (!status.signedIn) {
                    text = getString(R.string.wtr_lab_status_signed_out);
                } else if (status.name.isEmpty()) {
                    text = getString(R.string.wtr_lab_status_signed_in);
                } else {
                    text = getString(R.string.wtr_lab_status_signed_in_as, status.name);
                }
                binding.wtrLabAccountOption.setSubtitle(text);
                // Only hide the button when the server clearly says we are signed out.
                binding.wtrLabSignOutOption.setVisibility(
                        Boolean.FALSE.equals(status.signedIn) ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    private void release(GithubRepo.GithubRelease release) {
        if (release.updateAvailable) {
            binding.updateCard.setVisibility(View.VISIBLE);
            binding.versionString.setText(getString(R.string.update_available, release.newReleaseVersion));
            binding.getUpdate.setOnClickListener(v -> openLink(release.newReleaseUrl));
        }
    }

    private void setCurrentThemeMode() {
        int mode = Ranobe.getThemeMode(requireActivity().getApplicationContext());
        if (mode == AppCompatDelegate.MODE_NIGHT_NO)
            binding.themeModeOption.setIcon(R.drawable.ic_theme_mode_light);
        else if (mode == AppCompatDelegate.MODE_NIGHT_YES)
            binding.themeModeOption.setIcon(R.drawable.ic_theme_mode_night);
        else
            binding.themeModeOption.setIcon(R.drawable.ic_theme_mode_auto);
    }

    private int themeButtonFor(int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return R.id.theme_light;
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return R.id.theme_dark;
        return R.id.theme_auto;
    }

    private void selectTheme(int theme) {
        // store first: changing the mode recreates the activity, which re-reads the stored value
        Ranobe.storeThemeMode(requireActivity().getApplicationContext(), theme);
        AppCompatDelegate.setDefaultNightMode(theme);
    }

    private void syncChapterUpdatesToggle() {
        binding.chapterUpdatesOption.setChecked(Ranobe.isPro() && Ranobe.isNewChapterUpdatesEnabled());
    }

    private void handleChapterUpdatesClick() {
        if (!Ranobe.isPro()) {
            new GetPro().show(getParentFragmentManager(), GetPro.TAG);
            return;
        }
        boolean enabled = Ranobe.isNewChapterUpdatesEnabled();
        if (enabled) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Disable Notifications")
                    .setMessage("Disable new chapter notifications?")
                    .setPositiveButton("Disable", (dialog, i) -> {
                        Ranobe.setNewChapterUpdatesEnabled(false);
                        ChapterUpdateScheduler.cancel(requireContext());
                        syncChapterUpdatesToggle();
                        Snackbar.make(binding.getRoot(), "New chapter notifications disabled", Snackbar.LENGTH_SHORT).show();
                    })
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
        syncChapterUpdatesToggle();
        Snackbar.make(binding.getRoot(), "New chapter notifications enabled", Snackbar.LENGTH_SHORT).show();
    }

    private void openLink(String url) {
        requireActivity().startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
        ));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
