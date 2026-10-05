package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DjDarkColorScheme = darkColorScheme(
    primary = DeckAPrimary,
    secondary = DeckBPrimary,
    tertiary = SyncColor,
    background = DjDarkBg,
    surface = DjPanelBg,
    surfaceVariant = DjCardBg,
    onPrimary = DjDarkBg,
    onSecondary = DjDarkBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DjBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // DJ software is fundamentally optimized for dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = DjDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DjDarkBg.toArgb()
            window.navigationBarColor = DjDarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
