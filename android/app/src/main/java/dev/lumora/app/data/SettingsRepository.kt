package dev.lumora.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** User-tunable behaviour, persisted across launches. */
data class AppSettings(
    val adBlockEnabled: Boolean = true,
    val backgroundPlayEnabled: Boolean = true,
    val sponsorBlockEnabled: Boolean = true,
    val autoPipEnabled: Boolean = true,
    val desktopMode: Boolean = false,
    val lastMode: BrowseMode = BrowseMode.Video,
)

/**
 * Tiny SharedPreferences-backed store exposing settings as a [StateFlow].
 * Kept deliberately simple (no DataStore) — a handful of booleans.
 */
class SettingsRepository private constructor(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update { old ->
            transform(old).also { write(it) }
        }
    }

    private fun read() = AppSettings(
        adBlockEnabled = prefs.getBoolean(KEY_ADBLOCK, true),
        backgroundPlayEnabled = prefs.getBoolean(KEY_BG_PLAY, true),
        sponsorBlockEnabled = prefs.getBoolean(KEY_SPONSOR, true),
        autoPipEnabled = prefs.getBoolean(KEY_PIP, true),
        desktopMode = prefs.getBoolean(KEY_DESKTOP, false),
        lastMode = runCatching {
            BrowseMode.valueOf(prefs.getString(KEY_MODE, BrowseMode.Video.name)!!)
        }.getOrDefault(BrowseMode.Video),
    )

    private fun write(s: AppSettings) = prefs.edit {
        putBoolean(KEY_ADBLOCK, s.adBlockEnabled)
        putBoolean(KEY_BG_PLAY, s.backgroundPlayEnabled)
        putBoolean(KEY_SPONSOR, s.sponsorBlockEnabled)
        putBoolean(KEY_PIP, s.autoPipEnabled)
        putBoolean(KEY_DESKTOP, s.desktopMode)
        putString(KEY_MODE, s.lastMode.name)
    }

    companion object {
        private const val KEY_ADBLOCK = "adblock"
        private const val KEY_BG_PLAY = "background_play"
        private const val KEY_SPONSOR = "sponsorblock"
        private const val KEY_PIP = "auto_pip"
        private const val KEY_DESKTOP = "desktop_mode"
        private const val KEY_MODE = "last_mode"

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(
                    context.applicationContext.getSharedPreferences("lumora_settings", Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
