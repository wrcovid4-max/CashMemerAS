package com.cashmemer.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * One spacing and shape scale for the whole app. Screens were drifting —
 * 8/12/16/24dp picked ad hoc per file — which is most of why the UI read as
 * untidy. Everything now steps through these.
 */
object Dimens {
    /** Gap between related items inside a card. */
    val gapTight = 8.dp

    /** Default gap between fields and rows. */
    val gap = 12.dp

    /** Gap between cards, and the screen's own edge padding. */
    val gapWide = 16.dp

    /** Breathing room above a new section heading. */
    val gapSection = 24.dp

    val cardPadding = 16.dp
    val screenPadding = 16.dp

    val cardCorner = RoundedCornerShape(18.dp)
    val fieldCorner = RoundedCornerShape(12.dp)
    val pillCorner = RoundedCornerShape(50)

    val iconGap = 8.dp
    val touchTarget = 48.dp
    val logoSize = 44.dp
}
