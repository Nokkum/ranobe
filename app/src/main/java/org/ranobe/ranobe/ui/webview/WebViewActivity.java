package org.ranobe.ranobe.ui.webview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.databinding.ActivityWebviewBinding;
import org.ranobe.ranobe.network.Cloudflare;
import org.ranobe.ranobe.network.HttpClient;
import org.ranobe.ranobe.network.WebViewCookieJar;

/**
 * A site opened in a real browser view. It shares cookies with the app's own requests, so whatever
 * is done here (a Cloudflare check passed, an account signed in to, a chapter unlocked) counts for the
 * app too.
 * <p>
 * Opened by {@link Verify} it is there to pass a Cloudflare check by hand, and closes itself as soon
 * as the clearance cookie shows up. Opened by {@link Browse} it is a page to use, and stays until the
 * user taps Done.
 */
public class WebViewActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_AGENT = "agent";
    private static final String EXTRA_SIGN_IN_COOKIES = "sign_in_cookies";
    private static final String EXTRA_SIGN_IN_URLS = "sign_in_urls";

    private ActivityWebviewBinding binding;
    private String url;
    private String initialClearance;
    // a page to use rather than a check to pass: no closing by itself, except after a sign-in made here (see SignInWatch)
    private boolean browsing;
    private SignInWatch signIn;
    private String[] signInCookies;
    private String[] signInUrls;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityWebviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) {
            finish();
            return;
        }
        initialClearance = WebViewCookieJar.get(url, Cloudflare.CLEARANCE_COOKIE);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        browsing = title != null;
        signInCookies = getIntent().getStringArrayExtra(EXTRA_SIGN_IN_COOKIES);
        signInUrls = getIntent().getStringArrayExtra(EXTRA_SIGN_IN_URLS);
        if (browsing && signInCookies != null && signInCookies.length > 0) {
            // what is there when the page opens decides: a sign-in that exists already is not one made here
            signIn = SignInWatch.opened();
            signIn.baseline(hasSignIn());
        }
        binding.toolbar.setTitle(browsing ? title : getString(R.string.cloudflare_verify_title));
        // A page to use gets the whole screen: its title says what it is, and Done says how to leave. Only the
        // check explains itself.
        if (browsing) binding.hint.setVisibility(View.GONE);
        binding.toolbar.setSubtitle(Uri.parse(url).getHost());
        binding.toolbar.setNavigationOnClickListener(v -> finishVerified());
        binding.done.setOnClickListener(v -> finishVerified());

        WebView web = binding.web;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // the agent the caller's own requests use comes first, a clearance is tied to it
        String agent = getIntent().getStringExtra(EXTRA_AGENT);
        if (agent == null) agent = Cloudflare.userAgentFor(url);
        settings.setUserAgentString(agent != null ? agent : HttpClient.userAgent());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                binding.progress.setProgressCompat(progress, true);
                binding.progress.setVisibility(progress < 100 ? View.VISIBLE : View.INVISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                binding.progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (browsing) {
                    if (signIn != null && signIn.shouldClose(hasSignIn())) finishVerified();
                    return;
                }
                // the check is passed once a fresh clearance is set and the real page shows
                String clearance = WebViewCookieJar.get(url, Cloudflare.CLEARANCE_COOKIE);
                String title = view.getTitle();
                boolean stillChecking = title != null && title.contains("Just a moment");
                if (clearance != null && !clearance.equals(initialClearance) && !stillChecking) {
                    finishVerified();
                }
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.web.canGoBack()) binding.web.goBack();
                else finishVerified();
            }
        });
        if (savedInstanceState == null) web.loadUrl(url);
        else web.restoreState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.web.saveState(outState);
    }

    // Whether one of the sign-in cookies is set at any of the addresses to look at (the page's own, by default).
    private boolean hasSignIn() {
        String[] urls = signInUrls != null && signInUrls.length > 0 ? signInUrls : new String[]{url};
        for (String address : urls) {
            for (String name : signInCookies) {
                String value = WebViewCookieJar.get(address, name);
                if (value != null && !value.isEmpty()) return true;
            }
        }
        return false;
    }

    // the caller retries either way, a clearance may have been set without the page telling us
    private void finishVerified() {
        CookieManager.getInstance().flush();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (binding != null) {
            binding.web.stopLoading();
            binding.web.destroy();
        }
        super.onDestroy();
    }

    /**
     * A page to work in: where to go, what to call the screen, and the browser agent to show the site
     * (null for the app's default).
     */
    public static class Page {
        private final String url;
        private final String title;
        private final String userAgent;
        // set by closeOnSignIn
        private String[] signInCookies;
        private String[] signInUrls;

        public Page(String url, String title, @Nullable String userAgent) {
            this.url = url;
            this.title = title;
            this.userAgent = userAgent;
        }

        /**
         * Has the page close itself once, when a sign-in turns up in one of these cookies (read at these addresses; the
         * page's own when none are given). Not when one was there as the page opened, nor a second time: see
         * {@link SignInWatch}.
         */
        public Page closeOnSignIn(@Nullable String[] cookieNames, @Nullable String[] cookieUrls) {
            this.signInCookies = cookieNames;
            this.signInUrls = cookieUrls;
            return this;
        }
    }

    /**
     * Opens a {@link Page} to use; the result is true once the user taps Done, so the caller can try
     * again with whatever was done there.
     */
    public static class Browse extends ActivityResultContract<Page, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Page page) {
            return new Intent(context, WebViewActivity.class)
                    .putExtra(EXTRA_URL, page.url)
                    .putExtra(EXTRA_TITLE, page.title)
                    .putExtra(EXTRA_SIGN_IN_COOKIES, page.signInCookies)
                    .putExtra(EXTRA_SIGN_IN_URLS, page.signInUrls)
                    .putExtra(EXTRA_AGENT, page.userAgent);
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }

    /**
     * Opens the url, the result tells whether the check was passed.
     */
    public static class Verify extends ActivityResultContract<String, Boolean> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, String url) {
            return new Intent(context, WebViewActivity.class).putExtra(EXTRA_URL, url);
        }

        @Override
        public Boolean parseResult(int resultCode, @Nullable Intent intent) {
            return resultCode == RESULT_OK;
        }
    }
}
