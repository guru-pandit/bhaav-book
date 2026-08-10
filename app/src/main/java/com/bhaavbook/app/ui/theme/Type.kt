package com.bhaavbook.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Chaitanya Stores typography
//
// Headings / brand: Serif (maps to Noto Serif on device — no bundled font
// files, so the APK stays small and text renders in every Indic script the
// phone already supports).
// Body / list rows / prices: Sans-serif.
//
// Deliberately no `color` on any style: colour is the colour scheme's job, so
// the same styles read correctly in both light and dark mode.
// ---------------------------------------------------------------------------

val HeadingFontFamily = FontFamily.Serif
val BodyFontFamily = FontFamily.SansSerif

/**
 * Monospaced digits for prices. Without `tnum`, a `1` is narrower than a `7`,
 * so a column of price pills jitters as the search results change under it.
 */
val TabularFigures = TextStyle(
    fontFamily = BodyFontFamily,
    fontFeatureSettings = "tnum"
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 62.sp
    ),
    displayMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 50.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = HeadingFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)
