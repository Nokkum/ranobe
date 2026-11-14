package org.ranobe.ranobe.ui.webview;

import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;
import java.util.Set;

/**
 * Lets a page that shows WTR-LAB's human check tell the app when the check has been passed, without the app asking
 * the site anything itself.
 * <p>
 * The page's own script is what talks to the site, so the only thing to do is listen: a small script, run before any
 * of the page's own, wraps {@code fetch} and {@code XMLHttpRequest} and hands every successful answer of the reader
 * API ({@code /api/reader/get}, the call the app makes for each chapter) to {@link Listener}. Nothing is sent from
 * here, so watching adds no requests that could count towards the site's rate limit and bring the check back.
 * <p>
 * The script and the message channel are only given to {@code origin}, so other sites and the check's own frame
 * cannot report anything.
 */
public final class ReaderCheckHook {
    /**
     * Receives the body of each successful reader API answer the page gets, on the main thread.
     */
    public interface Listener {
        void onReaderAnswer(@NonNull String body);
    }

    // the object the script posts to, see WebViewCompat.addWebMessageListener
    static final String CHANNEL = "RanobeReaderCheck";

    // Runs in the page. Never changes what the page sees: the original promise or request is passed on as it is, and
    // every step of the watching is wrapped so that a failure in it cannot reach the page.
    static final String SCRIPT = "(function(){"
            + "if(window.__ranobeReaderCheck)return;"
            + "window.__ranobeReaderCheck=true;"
            + "var PATH='/api/reader/get';"
            + "function isReader(u){try{var p=new URL(String(u),location.href).pathname;"
            + "return p.length>=PATH.length&&p.indexOf(PATH,p.length-PATH.length)>=0}catch(e){return false}}"
            + "function report(t){try{if(typeof t==='string')" + CHANNEL + ".postMessage(t)}catch(e){}}"
            + "var nativeFetch=window.fetch;"
            + "if(typeof nativeFetch==='function'){"
            + "window.fetch=function(input){"
            + "var result=nativeFetch.apply(this,arguments);"
            + "try{var u=typeof input==='string'?input:(input&&input.url)||input;"
            + "if(isReader(u)){result.then(function(r){"
            + "try{if(r&&r.ok)r.clone().text().then(report,function(){})}catch(e){}"
            + "},function(){})}}catch(e){}"
            + "return result}}"
            + "var open=XMLHttpRequest.prototype.open;"
            + "XMLHttpRequest.prototype.open=function(m,u){"
            + "try{this.__ranobeReader=isReader(u)}catch(e){}"
            + "return open.apply(this,arguments)};"
            + "var send=XMLHttpRequest.prototype.send;"
            + "XMLHttpRequest.prototype.send=function(){"
            + "if(this.__ranobeReader){var x=this;x.addEventListener('load',function(){"
            + "try{if(x.status>=200&&x.status<300)"
            + "report(x.responseType==='json'?JSON.stringify(x.response):x.responseText)}catch(e){}"
            + "})}"
            + "return send.apply(this,arguments)}"
            + "})();";

    private ReaderCheckHook() {
    }

    /**
     * Starts listening in every page {@code webView} loads from now on, so it has to be called before the first
     * {@code loadUrl}.
     *
     * @param origin the one origin to watch, "https://example.com"
     * @return false when this WebView is too old to do it; the page works as before, it just cannot report
     */
    public static boolean install(@NonNull WebView webView, @NonNull String origin, @NonNull Listener listener) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return false;
        }
        Set<String> origins = Collections.singleton(origin);
        WebViewCompat.addWebMessageListener(webView, CHANNEL, origins,
                (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                    String body = message.getData();
                    if (body != null) listener.onReaderAnswer(body);
                });
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, origins);
        return true;
    }
}
