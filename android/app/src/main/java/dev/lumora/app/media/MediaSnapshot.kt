package dev.lumora.app.media

import org.json.JSONObject

/** Playback state reported by the injected media bridge script. */
data class MediaSnapshot(
    val hasMedia: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val isAd: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
    val videoId: String? = null,
) {
    companion object {
        val Empty = MediaSnapshot()

        fun fromJson(json: JSONObject) = MediaSnapshot(
            hasMedia = json.optBoolean("hasMedia"),
            playing = json.optBoolean("playing"),
            buffering = json.optBoolean("buffering"),
            ended = json.optBoolean("ended"),
            positionMs = json.optLong("position"),
            durationMs = json.optLong("duration"),
            videoWidth = json.optInt("width"),
            videoHeight = json.optInt("height"),
            isAd = json.optBoolean("ad"),
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            artworkUrl = json.optString("artwork"),
            videoId = json.optString("videoId").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

/** Transport commands sent from Android (notification, lock screen, PiP, UI) to the page. */
sealed interface WebCommand {
    val js: String

    data object Play : WebCommand { override val js = "play" }
    data object Pause : WebCommand { override val js = "pause" }
    data object Next : WebCommand { override val js = "next" }
    data object Previous : WebCommand { override val js = "previous" }
    data class Seek(val positionMs: Long) : WebCommand { override val js = "seek" }
}
