package dev.lumora.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lumora.app.data.AppSettings
import dev.lumora.app.data.BrowseMode
import dev.lumora.app.data.SettingsRepository
import dev.lumora.app.media.MediaSnapshot
import dev.lumora.app.media.PlaybackBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Everything the UI renders. */
data class UiState(
    val settings: AppSettings = AppSettings(),
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val currentUrl: String? = null,
    val blockedCount: Int = 0,
    val nowPlaying: MediaSnapshot = MediaSnapshot.Empty,
    val isFullscreen: Boolean = false,
    val isInPip: Boolean = false,
    val showSettings: Boolean = false,
) {
    val mode: BrowseMode get() = settings.lastMode
}

/** Transient browser state reported by the WebView. */
private data class BrowserState(
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val currentUrl: String? = null,
    val blockedCount: Int = 0,
    val isFullscreen: Boolean = false,
    val isInPip: Boolean = false,
    val showSettings: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository.get(application)
    private val browser = MutableStateFlow(BrowserState())

    val settings: StateFlow<AppSettings> = repository.settings

    val uiState: StateFlow<UiState> = combine(
        repository.settings,
        browser,
        PlaybackBridge.nowPlaying,
    ) { settings, b, media ->
        UiState(
            settings = settings,
            progress = b.progress,
            canGoBack = b.canGoBack,
            currentUrl = b.currentUrl,
            blockedCount = b.blockedCount,
            nowPlaying = media,
            isFullscreen = b.isFullscreen,
            isInPip = b.isInPip,
            showSettings = b.showSettings,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(settings = repository.current))

    val currentSettings: AppSettings get() = repository.current

    // --- Mode & settings -------------------------------------------------

    fun setMode(mode: BrowseMode) = repository.update { it.copy(lastMode = mode) }

    fun toggleDesktop() = repository.update { it.copy(desktopMode = !it.desktopMode) }

    fun setAdBlock(enabled: Boolean) = repository.update { it.copy(adBlockEnabled = enabled) }

    fun setBackgroundPlay(enabled: Boolean) = repository.update { it.copy(backgroundPlayEnabled = enabled) }

    fun setSponsorBlock(enabled: Boolean) = repository.update { it.copy(sponsorBlockEnabled = enabled) }

    fun setAutoPip(enabled: Boolean) = repository.update { it.copy(autoPipEnabled = enabled) }

    // --- Browser events --------------------------------------------------

    fun onProgress(progress: Int) = browser.update { it.copy(progress = progress) }

    fun onNavigation(url: String, canGoBack: Boolean) {
        browser.update { it.copy(currentUrl = url, canGoBack = canGoBack) }
        // Keep the switcher in sync when history crosses between YouTube and YouTube Music.
        val mode = BrowseMode.forUrl(url)
        if (url.contains("youtube.com") && mode != repository.current.lastMode) setMode(mode)
    }

    fun onRequestBlocked() = browser.update { it.copy(blockedCount = it.blockedCount + 1) }

    fun setFullscreen(fullscreen: Boolean) = browser.update { it.copy(isFullscreen = fullscreen) }

    fun setInPip(inPip: Boolean) = browser.update { it.copy(isInPip = inPip) }

    fun showSettings(show: Boolean) = browser.update { it.copy(showSettings = show) }
}
