package dev.lumora.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * WebView that can pretend to stay visible after the app is backgrounded.
 *
 * Chromium pauses media (and throttles timers) when the hosting window
 * becomes invisible. When background playback is enabled we report the
 * window as still visible so audio continues with the screen off; the
 * injected script handles the page-level Visibility API.
 */
@SuppressLint("ViewConstructor")
class LumoraWebView(context: Context) : WebView(context) {

    var keepAliveInBackground: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (keepAliveInBackground && visibility != View.GONE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }
}
