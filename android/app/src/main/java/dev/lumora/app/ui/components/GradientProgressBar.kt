package dev.lumora.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.lumora.app.ui.theme.LocalAccent

/**
 * Thin loading bar with an accent gradient and a travelling highlight.
 * Fades out once the page reaches 100 %.
 */
@Composable
fun GradientProgressBar(progress: Int, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val visible = progress in 1..99
    val animated by animateFloatAsState(
        targetValue = (progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.08f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progress",
    )
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 120 else 500), label = "alpha")
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .alpha(alpha),
    ) {
        val width = size.width * animated
        val radius = CornerRadius(size.height, size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(accent.primary, accent.secondary), endX = width),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
        val x = width * shimmer
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                startX = x - 60f,
                endX = x + 60f,
            ),
            topLeft = Offset(0f, 0f),
            size = Size(width, size.height),
            cornerRadius = radius,
        )
    }
}
