package com.sightline.facestyle

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedImage(val uri: Uri, val thumb: Bitmap)

data class UiState(
    val faces: List<PickedImage> = emptyList(),
    val reference: PickedImage? = null,
    val extraPrompt: String = "",
    val keepMyHair: Boolean = false,
    val consent: Boolean = false,
    val loading: Boolean = false,
    val result: Bitmap? = null,
    val error: String? = null,
    val message: String? = null,
    val apiKey: String = "",
    val model: String = MainViewModel.DEFAULT_MODEL,
) {
    val canGenerate get() = faces.isNotEmpty() && reference != null && consent && !loading
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            apiKey = prefs.getString(KEY_API, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL },
        ),
    )
    val state: StateFlow<UiState> = _state

    private val ctx get() = getApplication<Application>()

    fun addFaces(uris: List<Uri>) = viewModelScope.launch {
        val picked = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri -> runCatching { PickedImage(uri, ImageUtils.loadBitmap(ctx, uri, 400)) }.getOrNull() }
        }
        _state.update { it.copy(faces = (it.faces + picked).takeLast(MAX_FACES)) }
    }

    fun removeFace(index: Int) = _state.update { s ->
        s.copy(faces = s.faces.filterIndexed { i, _ -> i != index })
    }

    fun setReference(uri: Uri) = viewModelScope.launch {
        val picked = withContext(Dispatchers.IO) {
            runCatching { PickedImage(uri, ImageUtils.loadBitmap(ctx, uri, 800)) }.getOrNull()
        }
        if (picked == null) _state.update { it.copy(error = "Could not open that image.") }
        else _state.update { it.copy(reference = picked) }
    }

    fun setExtraPrompt(v: String) = _state.update { it.copy(extraPrompt = v) }
    fun setKeepMyHair(v: Boolean) = _state.update { it.copy(keepMyHair = v) }
    fun setConsent(v: Boolean) = _state.update { it.copy(consent = v) }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun clearResult() = _state.update { it.copy(result = null) }

    fun saveSettings(apiKey: String, model: String) {
        val k = apiKey.trim()
        val m = model.trim().ifBlank { DEFAULT_MODEL }
        prefs.edit().putString(KEY_API, k).putString(KEY_MODEL, m).apply()
        _state.update { it.copy(apiKey = k, model = m) }
    }

    fun generate() {
        val s = _state.value
        val ref = s.reference ?: return
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(error = "Add your Gemini API key in Settings (gear icon) first.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val refBmp = ImageUtils.loadBitmap(ctx, ref.uri, 1536)
                    val ratio = GeminiClient.closestRatio(refBmp.width, refBmp.height)
                    val refJpeg = ImageUtils.toJpeg(refBmp)
                    val faceJpegs = s.faces.map { ImageUtils.toJpeg(ImageUtils.loadBitmap(ctx, it.uri, 1024)) }
                    val prompt = PromptBuilder.build(faceJpegs.size, s.keepMyHair, s.extraPrompt)
                    GeminiClient(s.apiKey, s.model).generate(prompt, refJpeg, faceJpegs, ratio)
                }
                val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    ?: error("The returned image could not be decoded.")
                _state.update { it.copy(loading = false, result = bmp) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun saveResult() {
        val bmp = _state.value.result ?: return
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching { ImageUtils.saveToGallery(ctx, bmp) }
                    .fold({ "Saved to Pictures/FaceStyle" }, { "Save failed: ${it.message}" })
            }
            _state.update { it.copy(message = msg) }
        }
    }

    companion object {
        const val MAX_FACES = 3
        const val DEFAULT_MODEL = "gemini-3.1-flash-image"
        val MODEL_PRESETS = listOf(
            "gemini-3.1-flash-image" to "Nano Banana 2 — best with multiple face photos (recommended)",
            "gemini-nano-banana-2.1" to "Nano Banana 2.1 — newest fast model",
            "gemini-3-pro-image" to "Nano Banana Pro — highest detail, slower & pricier",
            "gemini-2.5-flash-image" to "Nano Banana (legacy) — the original trend model",
        )
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
    }
}
