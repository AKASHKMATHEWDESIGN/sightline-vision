package dev.lumora.app.media

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A Media3 [Player] facade over media playing *inside the WebView*.
 *
 * The page reports its state through [update]; transport commands issued by
 * the MediaSession (notification, lock screen, Bluetooth, PiP) are forwarded
 * to the page via [onCommand].
 *
 * The timeline is exposed as [placeholder previous, current, placeholder next]
 * so the system always offers Previous/Next buttons; seeking to a neighbour
 * item is translated into a previous/next command for the website.
 */
@OptIn(UnstableApi::class)
class WebMediaPlayer(
    private val onCommand: (WebCommand) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private var snapshot: MediaSnapshot = MediaSnapshot.Empty

    fun update(newSnapshot: MediaSnapshot) {
        snapshot = newSnapshot
        invalidateState()
    }

    override fun getState(): State {
        val s = snapshot
        if (!s.hasMedia) {
            return State.Builder()
                .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_RELEASE).build())
                .setPlaybackState(Player.STATE_IDLE)
                .build()
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(if (s.isAd) "Skipping ad…" else s.title.ifBlank { "Lumora" })
            .setArtist(s.artist.ifBlank { null })
            .setAlbumTitle(s.album.ifBlank { null })
            .setDisplayTitle(s.title)
            .apply { if (s.artworkUrl.isNotBlank()) setArtworkUri(Uri.parse(s.artworkUrl)) }
            .build()

        val currentId = "lumora:current:${s.videoId ?: s.title.hashCode()}"
        val current = MediaItemData.Builder(currentId)
            .setMediaItem(MediaItem.Builder().setMediaId(currentId).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setDurationUs(if (s.durationMs > 0) s.durationMs * 1000 else C.TIME_UNSET)
            .setIsSeekable(s.durationMs > 0 && !s.isAd)
            .setIsDynamic(false)
            .build()

        val playbackState = when {
            s.ended -> Player.STATE_ENDED
            s.buffering -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }

        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaylist(listOf(placeholder(PREVIOUS_UID), current, placeholder(NEXT_UID)))
            .setCurrentMediaItemIndex(CURRENT_INDEX)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(s.playing || s.buffering, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setContentPositionMs(
                PositionSupplier.getExtrapolating(s.positionMs, if (s.playing) 1f else 0f),
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        onCommand(if (playWhenReady) WebCommand.Play else WebCommand.Pause)
        snapshot = snapshot.copy(playing = playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when {
            mediaItemIndex > CURRENT_INDEX -> onCommand(WebCommand.Next)
            mediaItemIndex in 0 until CURRENT_INDEX -> onCommand(WebCommand.Previous)
            else -> {
                val target = if (positionMs == C.TIME_UNSET) 0 else positionMs
                snapshot = snapshot.copy(positionMs = target)
                onCommand(WebCommand.Seek(target))
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        onCommand(WebCommand.Pause)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private fun placeholder(uid: String) = MediaItemData.Builder(uid)
        .setMediaItem(MediaItem.Builder().setMediaId(uid).build())
        .build()

    private companion object {
        const val PREVIOUS_UID = "lumora:previous"
        const val NEXT_UID = "lumora:next"
        const val CURRENT_INDEX = 1

        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
