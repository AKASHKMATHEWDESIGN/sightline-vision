package dev.lumora.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.lumora.app.ui.theme.AccentPalette
import dev.lumora.app.ui.theme.LocalAccent

/**
 * The Lumora mark, drawn entirely in Canvas: a gradient squircle holding a
 * soft-cornered play glyph, with two "sound-wave" arcs orbiting its left side.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    accent: AccentPalette = LocalAccent.current,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val radius = CornerRadius(s * 0.3f, s * 0.3f)

        // Glow + body
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(accent.primary, accent.secondary),
                start = Offset.Zero,
                end = Offset(s, s),
            ),
            cornerRadius = radius,
        )
        // Glass highlight on the top half
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = s * 0.6f,
            ),
            cornerRadius = radius,
            size = Size(s, s * 0.6f),
        )

        // Rounded play triangle
        val cx = s * 0.56f
        val cy = s * 0.5f
        val h = s * 0.36f
        val play = Path().apply {
            moveTo(cx - h * 0.42f, cy - h * 0.5f)
            lineTo(cx + h * 0.5f, cy)
            lineTo(cx - h * 0.42f, cy + h * 0.5f)
            close()
        }
        drawPath(play, Color.White)
        drawPath(play, Color.White, style = Stroke(width = s * 0.06f, join = androidx.compose.ui.graphics.StrokeJoin.Round))

        // Orbiting sound arcs
        val arcStroke = Stroke(width = s * 0.055f, cap = StrokeCap.Round)
        drawArc(
            color = Color.White.copy(alpha = 0.85f),
            startAngle = 125f, sweepAngle = 110f, useCenter = false,
            topLeft = Offset(s * 0.2f, s * 0.22f), size = Size(s * 0.56f, s * 0.56f),
            style = arcStroke,
        )
        drawArc(
            color = Color.White.copy(alpha = 0.45f),
            startAngle = 130f, sweepAngle = 100f, useCenter = false,
            topLeft = Offset(s * 0.1f, s * 0.12f), size = Size(s * 0.76f, s * 0.76f),
            style = arcStroke,
        )
    }
}

@Preview
@Composable
private fun AppLogoPreview() = AppLogo(size = 96.dp)
