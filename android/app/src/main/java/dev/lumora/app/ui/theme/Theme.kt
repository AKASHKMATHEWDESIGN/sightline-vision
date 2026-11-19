package dev.lumora.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.lumora.app.data.BrowseMode

object LumoraColors {
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF16161A)
    val SurfaceHigh = Color(0xFF1E1E24)
    val Outline = Color(0x1FFFFFFF)
    val OnSurface = Color(0xFFF2F2F5)
    val OnSurfaceMuted = Color(0xFF9A9AA6)

    val Crimson = Color(0xFFFF2A54)
    val Ember = Color(0xFFFF7A45)
    val ElectricPurple = Color(0xFF8A2BE2)
    val Teal = Color(0xFF00E0C6)
}

/** The live (animated) accent pair for the current mode. */
@Immutable
data class AccentPalette(val primary: Color, val secondary: Color) {
    val gradient: Brush get() = Brush.linearGradient(listOf(primary, secondary))
    val horizontalGradient: Brush get() = Brush.horizontalGradient(listOf(primary, secondary))
}

fun BrowseMode.palette() = when (this) {
    BrowseMode.Video -> AccentPalette(LumoraColors.Crimson, LumoraColors.Ember)
    BrowseMode.Music -> AccentPalette(LumoraColors.ElectricPurple, LumoraColors.Teal)
}

val LocalAccent = staticCompositionLocalOf { BrowseMode.Video.palette() }

private val LumoraTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    )
}

@Composable
fun LumoraTheme(mode: BrowseMode, content: @Composable () -> Unit) {
    val target = mode.palette()
    val spec = tween<Color>(durationMillis = 650, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, spec, label = "accentPrimary")
    val secondary by animateColorAsState(target.secondary, spec, label = "accentSecondary")
    val accent = AccentPalette(primary, secondary)

    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = Color.White,
        secondary = secondary,
        onSecondary = Color.Black,
        tertiary = secondary,
        background = LumoraColors.Background,
        onBackground = LumoraColors.OnSurface,
        surface = LumoraColors.Surface,
        onSurface = LumoraColors.OnSurface,
        surfaceVariant = LumoraColors.SurfaceHigh,
        onSurfaceVariant = LumoraColors.OnSurfaceMuted,
        surfaceContainer = LumoraColors.Surface,
        surfaceContainerHigh = LumoraColors.SurfaceHigh,
        surfaceContainerLow = LumoraColors.Surface,
        outline = LumoraColors.Outline,
        outlineVariant = LumoraColors.Outline,
    )

    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = LumoraTypography, content = content)
    }
}
