package dev.lumora.app.ui

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.lumora.app.data.BrowseMode
import dev.lumora.app.ui.components.FullscreenVideo
import dev.lumora.app.ui.components.GlassTopBar
import dev.lumora.app.ui.components.GradientProgressBar
import dev.lumora.app.ui.components.MediaWebView
import dev.lumora.app.ui.components.SettingsActions
import dev.lumora.app.ui.components.SettingsSheet
import dev.lumora.app.ui.theme.LumoraColors
import dev.lumora.app.web.WebController
import kotlinx.coroutines.CancellationException

/** Callbacks the root screen needs from the Activity. */
data class ScreenActions(
    val onModeChange: (BrowseMode) -> Unit,
    val onRefresh: () -> Unit,
    val onToggleDesktop: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissSettings: () -> Unit,
    val onBack: () -> Unit,
    val onExitFullscreen: () -> Unit,
    val settings: SettingsActions,
)

@Composable
fun LumoraScreen(
    state: UiState,
    controller: WebController,
    fullscreenView: View?,
    actions: ScreenActions,
) {
    val chromeVisible = !state.isInPip && fullscreenView == null

    // Predictive back: the page card shrinks and slides with the gesture,
    // then navigates back through WebView history on commit.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    PredictiveBackHandler(enabled = state.canGoBack && fullscreenView == null && !state.showSettings) { events ->
        try {
            events.collect { event ->
                backProgress = event.progress
                swipeEdge = event.swipeEdge
            }
            actions.onBack()
        } catch (_: CancellationException) {
            // Gesture cancelled — just spring back.
        } finally {
            backProgress = 0f
        }
    }
    BackHandler(enabled = fullscreenView != null, onBack = actions.onExitFullscreen)

    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .background(LumoraColors.Background),
    ) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                GlassTopBar(
                    mode = state.mode,
                    desktopMode = state.settings.desktopMode,
                    onModeChange = actions.onModeChange,
                    onRefresh = actions.onRefresh,
                    onToggleDesktop = actions.onToggleDesktop,
                    onOpenSettings = actions.onOpenSettings,
                    modifier = Modifier.statusBarsPadding(),
                )
            }

            val pageShape = if (chromeVisible) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) else RoundedCornerShape(0.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (chromeVisible) {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        val p = backProgress
                        val scale = 1f - 0.08f * p
                        scaleX = scale
                        scaleY = scale
                        val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                        translationX = direction * with(density) { 28.dp.toPx() } * p
                        shape = RoundedCornerShape((22 + 10 * p).dp)
                        clip = p > 0f
                    }
                    .clip(pageShape),
            ) {
                MediaWebView(controller, Modifier.fillMaxSize())
                if (chromeVisible) {
                    GradientProgressBar(state.progress, Modifier.align(Alignment.TopCenter))
                }
            }
        }

        if (fullscreenView != null) {
            FullscreenVideo(fullscreenView)
        }

        if (state.showSettings && chromeVisible) {
            SettingsSheet(
                settings = state.settings,
                blockedCount = state.blockedCount,
                nowPlaying = state.nowPlaying,
                actions = actions.settings,
                onDismiss = actions.onDismissSettings,
            )
        }
    }
}
