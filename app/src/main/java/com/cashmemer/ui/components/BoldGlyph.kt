package com.cashmemer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A heavier-looking icon.
 *
 * Material's vector icons have a single fixed stroke weight and no "bold" axis,
 * so to make them read thicker we draw the same glyph a few times nudged a
 * fraction of a dp to each corner and once in the centre. The overlaps fatten
 * the strokes — a faux-bold that works for any icon without swapping the set.
 *
 * The signature mirrors Material3's [Icon] (imageVector overload) exactly, so
 * call sites can use it as a drop-in replacement.
 */
@Composable
fun BoldGlyph(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val d = 0.75.dp
    Box {
        Icon(imageVector, null, modifier.offset(x = d, y = d), tint)
        Icon(imageVector, null, modifier.offset(x = -d, y = -d), tint)
        Icon(imageVector, null, modifier.offset(x = d, y = -d), tint)
        Icon(imageVector, null, modifier.offset(x = -d, y = d), tint)
        Icon(imageVector, contentDescription, modifier, tint)
    }
}
