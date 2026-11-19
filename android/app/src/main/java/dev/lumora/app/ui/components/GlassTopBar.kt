package dev.lumora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lumora.app.R
import dev.lumora.app.data.BrowseMode
import dev.lumora.app.ui.theme.LocalAccent
import dev.lumora.app.ui.theme.LumoraColors
import kotlinx.coroutines.launch

private val BarShape = RoundedCornerShape(24.dp)

/**
 * Floating "glass" header: translucent elevated surface, hairline border,
 * a soft accent bloom, and the logo · mode switcher · quick actions.
 */
@Composable
fun GlassTopBar(
    mode: BrowseMode,
    desktopMode: Boolean,
    onModeChange: (BrowseMode) -> Unit,
    onRefresh: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val refreshSpin = remember { Animatable(0f) }

    Row(
        modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(56.dp)
            .shadow(18.dp, BarShape, ambientColor = accent.primary.copy(alpha = 0.5f), spotColor = Color.Black)
            .clip(BarShape)
            .background(LumoraColors.Surface.copy(alpha = 0.82f))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.primary.copy(alpha = 0.16f),
                        Color.Transparent,
                        accent.secondary.copy(alpha = 0.10f),
                    ),
                ),
            )
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)),
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), BarShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = 34.dp)
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
        ModeSwitcher(mode = mode, onModeChange = onModeChange)
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            BarAction(
                icon = Icons.Rounded.Refresh,
                label = stringResource(R.string.action_refresh),
                rotation = refreshSpin.value,
                onClick = {
                    onRefresh()
                    scope.launch {
                        refreshSpin.snapTo(0f)
                        refreshSpin.animateTo(360f, tween(650, easing = FastOutSlowInEasing))
                    }
                },
            )
            BarAction(
                icon = if (desktopMode) Icons.Rounded.DesktopWindows else Icons.Rounded.Smartphone,
                label = stringResource(R.string.action_desktop),
                active = desktopMode,
                onClick = onToggleDesktop,
            )
            BarAction(
                icon = Icons.Rounded.Tune,
                label = stringResource(R.string.action_settings),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    rotation: Float = 0f,
) {
    val tint by animateColorAsState(
        if (active) LocalAccent.current.primary else LumoraColors.OnSurface.copy(alpha = 0.86f),
        label = "barActionTint",
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp).rotate(rotation))
    }
}
