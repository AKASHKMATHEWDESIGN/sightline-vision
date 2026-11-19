package dev.lumora.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.lumora.app.data.BrowseMode
import dev.lumora.app.media.MediaSnapshot
import dev.lumora.app.media.PlaybackBridge
import dev.lumora.app.media.WebCommand
import dev.lumora.app.ui.LumoraScreen
import dev.lumora.app.ui.MainViewModel
import dev.lumora.app.ui.ScreenActions
import dev.lumora.app.ui.components.SettingsActions
import dev.lumora.app.ui.theme.LumoraTheme
import dev.lumora.app.web.UrlRouter
import dev.lumora.app.web.WebController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), WebController.Host {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var web: WebController

    private var fullscreenView by mutableStateOf<View?>(null)
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
            fileCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PlaybackBridge.init(this)

        web = WebController(this, lifecycleScope, this, viewModel.currentSettings)

        val incoming = UrlRouter.urlFromIntent(intent, viewModel.currentSettings.desktopMode)
        if (incoming != null) {
            viewModel.setMode(BrowseMode.forUrl(incoming))
            web.load(incoming)
        } else {
            web.loadMode(viewModel.currentSettings.lastMode)
        }

        observeSettings()
        requestNotificationPermission()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LumoraTheme(mode = state.mode) {
                LumoraScreen(
                    state = state,
                    controller = web,
                    fullscreenView = fullscreenView,
                    actions = screenActions(),
                )
            }
        }
    }

    private fun screenActions() = ScreenActions(
        onModeChange = { mode ->
            viewModel.setMode(mode)
            web.loadMode(mode)
        },
        onRefresh = web::reload,
        onToggleDesktop = viewModel::toggleDesktop,
        onOpenSettings = { viewModel.showSettings(true) },
        onDismissSettings = { viewModel.showSettings(false) },
        onBack = web::goBack,
        onExitFullscreen = { fullscreenCallback?.onCustomViewHidden() ?: onHideFullscreen() },
        settings = SettingsActions(
            onAdBlock = viewModel::setAdBlock,
            onBackgroundPlay = viewModel::setBackgroundPlay,
            onSponsorBlock = viewModel::setSponsorBlock,
            onAutoPip = viewModel::setAutoPip,
            onDesktop = viewModel::toggleDesktop,
            onShare = { web.currentUrl?.let { share(title = "", text = "", url = it) } },
            onOpenInBrowser = {
                web.currentUrl?.let { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            },
            onClearData = {
                viewModel.showSettings(false)
                web.clearBrowsingData()
            },
            onPlayPause = {
                PlaybackBridge.send(if (PlaybackBridge.nowPlaying.value.playing) WebCommand.Pause else WebCommand.Play)
            },
            onNext = { PlaybackBridge.send(WebCommand.Next) },
            onPrevious = { PlaybackBridge.send(WebCommand.Previous) },
        ),
    )

    /** Pushes persisted settings into the WebView whenever they change. */
    private fun observeSettings() {
        lifecycleScope.launch {
            viewModel.settings.collect { web.applySettings(it) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                PlaybackBridge.nowPlaying
                    .map { it.playing to it.hasMedia }
                    .distinctUntilChanged()
                    .collect { updatePipParams() }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        UrlRouter.urlFromIntent(intent, viewModel.currentSettings.desktopMode)?.let { url ->
            viewModel.setMode(BrowseMode.forUrl(url))
            web.load(url)
        }
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onStop() {
        super.onStop()
        web.onStop(isInPip = isInPictureInPictureMode)
    }

    override fun onDestroy() {
        // The WebView (and therefore the media) dies with the Activity.
        PlaybackBridge.shutdown()
        web.destroy()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Picture-in-Picture
    // ---------------------------------------------------------------------

    private fun shouldAutoPip(): Boolean {
        val media = PlaybackBridge.nowPlaying.value
        return viewModel.currentSettings.autoPipEnabled && fullscreenView != null && media.hasMedia && media.playing
    }

    private fun pipParams(): PictureInPictureParams {
        val media = PlaybackBridge.nowPlaying.value
        val ratio = if (media.videoWidth > 0 && media.videoHeight > 0) {
            (media.videoWidth.toFloat() / media.videoHeight).coerceIn(1f / 2.39f, 2.39f)
        } else {
            16f / 9f
        }
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(shouldAutoPip())
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun updatePipParams() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP automatically via setAutoEnterEnabled.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldAutoPip()) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setInPip(isInPictureInPictureMode)
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // The PiP window was dismissed (not expanded): stop playback.
            PlaybackBridge.send(WebCommand.Pause)
        }
    }

    // ---------------------------------------------------------------------
    // WebController.Host
    // ---------------------------------------------------------------------

    override fun onProgress(progress: Int) = viewModel.onProgress(progress)

    override fun onNavigation(url: String, canGoBack: Boolean) = viewModel.onNavigation(url, canGoBack)

    override fun onRequestBlocked() = viewModel.onRequestBlocked()

    override fun onMediaState(snapshot: MediaSnapshot) = PlaybackBridge.onSnapshot(snapshot)

    override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        viewModel.setFullscreen(true)
        web.setFullscreen(true)

        val media = PlaybackBridge.nowPlaying.value
        val portraitVideo = media.videoHeight > media.videoWidth && media.videoWidth > 0
        requestedOrientation = if (portraitVideo) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        updatePipParams()
    }

    override fun onHideFullscreen() {
        if (fullscreenView == null) return
        fullscreenView = null
        fullscreenCallback = null
        viewModel.setFullscreen(false)
        web.setFullscreen(false)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        updatePipParams()
    }

    override fun onShowFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            fileChooser.launch(params.createIntent())
            true
        } catch (_: ActivityNotFoundException) {
            fileCallback = null
            false
        }
    }

    override fun share(title: String, text: String, url: String) {
        val body = listOf(text, url).filter { it.isNotBlank() }.distinct().joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_via)))
    }

    override fun onRendererGone() {
        // Rebuild the Activity (and with it a fresh WebView) on the last-used mode.
        recreate()
    }

    override fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
