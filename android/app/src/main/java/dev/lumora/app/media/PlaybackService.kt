package dev.lumora.app.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.lumora.app.MainActivity
import dev.lumora.app.R

/**
 * Foreground media service. Hosts the [MediaSession] that drives the
 * notification-shade controls, lock-screen controls and Bluetooth/headset
 * buttons. The actual audio/video keeps running inside the WebView; this
 * service keeps the process foregrounded so Android doesn't kill it.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlaybackBridge.init(this)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, PlaybackBridge.player)
            .setId("lumora")
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping Lumora away from Recents stops playback entirely.
        PlaybackBridge.onTaskRemoved()
        stopSelf()
    }

    override fun onDestroy() {
        // Release the session only; the player facade lives for the whole process.
        session?.release()
        session = null
        super.onDestroy()
    }
}
