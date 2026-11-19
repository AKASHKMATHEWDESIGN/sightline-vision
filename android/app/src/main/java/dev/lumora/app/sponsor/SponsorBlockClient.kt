package dev.lumora.app.sponsor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** A segment the player should jump over. */
data class SkipSegment(val start: Double, val end: Double, val category: String)

/**
 * Minimal client for the community SponsorBlock API (sponsor.ajay.app).
 *
 * Uses the privacy-preserving hash-prefix endpoint: only the first 4 hex chars
 * of SHA-256(videoId) leave the device, never the video ID itself.
 */
class SponsorBlockClient {

    private val cache = object : LinkedHashMap<String, List<SkipSegment>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipSegment>>?) = size > 64
    }

    suspend fun segmentsFor(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[videoId] }?.let { return@withContext it }
        val result = runCatching { fetch(videoId) }.getOrDefault(emptyList())
        synchronized(cache) { cache[videoId] = result }
        result
    }

    private fun fetch(videoId: String): List<SkipSegment> {
        val prefix = sha256(videoId).take(4)
        val categories = URLEncoder.encode(JSONArray(CATEGORIES).toString(), "UTF-8")
        val url = URL("$API/api/skipSegments/$prefix?categories=$categories&actionType=skip")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Lumora/1.0 (Android)")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body, videoId)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String, videoId: String): List<SkipSegment> {
        val videos = JSONArray(body)
        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            if (video.optString("videoID") != videoId) continue
            val segments = video.optJSONArray("segments") ?: return emptyList()
            return (0 until segments.length()).mapNotNull { j ->
                val seg = segments.getJSONObject(j)
                val range = seg.optJSONArray("segment") ?: return@mapNotNull null
                val start = range.optDouble(0)
                val end = range.optDouble(1)
                if (end - start < 0.5) null else SkipSegment(start, end, seg.optString("category"))
            }.sortedBy { it.start }
        }
        return emptyList()
    }

    companion object {
        private const val API = "https://sponsor.ajay.app"
        private val CATEGORIES = listOf("sponsor", "selfpromo", "interaction", "music_offtopic")

        fun toJs(segments: List<SkipSegment>): String = JSONArray().apply {
            segments.forEach {
                put(JSONObject().put("start", it.start).put("end", it.end).put("category", it.category))
            }
        }.toString()

        private fun sha256(input: String): String =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
