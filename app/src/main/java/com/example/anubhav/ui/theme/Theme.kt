package com.example.anubhav.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = CalmAccent,
    onPrimary = Color.White,
    secondary = CalmAccentSecondary,
    onSecondary = Color.White,
    background = CalmBackground,
    onBackground = CalmTextPrimary,
    surface = CalmSurface,
    onSurface = CalmTextPrimary,
    surfaceVariant = CalmSurfaceVariant,
    onSurfaceVariant = CalmTextSecondary,
    outline = CalmBorder,
    outlineVariant = CalmBorderSubtle,
    error = CalmError
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0EFEA),
    onPrimary = Color(0xFF141517),
    secondary = Color(0xFF8E959E),
    onSecondary = Color(0xFF141517),
    background = Color(0xFF121316),
    onBackground = Color(0xFFF0EFEA),
    surface = Color(0xFF1A1C20),
    onSurface = Color(0xFFF0EFEA),
    surfaceVariant = Color(0xFF24272C),
    onSurfaceVariant = Color(0xFFA5ABB2),
    outline = Color(0xFF353940),
    outlineVariant = Color(0xFF23262B),
    error = Color(0xFFEF5350)
)

val MaterialTheme.calmTextPrimary: Color
    @Composable get() = colorScheme.onSurface

val MaterialTheme.calmTextSecondary: Color
    @Composable get() = colorScheme.onSurfaceVariant

val MaterialTheme.calmTextTertiary: Color
    @Composable get() = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

val MaterialTheme.calmBorderSubtle: Color
    @Composable get() = colorScheme.outlineVariant

@Composable
fun AnubhavTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}