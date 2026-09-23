package com.application.timbremini.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun createDarkColorScheme(palette: TimbreColorPalette) = darkColorScheme(
    primary = palette.accent,
    onPrimary = palette.onAccent,
    primaryContainer = palette.surface2,
    onPrimaryContainer = palette.accent,
    secondary = palette.textSecondary,
    onSecondary = palette.background,
    secondaryContainer = palette.surface2,
    onSecondaryContainer = palette.textPrimary,
    background = palette.background,
    onBackground = palette.textPrimary,
    surface = palette.surface1,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surface2,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.hairline,
    outlineVariant = palette.hairlineStrong,
    error = palette.danger,
    onError = palette.background
)

private fun createLightColorScheme(palette: TimbreColorPalette) = lightColorScheme(
    primary = palette.accent,
    onPrimary = palette.onAccent,
    primaryContainer = palette.surface2,
    onPrimaryContainer = palette.accent,
    secondary = palette.textSecondary,
    onSecondary = palette.surface1,
    secondaryContainer = palette.surface2,
    onSecondaryContainer = palette.textPrimary,
    background = palette.background,
    onBackground = palette.textPrimary,
    surface = palette.surface1,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.surface2,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.hairline,
    outlineVariant = palette.hairlineStrong,
    error = palette.danger,
    onError = palette.surface1
)

@Composable
fun TimbreMiniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) createDarkColorScheme(palette) else createLightColorScheme(palette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = palette.background.toArgb()
                window.navigationBarColor = palette.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalTimbreColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
