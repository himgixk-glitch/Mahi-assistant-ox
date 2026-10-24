package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003840),
    onPrimaryContainer = NeonCyan,
    secondary = NeonPink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4C0028),
    onSecondaryContainer = NeonPink,
    tertiary = NeonPurple,
    onTertiary = Color.White,
    background = CyberDark,
    onBackground = TextWhite,
    surface = CyberDarkSurface,
    onSurface = TextWhite,
    surfaceVariant = CyberDarkSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = CyberBorder,
    error = NeonRed,
    onError = Color.White
)

@Composable
fun MahiAiTheme(
    content: @Composable () -> Unit
) {
    // Always use the immersive futuristic dark color scheme for Mahi AI
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
