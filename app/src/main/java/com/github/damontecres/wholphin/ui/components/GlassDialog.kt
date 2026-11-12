package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Glow
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemGlow
import androidx.tv.material3.ListItemShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.glassSurface

/** Corner radius of one row's focus fill. Small enough to read as a row, round enough for glass. */
private val MenuRowRadius = 14.dp

/**
 * A menu row's focus fill.
 *
 * The default TV list item fills a focused row edge to edge with square corners, which on a glass
 * sheet reads as a highlighted spreadsheet cell. A rounded fill is the same dark focus signal the
 * rest of glass mode uses, just shaped like the material.
 */
@Composable
fun glassMenuRowShape(): ListItemShape =
    if (LocalLiquidGlass.current) {
        ListItemDefaults.shape(shape = RoundedCornerShape(MenuRowRadius))
    } else {
        ListItemDefaults.shape()
    }

/**
 * A menu row's colours.
 *
 * The focus fill is stated explicitly rather than inherited: inside a dialog the theme's
 * `inverseSurface` is composited over a blurred, bright backdrop, which lands on a washed grey
 * that white labels cannot hold. A deeper translucent black keeps the row dark and the label
 * legible on any artwork.
 */
@Composable
fun glassMenuRowColors(): ListItemColors =
    if (LocalLiquidGlass.current) {
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.Black.copy(alpha = 0.42f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.Black.copy(alpha = 0.52f),
            pressedContentColor = Color.White,
            selectedContainerColor = Color.White,
            selectedContentColor = Color.Black,
        )
    } else {
        ListItemDefaults.colors()
    }

@Composable
fun glassMenuRowGlow(): ListItemGlow =
    if (LocalLiquidGlass.current) {
        ListItemDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None)
    } else {
        ListItemDefaults.glow()
    }

/**
 * The fill for a dropdown menu.
 *
 * A dropdown is its own popup window and cannot sample the shell's layer backdrop, so the glass
 * fallback is a smoked pane rather than a blur. It is deliberately more opaque than a page panel:
 * a menu floats over posters with nothing under it to steady the type, and the rows on top have to
 * stay readable from a sofa no matter what artwork happens to be behind the popup.
 */
@Composable
fun glassMenuColor(elevation: Dp = 3.dp): Color =
    if (LocalLiquidGlass.current) {
        Color.Black.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(elevation)
    }

/**
 * The pane behind a dialog or a long-press menu.
 *
 * Dialogs are a separate window, so they cannot simply inherit a page's material: they carry
 * their own lift and a little more body than a content panel, because a menu has to hold white
 * labels over whatever artwork happens to be underneath. The silhouette, the rim and the lift
 * still come from the same [glassSurface] call as every other pane, so a menu reads as the same
 * substance as the bar above it — without the lens, which needs a layer it can sample.
 *
 * With glass off this is the classic elevated surface. With glass on it is the material's tint
 * fallback, so a confirmation dialog never loses its background at all.
 */
@Composable
fun Modifier.glassDialogSurface(
    shape: Shape = GlassTokens.panelShape,
    classicElevation: Dp = 3.dp,
): Modifier {
    if (!LocalLiquidGlass.current) {
        return this
            .shadow(elevation = classicElevation, shape = shape)
            .background(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(classicElevation),
                shape = shape,
            )
    }
    return glassSurface(
        shape = shape,
        // A dialog lives in its own window, so it cannot sample the shell's layer backdrop:
        // asking for it returns an empty image and the pane draws as a pale wash. Skipping the
        // sample takes the material's deliberate tint fallback, which is a smoked pane with the
        // same silhouette and a lift, and is legible over any artwork underneath.
        backdrop = null,
        tint = GlassTokens.dialogTint,
        frost = GlassTokens.DIALOG_FROST,
        elevation = GlassTokens.dialogElevation,
        // A menu is the top layer of the interface, so its face is smoked a little more than a
        // page panel: the artwork stays legible through it, but every label sits on quiet glass.
        scrim = 0.35f,
    )
}
