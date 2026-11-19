package dev.lumora.app.adblock

import java.net.URI
import java.util.Locale

/**
 * Network + cosmetic ad-block engine.
 *
 * Network filtering is pure Kotlin (no Android types) so it can be unit tested
 * and called cheaply from [android.webkit.WebViewClient.shouldInterceptRequest],
 * which runs on a WebView background thread.
 */
object AdBlockManager {

    /** Hosts blocked outright, including all of their subdomains. */
    private val blockedHosts: Set<String> = hashSetOf(
        // Google ad serving
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "imasdk.googleapis.com",
        "ads.youtube.com",
        "admob.com",
        "adsense.com",
        "2mdn.net",
        "app-measurement.com",
        "googleads.g.doubleclick.net",
        "static.doubleclick.net",
        "fundingchoicesmessages.google.com",
        // Common third-party trackers occasionally pulled in by embeds
        "scorecardresearch.com",
        "moatads.com",
        "adnxs.com",
        "advertising.com",
        "amazon-adsystem.com",
        "criteo.com",
        "outbrain.com",
        "taboola.com",
        "quantserve.com",
        "facebook.net",
        "branch.io",
        "app.adjust.com",
        "appsflyer.com",
    )

    /** Hosts whose *paths* are filtered (first-party ad / telemetry endpoints). */
    private val youtubeHosts = listOf("youtube.com", "youtube-nocookie.com", "google.com")

    /** Path fragments blocked on YouTube / Google first-party hosts. */
    private val blockedPathFragments: List<String> = listOf(
        "/api/stats/ads",
        "/api/stats/atr",
        "/api/stats/qoe",
        "/pagead/",
        "/ptracking",
        "/get_midroll_info",
        "/youtubei/v1/player/ad_break",
        "/youtubei/v1/log_event",
        "/log_event",
        "/csi_204",
        "/error_204",
        "/pcs/activeview",
        "/ad_data_204",
        "/generate_204?ad",
        "/aclk",
        "/gen_204?ad",
        "/adview",
    )

    /** Query parameters that only appear on ad / ad-measurement requests. */
    private val blockedQueryMarkers: List<String> = listOf(
        "ad_type=",
        "adformat=",
    )

    /** Paths that must never be blocked (watch history, playback, sign-in). */
    private val allowedPathFragments: List<String> = listOf(
        "/api/stats/watchtime",
        "/api/stats/playback",
        "/youtubei/v1/player?",
        "/youtubei/v1/next",
        "/youtubei/v1/browse",
        "/accounts/",
        "/signin",
        "/ServiceLogin",
    )

    /**
     * Returns true when [url] is an ad, tracker or telemetry request.
     * Never throws: malformed URLs are allowed through.
     */
    fun shouldBlock(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false

        if (isHostBlocked(host)) return true

        if (youtubeHosts.any { host == it || host.endsWith(".$it") }) {
            val pathAndQuery = buildString {
                append(uri.rawPath ?: "")
                uri.rawQuery?.let { append('?').append(it) }
            }
            if (allowedPathFragments.any { pathAndQuery.startsWith(it) || pathAndQuery.contains(it) }) {
                return false
            }
            if (blockedPathFragments.any { pathAndQuery.contains(it) }) return true
            val query = uri.rawQuery ?: return false
            // Ad videos are served from googlevideo but measured through these markers.
            if (blockedQueryMarkers.any { query.contains(it) }) return true
        }
        return false
    }

    /** Matches [host] and every parent domain against the block list. */
    fun isHostBlocked(host: String): Boolean {
        var candidate = host.trimEnd('.')
        while (true) {
            if (candidate in blockedHosts) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || dot == candidate.lastIndexOf('.')) return candidate in blockedHosts
            candidate = candidate.substring(dot + 1)
        }
    }

    /** Cosmetic filters for YouTube (mobile + desktop) and YouTube Music. */
    val cosmeticCss: String = listOf(
        // m.youtube.com
        "ytm-promoted-sparkles-web-renderer",
        "ytm-promoted-sparkles-text-search-renderer",
        "ytm-promoted-video-renderer",
        "ytm-companion-ad-renderer",
        "ytm-companion-slot",
        "ytm-display-ad-renderer",
        "ytm-in-feed-ad-layout-renderer",
        "ytm-ad-slot-renderer",
        "ad-slot-renderer",
        "ytm-rich-item-renderer:has(ad-slot-renderer)",
        "ytm-rich-item-renderer:has(ytm-ad-slot-renderer)",
        "ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer)",
        "ytm-statement-banner-renderer",
        "ytm-banner-promo-renderer",
        "ytm-mealbar-promo-renderer",
        "ytm-paid-content-overlay-renderer",
        "ytm-brand-video-singleton-renderer",
        "ytm-merch-shelf-renderer",
        "ytm-engagement-panel-section-list-renderer:has(ytm-ads-engagement-panel-content-renderer)",
        // www.youtube.com (desktop view)
        "#masthead-ad",
        "#player-ads",
        "#panels > ytd-engagement-panel-section-list-renderer[target-id=\"engagement-panel-ads\"]",
        "ytd-ad-slot-renderer",
        "ytd-in-feed-ad-layout-renderer",
        "ytd-banner-promo-renderer",
        "ytd-statement-banner-renderer",
        "ytd-promoted-sparkles-web-renderer",
        "ytd-promoted-sparkles-text-search-renderer",
        "ytd-promoted-video-renderer",
        "ytd-display-ad-renderer",
        "ytd-companion-slot-renderer",
        "ytd-action-companion-ad-renderer",
        "ytd-player-legacy-desktop-watch-ads-renderer",
        "ytd-rich-item-renderer:has(ytd-ad-slot-renderer)",
        "ytd-rich-section-renderer:has(ytd-statement-banner-renderer)",
        "ytd-search-pyv-renderer",
        "ytd-merch-shelf-renderer",
        "ytd-mealbar-promo-renderer",
        "ytd-primetime-promo-renderer",
        "ytd-brand-video-singleton-renderer",
        "tp-yt-paper-dialog:has(ytd-enforcement-message-view-model)",
        "tp-yt-paper-dialog:has(ytd-mealbar-promo-renderer)",
        // In-player ad chrome
        ".video-ads",
        ".ytp-ad-module",
        ".ytp-ad-overlay-container",
        ".ytp-ad-image-overlay",
        ".ytp-ad-text-overlay",
        ".ytp-featured-product",
        ".ytp-suggested-action",
        ".ytp-paid-content-overlay",
        // YouTube Music
        "ytmusic-mealbar-promo-renderer",
        "ytmusic-statement-banner-renderer",
        "ytmusic-upsell-dialog-renderer",
        "ytmusic-enforcement-message-view-model",
        "tp-yt-paper-dialog:has(ytmusic-mealbar-promo-renderer)",
        "tp-yt-paper-dialog:has(ytmusic-upsell-dialog-renderer)",
        "ytmusic-popup-container tp-yt-paper-toast:has(a[href*=\"premium\"])",
        // "Get YouTube Premium" upsells
        "a[href^=\"/premium\"]:not([role=\"menuitem\"])",
        "ytm-upsell-dialog-renderer",
        "ytd-popup-container tp-yt-paper-dialog:has(a[href*=\"/premium\"])",
    ).joinToString(separator = ",\n", postfix = " { display: none !important; }") +
        "\ntp-yt-iron-overlay-backdrop.opened:not(:has(~ tp-yt-paper-dialog[aria-hidden=\"false\"])) { display: none !important; }"
}
