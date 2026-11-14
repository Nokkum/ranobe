package org.ranobe.ranobe.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.ranobe.ranobe.databinding.SheetAccountOptionsBinding;

// What an account row offers once there is a session to manage: open the site, or sign out of it. The same sheet
// serves every account; it only reports the choice (with the account it was opened for) to the fragment that showed
// it, as a child fragment, and that fragment does the work.
public class AccountOptions extends BottomSheetDialogFragment {
    public static final String TAG = "AccountOptions";
    private static final String ARG_ACCOUNT = "account";
    private static final String ARG_TITLE = "title";
    private static final String ARG_STATUS = "status";
    private static final String ARG_OPEN = "open";
    private static final String ARG_OPEN_SUB = "open_sub";
    private static final String ARG_SIGN_OUT = "sign_out";
    private static final String ARG_SIGN_OUT_SUB = "sign_out_sub";

    public interface Listener {
        void onOpenAccount(String account);

        void onSignOutOfAccount(String account);
    }

    public static AccountOptions newInstance(String account, @StringRes int title, String status,
                                             @StringRes int open, @StringRes int openSub,
                                             @StringRes int signOut, @StringRes int signOutSub) {
        AccountOptions sheet = new AccountOptions();
        Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT, account);
        args.putInt(ARG_TITLE, title);
        args.putString(ARG_STATUS, status);
        args.putInt(ARG_OPEN, open);
        args.putInt(ARG_OPEN_SUB, openSub);
        args.putInt(ARG_SIGN_OUT, signOut);
        args.putInt(ARG_SIGN_OUT_SUB, signOutSub);
        sheet.setArguments(args);
        return sheet;
    }

    public AccountOptions() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return SheetAccountOptionsBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            dismissAllowingStateLoss();
            return;
        }
        SheetAccountOptionsBinding binding = SheetAccountOptionsBinding.bind(view);
        String account = args.getString(ARG_ACCOUNT, "");
        String status = args.getString(ARG_STATUS);
        binding.title.setText(args.getInt(ARG_TITLE));
        binding.status.setText(status);
        binding.status.setVisibility(status == null || status.isEmpty() ? View.GONE : View.VISIBLE);
        binding.openTitle.setText(args.getInt(ARG_OPEN));
        binding.openSub.setText(args.getInt(ARG_OPEN_SUB));
        binding.signOutTitle.setText(args.getInt(ARG_SIGN_OUT));
        binding.signOutSub.setText(args.getInt(ARG_SIGN_OUT_SUB));
        binding.optionOpen.setOnClickListener(v -> choose(account, true));
        binding.optionSignOut.setOnClickListener(v -> choose(account, false));
    }

    private void choose(String account, boolean open) {
        Listener listener = getParentFragment() instanceof Listener ? (Listener) getParentFragment() : null;
        dismissAllowingStateLoss();
        if (listener == null) return;
        if (open) listener.onOpenAccount(account);
        else listener.onSignOutOfAccount(account);
    }

    // two short rows: open the sheet fully instead of at the default peek height
    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
