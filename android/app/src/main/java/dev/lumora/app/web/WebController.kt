package dev.lumora.app.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.lumora.app.R
import dev.lumora.app.adblock.AdBlockManager
import dev.lumora.app.data.AppSettings
import dev.lumora.app.data.BrowseMode
import dev.lumora.app.media.MediaSnapshot
import dev.lumora.app.media.PlaybackBridge
import dev.lumora.app.media.WebCommand
import dev.lumora.app.sponsor.SponsorBlockClient
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Owns the single [LumoraWebView] and everything wired into it: settings,
 * clients, ad-blocking, injected scripts, the JS bridge and pull-to-refresh.
 *
 * Lives as long as the Activity (the Activity handles configuration changes
 * itself, so the WebView and its playback survive rotation).
 */
@SuppressLint("SetJavaScriptEnabled")
class WebController(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
    private val host: Host,
    initialSettings: AppSettings,
) : WebBridge.Listener {

    /** Activity-level operations the controller cannot do itself. */
    interface Host {
        fun onProgress(progress: Int)
        fun onNavigation(url: String, canGoBack: Boolean)
        fun onRequestBlocked()
        fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
        fun onHideFullscreen()
        fun onShowFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
        fun onMediaState(snapshot: MediaSnapshot)
        fun share(title: String, text: String, url: String)
        fun toast(message: String)
        fun onRendererGone()
    }

    val webView: LumoraWebView = LumoraWebView(context)
    val container: SwipeRefreshLayout = SwipeRefreshLayout(context)

    private var settings: AppSettings = initialSettings
    private var jsCanScrollUp = false
    private var isFullscreen = false
    private val sponsorBlock = SponsorBlockClient()
    private val scriptHandlers = mutableListOf<ScriptHandler>()
    private val documentStartSupported =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val coreScriptSource: String by lazy { asset("js/core.js") }
    private val adBlockScriptSource: String by lazy {
        asset("js/adblock.js").replace("'__LUMORA_CSS__'", JSONObject.quote(AdBlockManager.cosmeticCss))
    }

    private val emptyResponse: WebResourceResponse
        get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    init {
        configureWebView()
        configurePullToRefresh()
        WebBridge(this).attach(webView)
        registerScripts()
        PlaybackBridge.commandSink = ::execute
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    private fun configureWebView() = with(webView) {
        setBackgroundColor(Color.parseColor("#0A0A0C"))
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        keepAliveInBackground = this@WebController.settings.backgroundPlayEnabled

        with(this.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = userAgentFor(this@WebController.settings.desktopMode)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            // YouTube has a native dark theme; never let Chromium invert it.
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(this.settings, false)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webViewClient = Client()
        webChromeClient = ChromeClient()
        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun configurePullToRefresh() = with(container) {
        addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setProgressBackgroundColorSchemeColor(Color.parseColor("#16161A"))
        setColorSchemeColors(Color.parseColor("#FF2A54"))
        setDistanceToTriggerSync(220)
        setOnChildScrollUpCallback { _, _ ->
            // Only allow pull-to-refresh when the page (or the touched scroller) is at the very top.
            isFullscreen || jsCanScrollUp || webView.scrollY > 0 || webView.canScrollVertically(-1)
        }
        setOnRefreshListener {
            webView.reload()
            postDelayed({ isRefreshing = false }, 8_000)
        }
    }

    /** (Re-)registers document-start scripts so they run before any page script. */
    @SuppressLint("RequiresFeature") // guarded by documentStartSupported
    private fun registerScripts() {
        scriptHandlers.forEach { it.remove() }
        scriptHandlers.clear()
        if (!documentStartSupported) return
        scriptHandlers += WebViewCompat.addDocumentStartJavaScript(webView, coreScript(), UrlRouter.scriptOrigins)
        if (settings.adBlockEnabled) {
            scriptHandlers += WebViewCompat.addDocumentStartJavaScript(webView, adBlockScriptSource, UrlRouter.scriptOrigins)
        }
    }

    private fun coreScript(): String {
        val config = JSONObject()
            .put("bgPlay", settings.backgroundPlayEnabled)
            .put("adBlock", settings.adBlockEnabled)
            .put("sponsorBlock", settings.sponsorBlockEnabled)
        return "window.__lumoraConfig = $config;\n$coreScriptSource"
    }

    /** Fallback / belt-and-braces injection for when document-start scripts are unavailable. */
    private fun injectScripts(view: WebView, url: String?) {
        val uri = url?.let(Uri::parse) ?: return
        val origin = "${uri.scheme}://${uri.host}"
        if (origin !in UrlRouter.scriptOrigins) return
        view.evaluateJavascript(coreScript(), null)
        if (settings.adBlockEnabled) view.evaluateJavascript(adBlockScriptSource, null)
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    fun load(url: String) = webView.loadUrl(url)

    fun loadMode(mode: BrowseMode) = load(mode.homeUrl(settings.desktopMode))

    fun reload() = webView.reload()

    fun canGoBack() = webView.canGoBack()

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    val currentUrl: String? get() = webView.url

    fun setAccentColor(argb: Int) = container.setColorSchemeColors(argb)

    fun setFullscreen(fullscreen: Boolean) {
        isFullscreen = fullscreen
        container.isEnabled = !fullscreen
    }

    /** Applies new settings; reloads when a change only takes effect on a fresh document. */
    fun applySettings(new: AppSettings) {
        val old = settings
        settings = new
        if (old == new) return

        webView.keepAliveInBackground = new.backgroundPlayEnabled
        val scriptsChanged = old.adBlockEnabled != new.adBlockEnabled ||
            old.backgroundPlayEnabled != new.backgroundPlayEnabled ||
            old.sponsorBlockEnabled != new.sponsorBlockEnabled
        if (scriptsChanged) {
            registerScripts()
            webView.evaluateJavascript(
                "window.__lumora && __lumora.setConfig({sponsorBlock:${new.sponsorBlockEnabled}})",
                null,
            )
        }

        when {
            old.desktopMode != new.desktopMode -> {
                webView.settings.userAgentString = userAgentFor(new.desktopMode)
                val target = currentUrl?.let { swapVideoHost(it, new.desktopMode) }
                    ?: new.lastMode.homeUrl(new.desktopMode)
                webView.loadUrl(target)
            }
            old.adBlockEnabled != new.adBlockEnabled ||
                old.backgroundPlayEnabled != new.backgroundPlayEnabled -> webView.reload()
        }
    }

    /** Executes a transport command inside the page. */
    fun execute(command: WebCommand) {
        val arg = (command as? WebCommand.Seek)?.positionMs ?: 0
        webView.evaluateJavascript("window.__lumora && __lumora.cmd('${command.js}', $arg)", null)
    }

    fun clearBrowsingData() {
        webView.clearCache(true)
        webView.clearHistory()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        loadMode(settings.lastMode)
    }

    fun onResume() {
        webView.onResume()
        webView.resumeTimers()
    }

    fun onPause() {
        CookieManager.getInstance().flush()
        if (!settings.backgroundPlayEnabled) webView.onPause()
    }

    fun onStop(isInPip: Boolean) {
        if (!settings.backgroundPlayEnabled && !isInPip) execute(WebCommand.Pause)
    }

    fun destroy() {
        PlaybackBridge.commandSink = null
        scriptHandlers.forEach { it.remove() }
        container.removeAllViews()
        webView.stopLoading()
        webView.destroy()
    }

    // ---------------------------------------------------------------------
    // Bridge callbacks
    // ---------------------------------------------------------------------

    override fun onMediaState(snapshot: MediaSnapshot) = host.onMediaState(snapshot)

    override fun onScrollHint(canScrollUp: Boolean) {
        jsCanScrollUp = canScrollUp
    }

    override fun onShare(title: String, text: String, url: String) = host.share(title, text, url)

    override fun onVideoChanged(videoId: String) {
        if (!settings.sponsorBlockEnabled) return
        scope.launch {
            val segments = sponsorBlock.segmentsFor(videoId)
            if (segments.isEmpty()) return@launch
            webView.evaluateJavascript(
                "window.__lumora && __lumora.setSegments(${JSONObject.quote(videoId)}, ${SponsorBlockClient.toJs(segments)})",
                null,
            )
        }
    }

    override fun onSegmentSkipped(category: String, seconds: Double) = Unit

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun userAgentFor(desktop: Boolean) =
        if (desktop) UserAgents.desktop(context) else UserAgents.mobile(context)

    private fun swapVideoHost(url: String, desktop: Boolean): String {
        val uri = Uri.parse(url)
        val host = uri.host ?: return url
        if (host == "music.youtube.com" || !host.endsWith("youtube.com")) return url
        return uri.buildUpon().authority(if (desktop) "www.youtube.com" else "m.youtube.com").build().toString()
    }

    private fun asset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun openExternal(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            host.toast(context.getString(R.string.no_app_to_open))
        }
    }

    private fun handleIntentScheme(url: String): Boolean {
        val intent = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return true
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        // A YouTube deep link pointing back at us: just load the fallback inside Lumora.
        val fallback = intent.getStringExtra("browser_fallback_url")
        val data = intent.dataString
        val target = listOfNotNull(data, fallback).firstNotNullOfOrNull { UrlRouter.normalise(it, settings.desktopMode) }
        if (target != null) {
            webView.loadUrl(target)
            return true
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            fallback?.let { openExternal(Uri.parse(it)) }
        }
        return true
    }

    private fun download(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            ContextCompat.getSystemService(context, DownloadManager::class.java)?.enqueue(request)
            host.toast(context.getString(R.string.download_started))
        } catch (_: Exception) {
            openExternal(Uri.parse(url))
        }
    }

    // ---------------------------------------------------------------------
    // Clients
    // ---------------------------------------------------------------------

    private inner class Client : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!settings.adBlockEnabled || request.isForMainFrame) return null
            val url = request.url.toString()
            if (AdBlockManager.shouldBlock(url)) {
                view.post { host.onRequestBlocked() }
                return emptyResponse
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val url = uri.toString()
            return when (uri.scheme?.lowercase()) {
                "intent" -> handleIntentScheme(url)
                "vnd.youtube" -> {
                    val id = uri.schemeSpecificPart.trimStart('/').substringBefore('?')
                    UrlRouter.normalise("https://youtu.be/$id", settings.desktopMode)?.let(view::loadUrl)
                    true
                }
                "http", "https" -> {
                    UrlRouter.unwrapRedirect(uri)?.let {
                        openExternal(it)
                        return true
                    }
                    if (UrlRouter.isInternal(uri)) false else {
                        openExternal(uri)
                        true
                    }
                }
                "about", "data", "blob", "javascript" -> false
                else -> {
                    openExternal(uri)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (!documentStartSupported) injectScripts(view, url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            container.isRefreshing = false
            injectScripts(view, url)
            CookieManager.getInstance().flush()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The renderer crashed or was killed for memory while backgrounded.
            // This WebView is unusable now: drop media state and let the host rebuild.
            PlaybackBridge.shutdown()
            host.onRendererGone()
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            host.onNavigation(url ?: return, view.canGoBack())
        }
    }

    private inner class ChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) = host.onProgress(newProgress)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) =
            host.onShowFullscreen(view, callback)

        override fun onHideCustomView() = host.onHideFullscreen()

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = host.onShowFileChooser(filePathCallback, fileChooserParams)

        /** Transparent poster: avoids Chromium's grey play-button flash before video frames. */
        override fun getDefaultVideoPoster(): Bitmap =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?) = false
    }
}
