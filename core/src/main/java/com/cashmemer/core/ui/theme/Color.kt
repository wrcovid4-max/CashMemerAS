package com.cashmemer.core.ui.theme

import androidx.compose.ui.graphics.Color

// Halloween palette: pumpkin orange, parchment, and midnight purple.
// Names are kept from the old green theme so nothing else has to change.
// (Old note:) Neon green brand. It is bright enough that text on top of it has
// to be near-black rather than white — see onPrimary in Theme.kt — otherwise
// labels on buttons wash out. Kept a touch below pure #39FF14 so large filled
// areas do not vibrate.
val BrandGreen = Color(0xFFE8680C)
val BrandGreenDark = Color(0xFFA94A06)
val BrandGreenLight = Color(0xFFFFA65C)
val BrandGreenContainer = Color(0xFFFFE2C7)
val OnBrandGreenContainer = Color(0xFF4A2300)

val PaperBackground = Color(0xFFFFF8F0)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceVariant = Color(0xFFFBEBDA)

/** Card hairline. Darkened from the old value, which was invisible on white. */
val PaperOutline = Color(0xFFE0B98E)

val InkPrimary = Color(0xFF1C1410)
val InkSecondary = Color(0xFF5A4636)

// A deeper, darker red for the trash / destructive buttons, so "delete" reads
// as a warning next to the bright green rather than a soft pink.
val DangerRed = Color(0xFF9B1C15)
val DangerContainer = Color(0xFFFBD8D6)
val DangerLight = Color(0xFFFF6B60)
/** Solid fill for a destructive button that should look dangerous, not outlined. */
val DangerButton = Color(0xFF8E1710)
/**
 * Icon colour for anything sitting on [DangerButton]. Deliberately its own
 * token rather than the theme's `onError` — `onError` is meant to pair with
 * the theme's `error` colour, which changes between light and dark, while
 * DangerButton is a fixed dark red in both. Material3's baseline dark
 * `onError` is itself a dark maroon (~#601410); on top of DangerButton
 * (#8E1710) that is a ~1.4:1 contrast, close to invisible. This off-white
 * stays >9:1 against DangerButton in either theme.
 */
val OnDangerButton = Color(0xFFFBEAE8)

// Dark scheme — same hue family, lifted for legibility on black.
val DarkBackground = Color(0xFF120A1A)
val DarkSurface = Color(0xFF1E1430)
val DarkSurfaceVariant = Color(0xFF2C1F45)
val DarkOutline = Color(0xFF4B3A66)
val DarkGreen = Color(0xFFFF9A3D)
val DarkGreenContainer = Color(0xFF4A2A0A)
