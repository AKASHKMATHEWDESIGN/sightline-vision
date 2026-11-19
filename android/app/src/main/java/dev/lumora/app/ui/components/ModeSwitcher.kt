package dev.lumora.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.lumora.app.R
import dev.lumora.app.data.BrowseMode
import dev.lumora.app.ui.theme.LocalAccent
import dev.lumora.app.ui.theme.LumoraColors

private val SegmentWidth = 78.dp
private val PillHeight = 38.dp
private val Inset = 3.dp

/** Pill-shaped Video | Music toggle with a spring-animated gradient thumb. */
@Composable
fun ModeSwitcher(
    mode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    val haptics = LocalHapticFeedback.current
    val thumbOffset by animateDpAsState(
        targetValue = if (mode == BrowseMode.Video) 0.dp else SegmentWidth,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "thumbOffset",
    )

    Box(
        modifier
            .height(PillHeight)
            .width(SegmentWidth * 2 + Inset * 2)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(Inset),
    ) {
        // Sliding thumb
        Box(
            Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .width(SegmentWidth)
                .fillMaxHeight()
                .shadow(10.dp, CircleShape, ambientColor = accent.primary, spotColor = accent.primary)
                .clip(CircleShape)
                .background(accent.gradient),
        )
        Row(Modifier.selectableGroup()) {
            BrowseMode.entries.forEach { item ->
                Segment(
                    label = stringResource(if (item == BrowseMode.Video) R.string.mode_video else R.string.mode_music),
                    icon = if (item == BrowseMode.Video) Icons.Rounded.SmartDisplay else Icons.Rounded.GraphicEq,
                    selected = item == mode,
                    onClick = {
                        if (item != mode) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeChange(item)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Segment(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val contentColor by animateColorAsState(
        if (selected) Color.White else LumoraColors.OnSurfaceMuted,
        label = "segmentContent",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "segmentScale",
    )
    Row(
        Modifier
            .width(SegmentWidth)
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .scale(scale),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}
