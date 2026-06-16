package com.ghostminator.scholar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F0F1),
    onPrimaryContainer = Slate,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    tertiary = Amber,
    onTertiary = Ink,
    background = Sand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFB8C4CE),
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Ink,
    primaryContainer = Color(0xFF1E4A4C),
    onPrimaryContainer = Color(0xFFB8E8EA),
    secondary = Color(0xFF8FA4B8),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF2A3644),
    onSecondaryContainer = Color(0xFFD8E2EA),
    tertiary = Amber,
    onTertiary = Ink,
    background = Color(0xFF121820),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF1A2332),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF2A3644),
    onSurfaceVariant = Color(0xFF9AA8B8),
    outline = Color(0xFF4A5868),
)

@Composable
fun SCHOLARTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
