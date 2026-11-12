package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Glow
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemShape
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

/**
 * The rows of a settings group, drawn as one iOS-style inset card.
 *
 * Each row is still its own focusable [androidx.tv.material3.ListItem] — restructuring the list
 * into one item per group would break the lazy list's focus restoration and the sticky section
 * headers. Instead the first row paints the card's top corners, the middle rows paint square
 * edges, the last row paints the bottom corners, and every row but the last draws the hairline
 * that separates it from the next. Visually the group is one object; structurally it is still
 * one item per control.
 */
@Composable
fun Modifier.glassPreferenceRow(
    first: Boolean,
    last: Boolean,
): Modifier {
    if (!LocalLiquidGlass.current) return this
    val radius = GlassTokens.rowRadius
    val shape =
        RoundedCornerShape(
            topStart = if (first) radius else 0.dp,
            topEnd = if (first) radius else 0.dp,
            bottomStart = if (last) radius else 0.dp,
            bottomEnd = if (last) radius else 0.dp,
        )
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = GlassTokens.ROW_FILL))
        .drawWithContent {
            drawContent()
            if (!last) {
                val inset = GlassTokens.rowInset.toPx()
                drawLine(
                    color = GlassTokens.rowDivider,
                    start = Offset(inset, size.height),
                    end = Offset(size.width - inset, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
}

/**
 * List item colours that get out of the glass's way.
 *
 * The stock TV list item paints an opaque elevation surface, which on a transparent sheet reads
 * as a grey block pasted over the artwork. Group rows are already sitting on the group's fill, so
 * idle is transparent and focus is a wash of light, matching the capsules in the bar.
 */
@Composable
@ReadOnlyComposable
fun glassListItemColors(): ListItemColors {
    if (!LocalLiquidGlass.current) return ListItemDefaults.colors()
    return ListItemDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = Color.White.copy(alpha = 0.94f),
        focusedContainerColor = Color.White.copy(alpha = 0.16f),
        focusedContentColor = Color.White,
        pressedContainerColor = Color.White.copy(alpha = 0.24f),
        pressedContentColor = Color.White,
        selectedContainerColor = Color.Transparent,
        selectedContentColor = Color.White.copy(alpha = 0.94f),
        disabledContainerColor = Color.Transparent,
        disabledContentColor = Color.White.copy(alpha = 0.38f),
        focusedSelectedContainerColor = Color.White.copy(alpha = 0.16f),
        focusedSelectedContentColor = Color.White,
        pressedSelectedContainerColor = Color.White.copy(alpha = 0.24f),
        pressedSelectedContentColor = Color.White,
    )
}

/** Rounded rows that grow by a hair on focus instead of jumping to a different shape. */
@Composable
@ReadOnlyComposable
fun glassListItemShape(): ListItemShape {
    if (!LocalLiquidGlass.current) return ListItemDefaults.shape()
    val shape = RoundedCornerShape(GlassTokens.rowRadius)
    return ListItemDefaults.shape(
        shape = shape,
        focusedShape = shape,
        pressedShape = shape,
        selectedShape = shape,
        disabledShape = shape,
        focusedSelectedShape = shape,
        focusedDisabledShape = shape,
        pressedSelectedShape = shape,
    )
}

/** Scale for rows inside a group: the group holds its silhouette while focus moves through it. */
@Composable
@ReadOnlyComposable
fun glassListItemScale() =
    if (!LocalLiquidGlass.current) {
        ListItemDefaults.scale()
    } else {
        ListItemDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    }

/** No focus glow: the focus wash and the material's own rim carry it. */
@Composable
@ReadOnlyComposable
fun glassListItemGlow() =
    if (!LocalLiquidGlass.current) {
        ListItemDefaults.glow()
    } else {
        ListItemDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow.None,
            pressedGlow = Glow.None,
            selectedGlow = Glow.None,
            focusedSelectedGlow = Glow.None,
        )
    }

/**
 * Rows sit *on* the glass, so they must not add a tonal surface of their own.
 *
 * The stock list item computes an opaque elevation tint from the theme; in glass mode that is a
 * grey band pasted over the artwork and a second material under the real one.
 */
@Composable
@ReadOnlyComposable
fun glassListItemTonalElevation(): Dp =
    if (LocalLiquidGlass.current) 0.dp else ListItemDefaults.TonalElevation

/**
 * The right-hand affordance that says "this row opens something", as on iOS.
 *
 * A switch or slider already shows its state, so only rows that lead somewhere get one. The
 * glyph is drawn as text so it matches the Inter optical size of the labels instead of importing
 * a second icon set for one character.
 */
@Composable
fun GlassChevron(modifier: Modifier = Modifier) {
    if (!LocalLiquidGlass.current) return
    Text(
        text = "\u203A",
        style = GlassType.chevron,
        color = Color.White.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
