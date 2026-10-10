package com.cashmemer.core.ui.theme

import androidx.compose.ui.graphics.Color

// Halloween palette: pumpkin orange, parchment, and midnight purple.
// Names are kept from the old green theme so nothing else has to change.
// (Old note:) Neon green brand. It is bright enough that text on top of it has
// to be near-black rather than white — see onPrimary in Theme.kt — otherwise
// labels on buttons wash out. Kept a touch below pure #39FF14 so large filled
// areas do not vibrate.
val BrandGreen = if (Holiday.HALLOWEEN) Color(0xFFE8680C) else Color(0xFF12D63B)
val BrandGreenDark = if (Holiday.HALLOWEEN) Color(0xFFA94A06) else Color(0xFF0A7D22)
val BrandGreenLight = if (Holiday.HALLOWEEN) Color(0xFFFFA65C) else Color(0xFF5CF06F)
val BrandGreenContainer = if (Holiday.HALLOWEEN) Color(0xFFFFE2C7) else Color(0xFFC9FBC8)
val OnBrandGreenContainer = if (Holiday.HALLOWEEN) Color(0xFF4A2300) else Color(0xFF06380F)

val PaperBackground = if (Holiday.HALLOWEEN) Color(0xFFFFF8F0) else Color(0xFFF8F9EF)
val PaperSurface = if (Holiday.HALLOWEEN) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)
val PaperSurfaceVariant = if (Holiday.HALLOWEEN) Color(0xFFFBEBDA) else Color(0xFFEDF0E2)

/** Card hairline. Darkened from the old value, which was invisible on white. */
val PaperOutline = if (Holiday.HALLOWEEN) Color(0xFFE0B98E) else Color(0xFFC9D0B6)

val InkPrimary = if (Holiday.HALLOWEEN) Color(0xFF1C1410) else Color(0xFF11150C)
val InkSecondary = if (Holiday.HALLOWEEN) Color(0xFF5A4636) else Color(0xFF4A5140)

// A deeper, darker red for the trash / destructive buttons, so "delete" reads
// as a warning next to the bright green rather than a soft pink.
val DangerRed = if (Holiday.HALLOWEEN) Color(0xFF9B1C15) else Color(0xFF9B1C15)
val DangerContainer = if (Holiday.HALLOWEEN) Color(0xFFFBD8D6) else Color(0xFFFBD8D6)
val DangerLight = if (Holiday.HALLOWEEN) Color(0xFFFF6B60) else Color(0xFFFF6B60)
/** Solid fill for a destructive button that should look dangerous, not outlined. */
val DangerButton = if (Holiday.HALLOWEEN) Color(0xFF8E1710) else Color(0xFF8E1710)
/**
 * Icon colour for anything sitting on [DangerButton]. Deliberately its own
 * token rather than the theme's `onError` — `onError` is meant to pair with
 * the theme's `error` colour, which changes between light and dark, while
 * DangerButton is a fixed dark red in both. Material3's baseline dark
 * `onError` is itself a dark maroon (~#601410); on top of DangerButton
 * (#8E1710) that is a ~1.4:1 contrast, close to invisible. This off-white
 * stays >9:1 against DangerButton in either theme.
 */
val OnDangerButton = if (Holiday.HALLOWEEN) Color(0xFFFBEAE8) else Color(0xFFFBEAE8)

// Dark scheme — same hue family, lifted for legibility on black.
val DarkBackground = if (Holiday.HALLOWEEN) Color(0xFF120A1A) else Color(0xFF11150C)
val DarkSurface = if (Holiday.HALLOWEEN) Color(0xFF1E1430) else Color(0xFF1A2013)
val DarkSurfaceVariant = if (Holiday.HALLOWEEN) Color(0xFF2C1F45) else Color(0xFF2A3320)
val DarkOutline = if (Holiday.HALLOWEEN) Color(0xFF4B3A66) else Color(0xFF454F3B)
val DarkGreen = if (Holiday.HALLOWEEN) Color(0xFFFF9A3D) else Color(0xFF39FF14)
val DarkGreenContainer = if (Holiday.HALLOWEEN) Color(0xFF4A2A0A) else Color(0xFF10420F)
