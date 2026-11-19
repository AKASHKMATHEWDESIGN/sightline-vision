package dev.lumora.app.ui.components

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import dev.lumora.app.ui.theme.LocalAccent
import dev.lumora.app.web.WebController

/**
 * Hosts the long-lived WebView (wrapped in its pull-to-refresh container)
 * inside Compose. The view is owned by [WebController], so it survives
 * recomposition and configuration changes.
 */
@Composable
fun MediaWebView(controller: WebController, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.primary.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { controller.container.also { it.detachFromParent() } },
        update = { controller.setAccentColor(accent) },
    )
}

/** Full-screen video surface handed to us by WebChromeClient.onShowCustomView. */
@Composable
fun FullscreenVideo(view: View, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        key(view) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    FrameLayout(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        view.detachFromParent()
                        addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = { it.removeAllViews() },
            )
        }
    }
}

private fun View.detachFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
