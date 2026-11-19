package com.sightline.facestyle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFB98CFF),
    onPrimary = Color(0xFF1E0F33),
    secondary = Color(0xFFFF7AB6),
    background = Color(0xFF121016),
    surface = Color(0xFF1C1922),
    surfaceVariant = Color(0xFF2A2533),
    onBackground = Color(0xFFECE6F3),
    onSurface = Color(0xFFECE6F3),
    onSurfaceVariant = Color(0xFFBDB4C9),
    error = Color(0xFFFF8A80),
)

@Composable
fun FaceStyleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
