package com.mogg.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Основной зелёный — для текста/кнопок (читается на белом). */
val Accent = Color(0xFF1E9E5A)
/** Светло-зелёный фон карточек «сделано», чипов и т.д. */
val LightGreen = Color(0xFFDFF7E8)
val Mint = Color(0xFFC9F2E3)
val SkyBlue = Color(0xFFDDEFFF)
val Good = Color(0xFF1E9E5A)
val Warn = Color(0xFFB26A00)
val WarnBg = Color(0xFFFFF3DD)

private val scheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = LightGreen,
    onPrimaryContainer = Color(0xFF0B3D22),
    secondary = Color(0xFF3BB98A),
    onSecondary = Color.White,
    secondaryContainer = Mint,
    onSecondaryContainer = Color(0xFF0B3D2C),
    tertiary = Color(0xFF3A8EDB),
    tertiaryContainer = SkyBlue,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF1F5F2),
    onSurfaceVariant = Color(0xFF5E6762),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAF8),
    surfaceContainer = Color(0xFFF3F7F4),
    surfaceContainerHigh = Color(0xFFEEF5F0),
    surfaceContainerHighest = Color(0xFFE6F0EA),
    outline = Color(0xFFCFDAD3),
    outlineVariant = Color(0xFFE2EAE5),
    error = Color(0xFFD93848),
)

@Composable
fun MoggTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
