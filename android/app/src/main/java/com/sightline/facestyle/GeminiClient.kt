package com.sightline.facestyle

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal client for Google's Gemini image models ("Nano Banana") over the public REST API.
 * Docs: https://ai.google.dev/gemini-api/docs/image-generation
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String,
) {
    class GenerationException(message: String) : Exception(message)

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    /**
     * Sends the reference image followed by the identity photos, plus the instruction text.
     * Returns the generated image as encoded bytes (PNG or JPEG).
     */
    suspend fun generate(
        prompt: String,
        reference: ByteArray,
        faces: List<ByteArray>,
        aspectRatio: String?,
    ): ByteArray = withContext(Dispatchers.IO) {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", "IMAGE 1 — THE REFERENCE (scene, style, mood):"))
        parts.put(inlineImage(reference))
        faces.forEachIndexed { i, bytes ->
            parts.put(JSONObject().put("text", "IMAGE ${i + 2} — THE PERSON (identity source):"))
            parts.put(inlineImage(bytes))
        }
        parts.put(JSONObject().put("text", prompt))

        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            .put("temperature", 0.4)
        val imageConfig = JSONObject()
        if (aspectRatio != null) imageConfig.put("aspectRatio", aspectRatio)
        // Newer (3.x) image models can output 2K; the legacy 2.5 model only supports its default size.
        if (!model.startsWith("gemini-2.")) imageConfig.put("imageSize", "2K")
        if (imageConfig.length() > 0) generationConfig.put("imageConfig", imageConfig)

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", generationConfig)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault(text.take(300))
                throw GenerationException("API error ${response.code}: $msg")
            }
            parseImage(JSONObject(text))
        }
    }

    private fun inlineImage(bytes: ByteArray): JSONObject = JSONObject().put(
        "inline_data",
        JSONObject()
            .put("mime_type", "image/jpeg")
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
    )

    private fun parseImage(json: JSONObject): ByteArray {
        json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
            throw GenerationException("The request was blocked by the model's safety filter ($it). Try different photos.")
        }
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw GenerationException("The model returned no result. Please try again.")
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val modelText = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
            if (inline != null) {
                val data = inline.optString("data")
                if (data.isNotEmpty()) return Base64.decode(data, Base64.DEFAULT)
            }
            part.optString("text").takeIf { it.isNotBlank() }?.let { modelText.append(it).append(' ') }
        }
        val reason = candidate.optString("finishReason")
        val detail = modelText.toString().trim().ifEmpty { "finish reason: $reason" }
        throw GenerationException("No image was returned. Model said: $detail")
    }

    companion object {
        val SUPPORTED_RATIOS = listOf(
            "1:1" to 1.0, "2:3" to 2.0 / 3, "3:2" to 1.5, "3:4" to 0.75, "4:3" to 4.0 / 3,
            "4:5" to 0.8, "5:4" to 1.25, "9:16" to 9.0 / 16, "16:9" to 16.0 / 9, "21:9" to 21.0 / 9,
        )

        /** Picks the supported aspect ratio closest to the reference image's shape. */
        fun closestRatio(width: Int, height: Int): String {
            if (width <= 0 || height <= 0) return "1:1"
            val r = width.toDouble() / height
            return SUPPORTED_RATIOS.minBy { kotlin.math.abs(kotlin.math.ln(it.second / r)) }.first
        }
    }
}
