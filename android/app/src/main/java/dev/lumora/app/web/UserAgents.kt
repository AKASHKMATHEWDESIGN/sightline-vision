package dev.lumora.app.web

import android.content.Context
import android.webkit.WebSettings

/**
 * User-Agent strings that let Google Sign-In work inside the WebView.
 *
 * Google blocks embedded browsers identified by the `; wv` token and the
 * legacy `Version/4.0` marker, so both are stripped from the system UA while
 * keeping the device's real Chrome version (which keeps YouTube's feature
 * detection honest).
 */
object UserAgents {

    private val chromeVersion = Regex("Chrome/([\\d.]+)")

    fun mobile(context: Context): String {
        val system = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?: return FALLBACK_MOBILE
        return system
            .replace("; wv", "")
            .replace(Regex("Version/[\\d.]+ "), "")
            .trim()
    }

    fun desktop(context: Context): String {
        val version = chromeVersion.find(mobile(context))?.groupValues?.get(1)
            ?.substringBefore('.')?.let { "$it.0.0.0" } ?: "131.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$version Safari/537.36"
    }

    private const val FALLBACK_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
}
