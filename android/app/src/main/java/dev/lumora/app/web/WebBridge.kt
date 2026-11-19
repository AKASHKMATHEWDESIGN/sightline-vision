package dev.lumora.app.web

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.lumora.app.media.MediaSnapshot
import org.json.JSONObject

/**
 * Receives messages posted by the injected scripts via `LumoraBridge.postMessage(json)`.
 *
 * Prefers [WebViewCompat.addWebMessageListener], which only exposes the bridge
 * to YouTube origins. Falls back to a classic JavaScript interface on very old
 * WebView builds. Listener callbacks always run on the main thread.
 */
class WebBridge(private val listener: Listener) {

    interface Listener {
        fun onMediaState(snapshot: MediaSnapshot)
        fun onScrollHint(canScrollUp: Boolean)
        fun onShare(title: String, text: String, url: String)
        fun onVideoChanged(videoId: String)
        fun onSegmentSkipped(category: String, seconds: Double)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, NAME, UrlRouter.scriptOrigins) { _, message, _, _, _ ->
                message.data?.let(::dispatch)
            }
        } else {
            webView.addJavascriptInterface(LegacyInterface(), NAME)
        }
    }

    private fun dispatch(raw: String) {
        val json = runCatching { JSONObject(raw) }.getOrElse {
            Log.w(TAG, "Malformed bridge message", it)
            return
        }
        when (json.optString("type")) {
            "media" -> listener.onMediaState(MediaSnapshot.fromJson(json))
            "scroll" -> listener.onScrollHint(json.optBoolean("canScrollUp"))
            "share" -> listener.onShare(json.optString("title"), json.optString("text"), json.optString("url"))
            "video" -> json.optString("videoId").takeIf { it.isNotBlank() }?.let(listener::onVideoChanged)
            "skipped" -> listener.onSegmentSkipped(json.optString("category"), json.optDouble("seconds", 0.0))
        }
    }

    /** Fallback bridge (only used when WebMessageListener is unsupported). */
    inner class LegacyInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            mainHandler.post { dispatch(message) }
        }
    }

    private companion object {
        const val NAME = "LumoraBridge"
        const val TAG = "WebBridge"
    }
}
