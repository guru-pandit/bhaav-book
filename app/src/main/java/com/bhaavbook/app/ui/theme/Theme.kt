package com.bhaavbook.app.ui.theme

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
import com.bhaavbook.app.data.settings.ThemeOption

private val ChaitanyaLightColorScheme = lightColorScheme(
    primary              = Terracotta,
    onPrimary            = Cream,
    primaryContainer     = CreamDark,
    onPrimaryContainer   = Maroon,
    secondary            = Maroon,
    onSecondary          = Cream,
    secondaryContainer   = GoldLight,
    onSecondaryContainer = Maroon,
    tertiary             = Gold,
    onTertiary           = Charcoal,
    background           = Cream,
    onBackground         = Charcoal,
    surface              = Cream,
    onSurface            = Charcoal,
    surfaceVariant       = CreamDark,
    onSurfaceVariant     = CharcoalVariant,
    outline              = Gold,
    error                = ErrorRed,
    errorContainer       = ErrorContainer
)

private val ChaitanyaDarkColorScheme = darkColorScheme(
    primary              = Terracotta,
    onPrimary            = Cream,
    primaryContainer     = MaroonDark,
    onPrimaryContainer   = Cream,
    secondary            = Gold,
    onSecondary          = MaroonDark,
    secondaryContainer   = Maroon,
    onSecondaryContainer = GoldLight,
    tertiary             = GoldLight,
    onTertiary           = Charcoal,
    background           = Charcoal,
    onBackground         = Cream,
    surface              = MaroonDark,
    onSurface            = Cream,
    surfaceVariant       = Maroon,
    onSurfaceVariant     = CreamDark,
    outline              = Gold,
    error                = ErrorRed,
    errorContainer       = ErrorContainer
)

@Composable
fun BhaavBookTheme(
    themeOption: ThemeOption = ThemeOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeOption) {
        ThemeOption.DARK   -> true
        ThemeOption.LIGHT  -> false
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) ChaitanyaDarkColorScheme else ChaitanyaLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.secondary.toArgb() // Maroon header bar
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
