package com.triloo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.node.DelegatableNode
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Основная тема Triloo.
 *
 * Тёплая travel-палитра строится на коралловом primary и бирюзовом secondary,
 * чтобы интерфейс оставался живым, но не перегруженным.
 */

private val LightColorScheme = lightColorScheme(
    // Основной коралловый блок для главных действий.
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralSubtle,
    onPrimaryContainer = CoralDark,

    // Бирюзовый блок для вторичных действий и успешных состояний.
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealSubtle,
    onSecondaryContainer = TealDark,

    // Tertiary — золотой для акцентов и валюты.
    tertiary = GoldenAccent,
    onTertiary = Slate900,
    tertiaryContainer = GoldenSubtle,
    onTertiaryContainer = GoldenDark,

    // Фон и поверхности.
    background = Slate50,
    onBackground = Slate950,
    surface = Color.White,
    onSurface = Slate950,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,

    // Обводки и разделители.
    outline = Slate400,
    outlineVariant = Slate300,

    // Ошибки.
    error = Error,
    onError = Color.White,
    errorContainer = ErrorLight,
    onErrorContainer = Error,

    // Инверсные цвета.
    inverseSurface = Slate900,
    inverseOnSurface = Slate100,
    inversePrimary = CoralLight,

    // Затемняющая подложка.
    scrim = Color.Black.copy(alpha = 0.32f),

    // Тонирование поверхностей.
    surfaceTint = CoralPrimary
)

private val DarkColorScheme = darkColorScheme(
    // Более светлый коралл для тёмной темы.
    primary = CoralLight,
    onPrimary = Slate900,
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = CoralLight,

    // Бирюзовый блок.
    secondary = TealLight,
    onSecondary = Slate900,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = TealLight,

    // Tertiary — золотой.
    tertiary = GoldenLight,
    onTertiary = Slate900,
    tertiaryContainer = GoldenContainerDark,
    onTertiaryContainer = GoldenLight,

    // Фон и поверхности.
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,

    // Обводки и разделители.
    outline = DarkBorder,
    outlineVariant = DarkBorder,

    // Ошибки.
    error = Color(0xFFF87171), // Более светлый красный для тёмной темы.
    onError = Slate900,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorOnContainerDark,

    // Инверсные цвета.
    inverseSurface = Slate200,
    inverseOnSurface = Slate900,
    inversePrimary = CoralDark,

    // Затемняющая подложка.
    scrim = Color.Black.copy(alpha = 0.5f),

    // Тонирование поверхностей.
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
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TrilooTypography
    ) {
        // Глобально отключаем ripple на любых `clickable`/`combinedClickable`,
        // которые читают LocalIndication. Получаем iOS-подобное ощущение —
        // никаких белых «хитбоксов» при нажатии или удержании. Для отдельных
        // компонентов (если будет нужно) можно вернуть ripple точечно через
        // `CompositionLocalProvider(LocalIndication provides ripple())`.
        CompositionLocalProvider(LocalIndication provides NoIndication) {
            content()
        }
    }
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

private class NoIndicationNode : Modifier.Node()

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
