package org.ranobe.ranobe.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
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

public class WtrLabSignInActivity extends AppCompatActivity {
    private static final String SITE_HOST = "wtr-lab.com";
    private static final String LOGIN_URL = "https://wtr-lab.com/en/auth/login?callbackUrl=%2Fen";
    private static final String LOGIN_CALLBACK_PATH = "/en";
    private static final String SESSION_CHECK_SCRIPT =
            "(function(){try{var x=new XMLHttpRequest();"
                    + "x.open('GET','/api/auth/get-session',false);"
                    + "x.setRequestHeader('Cache-Control','no-cache');"
                    + "x.send();"
                    + "if(x.status<200||x.status>=300)return false;"
                    + "var s=JSON.parse(x.responseText);"
                    + "return !!(s&&(s.user||s.session))}catch(e){return false}})()";

    private WebView webView;
    private ProgressBar progress;
    private boolean authFlowActive;
    private boolean sessionCheckInProgress;
    private boolean sessionCheckAttempted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wtr_lab_sign_in);

        // Android 16 ignores the edge-to-edge opt-out at targetSdk 36, so pad the content ourselves.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.wtr_lab_sign_in_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.wtr_lab_sign_in_toolbar);
        toolbar.setTitle(R.string.wtr_lab_signin_title);
        toolbar.setNavigationIcon(R.drawable.ic_close);
        toolbar.setNavigationIconTint(MaterialColors.getColor(
                toolbar,
                com.google.android.material.R.attr.colorOnSurface
        ));
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.inflateMenu(R.menu.menu_wtr_lab_sign_in);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.paste_magic_link) {
                pasteMagicLink();
                return true;
            }
            return false;
        });

        webView = findViewById(R.id.wtr_lab_sign_in_web_view);
        progress = findViewById(R.id.wtr_lab_sign_in_progress);
        configureWebView();
        loadStartingUrl(getIntent());

        // onBackPressed() is not called for apps targeting Android 16, so use the dispatcher.
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
                return shouldBlockNavigation(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldBlockNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Each page load gets its own session check; one failed check must not disable the rest.
                sessionCheckAttempted = false;
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                // Client-side (SPA) navigations never reach onPageFinished. Full loads are handled there.
                if (view.getProgress() < 100) return;
                Uri uri = Uri.parse(url);
                if (isWtrLabUrl(uri)) maybeCompleteSignIn(view, uri);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Uri uri = Uri.parse(url);
                if (isWtrLabUrl(uri)) {
                    // Keep WebView's own cookie store as the source of truth for the session.
                    CookieManager.getInstance().flush();
                    maybeCompleteSignIn(view, uri);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });
    }

    private void loadStartingUrl(android.content.Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (intent != null
                && android.content.Intent.ACTION_VIEW.equals(intent.getAction())
                && (isMagicLink(data) || isLoginPageUri(data))) {
            beginAuthFlow();
            webView.loadUrl(data.toString());
        } else {
            beginAuthFlow();
            webView.loadUrl(LOGIN_URL);
        }
    }

    private boolean isMagicLink(@Nullable Uri uri) {
        if (!isWtrLabUrl(uri) || uri == null) return false;
        String path = uri.getEncodedPath();
        boolean verificationRoute = "/api/auth/magic-link/verify".equals(path)
                || "/auth/magic-link".equals(path)
                || "/en/auth/magic-link".equals(path)
                || "/en/auth/login".equals(path);
        String token = uri.getQueryParameter("token");
        return verificationRoute && token != null && !token.trim().isEmpty();
    }

    private void beginAuthFlow() {
        authFlowActive = true;
        sessionCheckInProgress = false;
        sessionCheckAttempted = false;
    }

    private boolean shouldBlockNavigation(Uri uri) {
        if (isWtrLabUrl(uri)) return false;
        if (isSupportedProviderUrl(uri)) {
            sessionCheckAttempted = false;
            return false;
        }
        Toast.makeText(this, R.string.wtr_lab_provider_unsupported, Toast.LENGTH_LONG).show();
        return true;
    }

    private boolean isLoginPageUri(@Nullable Uri uri) {
        return isWtrLabUrl(uri) && "/en/auth/login".equals(uri.getEncodedPath());
    }

    private boolean isSupportedProviderUrl(@Nullable Uri uri) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost();
        // Google blocks OAuth inside embedded WebViews (disallowed_useragent), so it is not allowed here.
        return "github.com".equalsIgnoreCase(host);
    }

    private void maybeCompleteSignIn(WebView view, Uri uri) {
        String path = uri.getEncodedPath();
        if (!authFlowActive
                || sessionCheckInProgress
                || sessionCheckAttempted
                || !(LOGIN_CALLBACK_PATH.equals(path) || (LOGIN_CALLBACK_PATH + "/").equals(path))) {
            return;
        }

        sessionCheckAttempted = true;
        sessionCheckInProgress = true;
        view.evaluateJavascript(SESSION_CHECK_SCRIPT, value -> {
            sessionCheckInProgress = false;
            if (isFinishing() || !authFlowActive) return;
            if ("true".equals(value)) {
                authFlowActive = false;
                CookieManager.getInstance().flush();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, R.string.wtr_lab_signin_not_confirmed, Toast.LENGTH_LONG).show();
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
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isMagicLink(intent.getData()) || isLoginPageUri(intent.getData())) {
            beginAuthFlow();
            webView.loadUrl(intent.getData().toString());
        } else {
            Toast.makeText(this, R.string.wtr_lab_invalid_magic_link, Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteMagicLink() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence text = clip == null || clip.getItemCount() == 0
                ? null
                : clip.getItemAt(0).coerceToText(this);
        Uri uri = text == null ? null : Uri.parse(text.toString().trim());
        if (isMagicLink(uri)) {
            beginAuthFlow();
            webView.loadUrl(uri.toString());
        } else {
            Toast.makeText(this, R.string.wtr_lab_clipboard_no_link, Toast.LENGTH_SHORT).show();
        }
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