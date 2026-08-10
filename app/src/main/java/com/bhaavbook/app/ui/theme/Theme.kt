package com.bhaavbook.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.bhaavbook.app.data.settings.ThemeOption

private val LightColors = lightColorScheme(
    primary              = Terracotta,
    onPrimary            = Cream,
    primaryContainer     = CreamDark,
    onPrimaryContainer   = Maroon,
    secondary            = Maroon,
    onSecondary          = Cream,
    secondaryContainer   = GoldLight,
    onSecondaryContainer = MaroonDeep,
    tertiary             = Gold,
    onTertiary           = Cream,
    tertiaryContainer    = GoldLight,
    onTertiaryContainer  = MaroonDeep,
    background           = Cream,
    onBackground         = Charcoal,
    surface              = Cream,
    onSurface            = Charcoal,
    surfaceVariant       = CreamDark,
    onSurfaceVariant     = CharcoalVariant,
    surfaceContainer     = CreamDark,
    surfaceContainerHigh = CreamDeep,
    outline              = CreamDeep,
    outlineVariant       = CreamDeep,
    error                = ErrorRed,
    onError              = Cream,
    errorContainer       = ErrorContainer,
    onErrorContainer     = MaroonDeep,
    scrim                = Charcoal
)

private val DarkColors = darkColorScheme(
    primary              = TerracottaLite,
    onPrimary            = MaroonDeep,
    primaryContainer     = NightSurfaceHi,
    onPrimaryContainer   = TerracottaLite,
    // The top bar is `secondary` in both themes, so it stays a maroon — a gold
    // bar here would read as a different app after dark.
    secondary            = MaroonNight,
    onSecondary          = NightText,
    secondaryContainer   = NightSurfaceHi,
    onSecondaryContainer = GoldLight,
    tertiary             = GoldLight,
    onTertiary           = MaroonDeep,
    tertiaryContainer    = NightSurfaceHi,
    onTertiaryContainer  = GoldLight,
    background           = NightBase,
    onBackground         = NightText,
    surface              = NightBase,
    onSurface            = NightText,
    surfaceVariant       = NightSurface,
    onSurfaceVariant     = NightTextMuted,
    surfaceContainer     = NightSurface,
    surfaceContainerHigh = NightSurfaceHi,
    outline              = NightOutline,
    outlineVariant       = NightOutline,
    error                = ErrorRedLight,
    onError              = MaroonDeep,
    errorContainer       = ErrorContainerNight,
    onErrorContainer     = ErrorRedLight,
    scrim                = NightBase
)

/**
 * Slightly softer than Material's defaults — rounded but not pill-shaped,
 * which suits the warm retail feel without looking like a toy.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Colour-role conventions this app relies on, so the two schemes stay in step:
 *
 *  - `secondary` / `onSecondary` — the top app bar, and selected filter chips.
 *    A maroon in both themes.
 *  - `onPrimaryContainer` — heading text drawn on the page background. Maroon in
 *    light, warm terracotta in dark, legible in both. Headings must not use
 *    `secondary`, which is a *background* colour and disappears on dark.
 *  - `primary` — prices and primary actions.
 *  - `tertiary` — brand names.
 *  - `surfaceContainer` — cards, the search field, unselected chips.
 *
 * The top bar is a maroon in both themes, so status-bar icons are always light.
 * The navigation bar sits over the page background, so its icons follow the
 * theme. Bar *colours* are intentionally not set: they are ignored from
 * Android 15 on, where edge-to-edge is enforced and the bars are transparent.
 */
@Composable
fun BhaavBookTheme(
    themeOption: ThemeOption = ThemeOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeOption) {
        ThemeOption.DARK -> true
        ThemeOption.LIGHT -> false
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
