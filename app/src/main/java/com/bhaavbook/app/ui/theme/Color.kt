package com.bhaavbook.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Chaitanya Stores — traditional, warm, premium palette
//
// Colours live here as raw brand tokens. Screens should read them through
// MaterialTheme.colorScheme (see Theme.kt) so light and dark both work.
// ---------------------------------------------------------------------------

// ── Light / brand core ─────────────────────────────────────────────────────
val Cream           = Color(0xFFFBF3E7) // Page background
val CreamDark       = Color(0xFFF3E7D3) // Cards, search field, chips
val CreamDeep       = Color(0xFFE8D8BC) // Hairlines, pressed fills
val Terracotta      = Color(0xFFC1622D) // Primary — prices, buttons
val TerracottaDark  = Color(0xFFA34F22) // Primary pressed
val Maroon          = Color(0xFF6B1E23) // Top bars, headings
val MaroonDeep      = Color(0xFF4A1418) // Deep brand shade
val Gold            = Color(0xFFB98A22) // Accents, brand marks (darkened for AA on cream)
val GoldLight       = Color(0xFFE4C877) // Subtle highlight fill
val Charcoal        = Color(0xFF2A211C) // Body text — rich charcoal, never pure black
val CharcoalVariant = Color(0xFF5E524A) // Captions, secondary text

// ── Dark mode: warm near-black, not the usual blue-grey ────────────────────
val NightBase      = Color(0xFF17120F) // Background
val NightSurface   = Color(0xFF231C17) // Cards
val NightSurfaceHi = Color(0xFF2F2620) // Chips, search field, raised fills
val NightOutline   = Color(0xFF4C3C31) // Hairlines
val NightText      = Color(0xFFF4E9DA) // Body text
val NightTextMuted = Color(0xFFB9A795) // Captions
val TerracottaLite = Color(0xFFE58B51) // Primary on dark — brighter for contrast
val MaroonNight    = Color(0xFF3A1215) // Top bar on dark

// ── Semantic ───────────────────────────────────────────────────────────────
val ErrorRed            = Color(0xFF9E2A2B)
val ErrorRedLight       = Color(0xFFF3938E)
val ErrorContainer      = Color(0xFFFADBD8)
val ErrorContainerNight = Color(0xFF48191A)
