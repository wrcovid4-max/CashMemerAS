package com.cashmemer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashmemer.R
import com.cashmemer.core.ui.theme.DangerButton
import com.cashmemer.core.ui.theme.Dimens
import com.cashmemer.core.ui.theme.OnDangerButton
import com.cashmemer.ui.Destination

/**
 * One line of text that gets smaller until it fits, instead of being cut off
 * with an ellipsis or wrapped onto a second line.
 *
 * "Barcode Scan" on a half-width button and "Professional Receipt Organizer"
 * beside the language switch were both being chopped to "Barcode …". A label
 * a shade smaller reads fine; a label with its ending missing does not.
 *
 * It draws nothing until the size has settled, so there is no visible jump
 * while it steps down.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    /** How far it may shrink before it gives up and clips. */
    smallestScale: Float = 0.65f,
) {
    val startSize = if (style.fontSize == TextUnit.Unspecified) 14.sp else style.fontSize
    val smallest = startSize.value * smallestScale

    var size by remember(text, startSize) { mutableStateOf(startSize) }
    var settled by remember(text, startSize) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        style = style.copy(fontSize = size),
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && size.value > smallest) {
                size = (size.value * STEP).sp
            } else {
                settled = true
            }
        },
    )
}

/** Small enough to converge quickly, large enough not to look stepped. */
private const val STEP = 0.94f

/** App bar: mark, wordmark, tagline and the ENG / اردو switch. */
@Composable
fun BrandHeader(
    language: String,
    onLanguageChange: (String) -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The app draws edge to edge, so the header has to step below
                // the status bar itself — otherwise it sits on top of the clock.
                .statusBarsPadding()
                .padding(
                    horizontal = Dimens.screenPadding,
                    vertical = Dimens.gapTight,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Image, not Icon — Icon would flatten the mark to a single tint.
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = null,
                modifier = Modifier
                    .size(Dimens.logoSize)
                    .clip(Dimens.fieldCorner),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.gap)
            ) {
                FitText(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                )
                FitText(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LanguageToggle(language = language, onLanguageChange = onLanguageChange)
        }
    }
}

@Composable
private fun LanguageToggle(
    language: String,
    onLanguageChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(Dimens.pillCorner)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguagePill("ENG", selected = language == "en") { onLanguageChange("en") }
        LanguagePill("اردو", selected = language == "ur") { onLanguageChange("ur") }
    }
}

@Composable
private fun LanguagePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = Dimens.pillCorner,
        color = if (selected) MaterialTheme.colorScheme.primary
        else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .clip(Dimens.pillCorner)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun CashMemerBottomBar(
    currentRoute: String,
    onSelect: (Destination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Destination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                        modifier = Modifier.size(24.dp),
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * The standard grouped block. A hairline border rather than a shadow — the
 * warm background is too low-contrast for elevation to read, which is why the
 * earlier cards looked like they were floating on nothing.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Dimens.cardCorner,
        colors = CardDefaults.cardColors(
            containerColor = if (accent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        border = if (accent) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
            content = content,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    FitText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Heading for a screen, above its first card. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    FitText(
        text = text,
        modifier = modifier.padding(bottom = Dimens.gapTight),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Icon + label buttons. Screens were padding labels with literal spaces to
 * fake a gap; these give a real one and keep every button identical.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Dimens.touchTarget),
        shape = Dimens.fieldCorner,
        // Material's default 24dp side padding leaves a half-width button almost
        // no room for its label; a tighter pad is most of what stops the clip.
        contentPadding = ButtonContentPadding,
    ) { ButtonContent(text, icon) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(Dimens.touchTarget),
        shape = Dimens.fieldCorner,
        contentPadding = ButtonContentPadding,
        colors = if (danger) {
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) { ButtonContent(text, icon) }
}

private val ButtonContentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Dimens.iconGap))
    }
    // Half-width buttons hold labels like "Barcode Scan" and "Current location"
    // that wrap or clip. The label takes the space that is left and shrinks down
    // to fit it — further than before, so a two-word label always lands.
    FitText(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        smallestScale = 0.5f,
        modifier = Modifier.weight(1f, fill = false),
    )
}

/**
 * A bordered icon action with its label underneath.
 *
 * A bare `IconButton` in a row gives no sense of where one target ends and the
 * next begins, which is what made the History row read as a cramped smear of
 * symbols. The outline is doing real work: it says "this is a button, and it is
 * this big".
 */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Destructive actions get a filled dark red box rather than an outline. */
    danger: Boolean = false,
) {
    val tint = if (danger) OnDangerButton
    else MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            shape = Dimens.fieldCorner,
            color = if (danger) DangerButton else MaterialTheme.colorScheme.surface,
            border = if (danger) null
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(
                modifier = Modifier.size(Dimens.touchTarget),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        FitText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (danger) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * A destructive icon on its own, without a label — the filled dark red box for
 * rows that only have room for the symbol.
 */
@Composable
fun DangerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = Dimens.fieldCorner,
        color = DangerButton,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = OnDangerButton,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The app's search field. The stock outlined field with a magnifier stuck on
 * the front looked like an afterthought; this is a pill with the icon inside
 * it and a clear button that appears once there is something to clear.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Dimens.pillCorner,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}

/** Placeholder shown when a list has nothing in it yet. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.gapTight),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = Dimens.gapSection),
            )
        }
    }
}

/** Label/value line used by totals and detail blocks. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    emphasised: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasised) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyLarge,
            color = if (emphasised) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasised) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasised) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
