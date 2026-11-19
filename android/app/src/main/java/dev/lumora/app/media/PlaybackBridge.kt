package dev.lumora.app.media

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide glue between the WebView (which actually plays the media),
 * the [WebMediaPlayer] facade and [PlaybackService].
 *
 * All methods must be called on the main thread.
 */
object PlaybackBridge {

    private lateinit var appContext: Context

    /** Set by the WebView host; executes a command inside the page. */
    var commandSink: ((WebCommand) -> Unit)? = null

    lateinit var player: WebMediaPlayer
        private set

    private val _nowPlaying = MutableStateFlow(MediaSnapshot.Empty)
    val nowPlaying: StateFlow<MediaSnapshot> = _nowPlaying.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        player = WebMediaPlayer { command -> send(command) }
    }

    fun send(command: WebCommand) {
        commandSink?.invoke(command)
    }

    /** Called whenever the page reports new playback state. */
    fun onSnapshot(snapshot: MediaSnapshot) {
        _nowPlaying.value = snapshot
        player.update(snapshot)
        if (snapshot.hasMedia && (snapshot.playing || snapshot.buffering)) {
            ensureSessionService()
            acquireLocks()
        } else {
            releaseLocks()
        }
    }

    /**
     * Binding a [MediaController] starts [PlaybackService]; Media3 then promotes
     * it to a foreground service (with notification) whenever the player plays.
     */
    private fun ensureSessionService() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
    }

    /** The WebView is gone (activity finished or task removed): tear everything down. */
    fun shutdown() {
        _nowPlaying.value = MediaSnapshot.Empty
        player.update(MediaSnapshot.Empty)
        releaseLocks()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    fun onTaskRemoved() {
        send(WebCommand.Pause)
        shutdown()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = ContextCompat.getSystemService(appContext, PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lumora:playback")
                ?.apply { setReferenceCounted(false) }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            wifiLock = ContextCompat.getSystemService(appContext, WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Lumora:playback")
                ?.apply { setReferenceCounted(false) }
        }
        if (wakeLock?.isHeld == false) wakeLock?.acquire()
        if (wifiLock?.isHeld == false) wifiLock?.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }
}
