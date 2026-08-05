package com.cashmemer.core.ui.theme

import androidx.compose.ui.graphics.Color

// Neon green brand, per request. It is bright enough that text on top of it has
// to be near-black rather than white — see onPrimary in Theme.kt — otherwise
// labels on buttons wash out. Kept a touch below pure #39FF14 so large filled
// areas do not vibrate.
val BrandGreen = Color(0xFF12D63B)
val BrandGreenDark = Color(0xFF0A7D22)
val BrandGreenLight = Color(0xFF5CF06F)
val BrandGreenContainer = Color(0xFFC9FBC8)
val OnBrandGreenContainer = Color(0xFF06380F)

val PaperBackground = Color(0xFFF8F9EF)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceVariant = Color(0xFFEDF0E2)

/** Card hairline. Darkened from the old value, which was invisible on white. */
val PaperOutline = Color(0xFFC9D0B6)

val InkPrimary = Color(0xFF11150C)
val InkSecondary = Color(0xFF4A5140)

// A deeper, darker red for the trash / destructive buttons, so "delete" reads
// as a warning next to the bright green rather than a soft pink.
val DangerRed = Color(0xFF9B1C15)
val DangerContainer = Color(0xFFFBD8D6)
val DangerLight = Color(0xFFFF6B60)
/** Solid fill for a destructive button that should look dangerous, not outlined. */
val DangerButton = Color(0xFF8E1710)

// Dark scheme — same hue family, lifted for legibility on black.
val DarkBackground = Color(0xFF11150C)
val DarkSurface = Color(0xFF1A2013)
val DarkSurfaceVariant = Color(0xFF2A3320)
val DarkOutline = Color(0xFF454F3B)
val DarkGreen = Color(0xFF39FF14)
val DarkGreenContainer = Color(0xFF10420F)
