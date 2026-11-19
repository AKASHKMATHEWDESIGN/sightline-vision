package dev.lumora.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dev.lumora.app.data.SettingsRepository
import dev.lumora.app.media.PlaybackBridge

class LumoraApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        SettingsRepository.get(this)
        PlaybackBridge.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
}
