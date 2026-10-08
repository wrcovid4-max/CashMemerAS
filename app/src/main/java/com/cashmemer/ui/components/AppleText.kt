package com.cashmemer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.cashmemer.R
import com.cashmemer.core.data.AppleLogo

private const val APPLE_KEY = "apple-logo"

/**
 * Text that draws the apple emoji as the real Apple logo (a bundled vector), so it
 * looks the same on every device instead of depending on the emoji font.
 */
@Composable
fun AppleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (!text.contains(AppleLogo.APPLE_EMOJI)) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val tint = if (color.isSpecified) color else LocalContentColor.current
    val size = if (style.fontSize.isSpecified) style.fontSize else 16.sp
    val logo = InlineTextContent(
        placeholder = Placeholder(
            width = size,
            height = size,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_apple_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
        )
    }

    val annotated: AnnotatedString = buildAnnotatedString {
        text.split(AppleLogo.APPLE_EMOJI).forEachIndexed { index, part ->
            if (index > 0) appendInlineContent(APPLE_KEY, "Apple")
            append(part)
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = mapOf(APPLE_KEY to logo),
    )
}
