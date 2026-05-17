package com.seguranca.rural.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Segurança Rural enforces dark mode — field use under direct sunlight benefits
// from high-contrast dark backgrounds over white.
private val DarkColorScheme = darkColorScheme(
    primary       = ForestGreen80,
    secondary     = ForestGreenGrey80,
    tertiary      = Moss80,
    background    = BackgroundDeep,
    surface       = SurfaceCard,
    error         = EmergencyRed,
    onPrimary     = BackgroundDeep,
    onSecondary   = BackgroundDeep,
    onBackground  = TextOnDark,
    onSurface     = TextOnDark,
)

private val LightColorScheme = lightColorScheme(
    primary       = ForestGreen40,
    secondary     = ForestGreenGrey40,
    tertiary      = Moss40,
    error         = EmergencyRed,
)

@Composable
fun SegurancaRuralTheme(
    // Always prefer dark theme for field use
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundDeep.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
