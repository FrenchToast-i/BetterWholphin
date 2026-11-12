// This file was inspired by related Button source files from
// https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/tv/tv-material/src/main/java/androidx/tv/material3

package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

/**
 * This is a re-implementation of [androidx.tv.material3.Button] with altered sizing, padding, colors, etc
 *
 * This allows for creating smaller and fully circular Buttons.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    shape: ClickableSurfaceShape =
        ClickableSurfaceDefaults.shape(
            shape = CircleShape,
        ),
    colors: ClickableSurfaceColors =
        ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            pressedContainerColor = MaterialTheme.colorScheme.onSurface,
            pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        ),
    tonalElevation: Dp = 0.dp,
    border: ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
            disabledBorder = Border.None,
            focusedDisabledBorder =
                Border(
                    border =
                        BorderStroke(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        ),
                    shape = CircleShape,
                ),
        ),
    contentPadding: PaddingValues = DefaultButtonPadding,
    contentHeight: Dp = MinButtonSize,
    interactionSource: MutableInteractionSource? = null,
    /** Draw no fill of its own, so the button sits *in* a pane of glass instead of on top of it. */
    glass: Boolean = false,
    /** The primary action on glass: a lit white capsule, the same signal as the hero's Play. */
    glassProminent: Boolean = false,
    /** A secondary action that floats on glass: a smoked circular capsule with a white glyph. */
    glassFilled: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val liquidGlass = LocalLiquidGlass.current
    val glassChrome = liquidGlass && (glass || glassProminent || glassFilled)
    Surface(
        modifier = modifier.semantics { role = Role.Button },
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        // A focus scale is the glass signal for "this object is being picked up"; the material
        // itself should not grow from its idle size the way an opaque material button does.
        scale = if (glassChrome) ClickableSurfaceDefaults.scale(focusedScale = 1.05f) else scale,
        glow = if (glassChrome) glassButtonGlow() else glow,
        shape = if (glassProminent) ClickableSurfaceDefaults.shape(RoundedCornerShape(percent = 50)) else shape,
        colors =
            when {
                glassProminent -> prominentGlassButtonColors()
                glassFilled -> filledGlassButtonColors()
                glass -> glassButtonColors()
                else -> colors
            },
        tonalElevation = tonalElevation,
        border = if (glassChrome) glassButtonBorder() else border,
        interactionSource = interactionSource,
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
            Row(
                modifier =
                    Modifier
                        .padding(contentPadding)
                        .height(contentHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
@NonRestartableComposable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues =
        PaddingValues(
            start = 8.dp,
            top = 4.dp,
            end = 8.dp,
            bottom = 4.dp,
        ),
    contentHeight: Dp = 32.dp,
    interactionSource: MutableInteractionSource? = null,
    /** See [Button.glassFilled]: a labelled control that floats on artwork rather than in a pane. */
    glassFilled: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = Button(
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    contentPadding = contentPadding,
    contentHeight = contentHeight,
    interactionSource = interactionSource,
    glassFilled = glassFilled,
    content = content,
)

@Composable
fun TextButton(
    @StringRes stringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues =
        PaddingValues(
            start = 8.dp,
            top = 4.dp,
            end = 8.dp,
            bottom = 4.dp,
        ),
    contentHeight: Dp = 32.dp,
    interactionSource: MutableInteractionSource? = null,
    glassFilled: Boolean = false,
) = Button(
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    contentPadding = contentPadding,
    contentHeight = contentHeight,
    interactionSource = interactionSource,
    glassFilled = glassFilled,
    content = {
        Text(text = stringResource(stringRes))
    },
)

val DefaultButtonPadding =
    PaddingValues(
        start = 4.dp,
        top = 4.dp,
        end = 4.dp,
        bottom = 4.dp,
    )

/**
 * A control that lives inside a capsule of glass rather than on top of it.
 *
 * The pane behind it already supplies the fill, the rim and the lift, so the button contributes
 * only its hit area and a focus wash. Giving it the usual opaque container would cover the very
 * material it is supposed to sit in.
 */
@Composable
private fun glassButtonColors(): ClickableSurfaceColors =
    ClickableSurfaceDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = Color.White.copy(alpha = 0.92f),
        focusedContainerColor = Color.White.copy(alpha = 0.22f),
        focusedContentColor = Color.White,
        pressedContainerColor = Color.White.copy(alpha = 0.30f),
        pressedContentColor = Color.White,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = Color.White.copy(alpha = 0.38f),
    )

/**
 * The prominent action: nearly solid white with black type.
 *
 * On glass, the only element allowed to be this bright is the action the page exists for, which is
 * why the hero and the detail sheet use exactly this fill. A half-transparent white is the worst of
 * both worlds — it stops reading as glass and it stops reading as a button.
 */
@Composable
private fun prominentGlassButtonColors(): ClickableSurfaceColors =
    ClickableSurfaceDefaults.colors(
        containerColor = Color.White.copy(alpha = 0.95f),
        contentColor = Color.Black,
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
        pressedContainerColor = Color.White.copy(alpha = 0.82f),
        pressedContentColor = Color.Black,
        disabledContainerColor = Color.White.copy(alpha = 0.35f),
        disabledContentColor = Color.Black.copy(alpha = 0.45f),
    )

/**
 * A secondary action floating on a pane: smoked glass with a white glyph. Without the smoke the
 * icon disappears into a bright poster behind it, which is exactly where a detail header sits.
 */
@Composable
private fun filledGlassButtonColors(): ClickableSurfaceColors =
    ClickableSurfaceDefaults.colors(
        containerColor = Color.Black.copy(alpha = 0.30f),
        contentColor = Color.White.copy(alpha = 0.94f),
        focusedContainerColor = Color.White,
        focusedContentColor = Color.Black,
        pressedContainerColor = Color.White.copy(alpha = 0.86f),
        pressedContentColor = Color.Black,
        disabledContainerColor = Color.Black.copy(alpha = 0.16f),
        disabledContentColor = Color.White.copy(alpha = 0.38f),
    )

@Composable
private fun glassButtonGlow(): ClickableSurfaceGlow =
    ClickableSurfaceDefaults.glow(
        glow = Glow.None,
        focusedGlow = Glow.None,
        pressedGlow = Glow.None,
    )

@Composable
private fun glassButtonBorder(): ClickableSurfaceBorder =
    ClickableSurfaceDefaults.border(
        border = Border.None,
        focusedBorder = Border.None,
        pressedBorder = Border.None,
        disabledBorder = Border.None,
        focusedDisabledBorder = Border.None,
    )
