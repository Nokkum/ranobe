package org.ranobe.ranobe.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;

import org.ranobe.ranobe.R;
import org.ranobe.ranobe.service.DownloadService;

// Lets the user pass WTR-LAB's human check (Cloudflare Turnstile) in a WebView. The app's own requests
// cannot solve it. The page's own script verifies the user; the app only shares the WebView cookies.
public class WtrLabVerifyActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    private static final String SITE_HOST = "wtr-lab.com";
    private static final String HOME_URL = "https://wtr-lab.com/en";

    private WebView webView;
    private ProgressBar progress;

    public static Intent intent(Context context, @Nullable String chapterUrl) {
        return new Intent(context, WtrLabVerifyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_URL, chapterUrl);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wtr_lab_sign_in);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.wtr_lab_sign_in_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.wtr_lab_sign_in_toolbar);
        toolbar.setTitle(R.string.wtr_lab_verify_title);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationIconTint(MaterialColors.getColor(
                toolbar,
                com.google.android.material.R.attr.colorOnSurface
        ));
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.inflateMenu(R.menu.menu_wtr_lab_verify);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.verify_done) {
                // Make sure anything the check stored is on disk before the app retries.
                CookieManager.getInstance().flush();
                // Chapters that stopped at the check continue now. If the check did not take, the first
                // one hits it again and the downloads pause again.
                int resumed = DownloadService.resumeAfterChallenge(getApplicationContext());
                if (resumed > 0) {
                    Toast.makeText(this, getString(R.string.wtr_lab_downloads_resumed, resumed), Toast.LENGTH_SHORT).show();
                }
                setResult(RESULT_OK);
                finish();
                return true;
            }
            return false;
        });

        webView = findViewById(R.id.wtr_lab_sign_in_web_view);
        progress = findViewById(R.id.wtr_lab_sign_in_progress);
        configureWebView();
        webView.loadUrl(startUrl());
        Toast.makeText(this, R.string.wtr_lab_verify_hint, Toast.LENGTH_LONG).show();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private String startUrl() {
        String raw = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_URL);
        Uri uri = raw == null ? null : Uri.parse(raw);
        // The chapter page is where the site shows its check. Drop our own ?chapter_id= query.
        if (isWtrLabUrl(uri)) return uri.buildUpon().clearQuery().fragment(null).build().toString();
        return HOME_URL;
    }

    private void configureWebView() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isWtrLabUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isWtrLabUrl(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isWtrLabUrl(Uri.parse(url))) CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });
    }

    private boolean isWtrLabUrl(@Nullable Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        int port = uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && SITE_HOST.equalsIgnoreCase(host)
                && uri.getUserInfo() == null
                && (port == -1 || port == 443);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
