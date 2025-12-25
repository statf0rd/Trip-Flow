package com.triloo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Triloo Theme
 * 
 * A warm, adventure-inspired theme with coral primary
 * and teal secondary accents. Optimized for travel UX.
 */

private val LightColorScheme = lightColorScheme(
    // Primary — Coral (main actions)
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralSubtle,
    onPrimaryContainer = CoralDark,

    // Secondary — Teal (secondary actions, success)
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealSubtle,
    onSecondaryContainer = TealDark,

    // Tertiary — Golden (accents, currency)
    tertiary = GoldenAccent,
    onTertiary = Slate900,
    tertiaryContainer = GoldenSubtle,
    onTertiaryContainer = GoldenDark,

    // Background & Surface
    background = Slate50,
    onBackground = Slate950,
    surface = Color.White,
    onSurface = Slate950,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,

    // Outline & Dividers
    outline = Slate400,
    outlineVariant = Slate300,

    // Error
    error = Error,
    onError = Color.White,
    errorContainer = ErrorLight,
    onErrorContainer = Error,

    // Inverse
    inverseSurface = Slate900,
    inverseOnSurface = Slate100,
    inversePrimary = CoralLight,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.32f),

    // Surface tint
    surfaceTint = CoralPrimary
)

private val DarkColorScheme = darkColorScheme(
    // Primary — Coral (lighter for dark mode)
    primary = CoralLight,
    onPrimary = Slate900,
    primaryContainer = CoralDark,
    onPrimaryContainer = CoralSubtle,

    // Secondary — Teal
    secondary = TealLight,
    onSecondary = Slate900,
    secondaryContainer = TealDark,
    onSecondaryContainer = TealSubtle,

    // Tertiary — Golden
    tertiary = GoldenLight,
    onTertiary = Slate900,
    tertiaryContainer = GoldenDark,
    onTertiaryContainer = GoldenSubtle,

    // Background & Surface
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,

    // Outline & Dividers
    outline = DarkBorder,
    outlineVariant = DarkBorder,

    // Error
    error = Color(0xFFF87171), // Lighter red for dark mode
    onError = Slate900,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = ErrorLight,

    // Inverse
    inverseSurface = Slate200,
    inverseOnSurface = Slate900,
    inversePrimary = CoralDark,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.5f),

    // Surface tint
    surfaceTint = CoralLight
)

@Composable
fun TrilooTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color to transparent for edge-to-edge
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrilooTypography,
        content = content
    )
}

@Preview(name = "Light Theme Preview", showBackground = true)
@Composable
private fun LightThemePreview() {
    TrilooTheme(darkTheme = false) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Triloo Light", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Коррал + тизер", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(name = "Dark Theme Preview", showBackground = true)
@Composable
private fun DarkThemePreview() {
    TrilooTheme(darkTheme = true) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Triloo Dark", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Теплый тёмный фон", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
