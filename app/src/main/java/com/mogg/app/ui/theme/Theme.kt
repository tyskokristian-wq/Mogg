package com.mogg.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFB388FF)
val Good = Color(0xFF7CFFB2)
val Warn = Color(0xFFFFC46B)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0B33),
    secondary = Good,
    background = Color(0xFF0B0B10),
    surface = Color(0xFF0B0B10),
    surfaceVariant = Color(0xFF1A1A24),
    surfaceContainer = Color(0xFF15151C),
    surfaceContainerHigh = Color(0xFF1C1C26),
    surfaceContainerHighest = Color(0xFF22222E),
    onSurface = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFFA9A9B8),
    error = Color(0xFFFF6B7A),
)

@Composable
fun MoggTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
