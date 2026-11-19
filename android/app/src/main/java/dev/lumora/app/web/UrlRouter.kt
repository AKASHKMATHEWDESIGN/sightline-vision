package dev.lumora.app.web

import android.content.Intent
import android.net.Uri
import java.util.Locale

/** Decides which URLs stay inside Lumora and normalises incoming links. */
object UrlRouter {

    /** Hosts that are allowed to load inside the WebView. */
    private val internalHosts = listOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "accounts.google.com",
        "accounts.youtube.com",
        "myaccount.google.com",
        "consent.youtube.com",
        "consent.google.com",
        "google.com",
        "gstatic.com",
        "googleusercontent.com",
        "ytimg.com",
        "ggpht.com",
    )

    /** Origins the native bridge and document-start scripts are exposed to. */
    val scriptOrigins: Set<String> = setOf(
        "https://m.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
        "https://music.youtube.com",
    )

    fun isInternal(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return internalHosts.any { host == it || host.endsWith(".$it") }
    }

    /** `youtube.com/redirect?q=…` links point outside — unwrap them. */
    fun unwrapRedirect(uri: Uri): Uri? {
        val host = uri.host ?: return null
        if (!host.endsWith("youtube.com") || uri.path != "/redirect") return null
        return uri.getQueryParameter("q")?.let(Uri::parse)
    }

    /** Converts any supported incoming intent into a URL Lumora can load, or null. */
    fun urlFromIntent(intent: Intent?, desktop: Boolean): String? {
        intent ?: return null
        val raw = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { URL_PATTERN.find(it)?.value }
            else -> null
        } ?: return null
        return normalise(raw, desktop)
    }

    fun normalise(raw: String, desktop: Boolean): String? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return when {
            host == "youtu.be" -> {
                val id = uri.pathSegments.firstOrNull() ?: return null
                val t = uri.getQueryParameter("t")?.let { "&t=$it" } ?: ""
                "${videoHost(desktop)}/watch?v=$id$t"
            }
            host == "music.youtube.com" -> uri.toString()
            host.endsWith("youtube.com") -> uri.buildUpon()
                .scheme("https")
                .authority(Uri.parse(videoHost(desktop)).host)
                .build()
                .toString()
            else -> null
        }
    }

    private fun videoHost(desktop: Boolean) =
        if (desktop) "https://www.youtube.com" else "https://m.youtube.com"

    private val URL_PATTERN = Regex("https?://\\S+")
}
