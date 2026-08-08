package com.bhaavbook.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bhaavbook.app.data.settings.ThemeOption

private val LightColorScheme = lightColorScheme(
    primary          = Indigo40,
    onPrimary        = Indigo99,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary        = Saffron40,
    onSecondary      = Indigo99,
    secondaryContainer = Saffron90,
    onSecondaryContainer = Saffron40,
    tertiary         = Gold40,
    onTertiary       = Indigo99,
    tertiaryContainer = Gold90,
    onTertiaryContainer = Gold40,
    error            = Error40,
    errorContainer   = Error90,
    surface          = Indigo99,
    onSurface        = Indigo10,
    surfaceVariant   = NeutralVar90,
    onSurfaceVariant = NeutralVar30,
    outline          = NeutralVar80
)

private val DarkColorScheme = darkColorScheme(
    primary          = Indigo80,
    onPrimary        = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    secondary        = Saffron80,
    onSecondary      = Saffron40,
    secondaryContainer = Saffron40,
    onSecondaryContainer = Saffron90,
    tertiary         = Gold80,
    onTertiary       = Gold40,
    tertiaryContainer = Gold40,
    onTertiaryContainer = Gold90,
    error            = Error80,
    errorContainer   = Error40,
    surface          = Indigo10,
    onSurface        = Indigo90,
    surfaceVariant   = NeutralVar30,
    onSurfaceVariant = NeutralVar80,
    outline          = NeutralVar80
)

@Composable
fun BhaavBookTheme(
    themeOption: ThemeOption = ThemeOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeOption) {
        ThemeOption.DARK  -> true
        ThemeOption.LIGHT -> false
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
    }

    // Use Material You dynamic colours on Android 12+ when available
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
