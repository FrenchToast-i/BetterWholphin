package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.Glow
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

/**
 * Card styling for Liquid Glass mode.
 *
 * Cards deliberately do not sample the backdrop: a home screen can show dozens at once, and a
 * blur pass per card is far too expensive for TV hardware. They get the material's read instead —
 * generous rounding and a scale lift — while the only edge is the focus ring, which is the one
 * time a bright rim is meaningful.
 */
@Composable
fun glassCardShape(radius: Dp = GlassTokens.cardRadius): CardShape =
    if (LocalLiquidGlass.current) {
        CardDefaults.shape(shape = RoundedCornerShape(radius))
    } else {
        CardDefaults.shape()
    }

@Composable
fun glassCardBorder(radius: Dp = GlassTokens.cardRadius): CardBorder =
    if (LocalLiquidGlass.current) {
        val shape = RoundedCornerShape(radius)
        CardDefaults.border(
            // No resting outline at all. A hairline around every poster in a row of eight reads
            // as a picture frame: glass mode wants the artwork to sit straight on the backdrop,
            // and the card only gains an edge when it is actually being pointed at.
            border = Border(border = BorderStroke(0.dp, Color.Transparent), shape = shape),
            // A thin, bright rim plus the lift below: heavy 2dp outlines are what make a TV app
            // look like a desktop widget scaled up.
            focusedBorder = Border(border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.95f)), shape = shape),
        )
    } else {
        CardDefaults.border()
    }

/**
 * Cards lift toward the viewer when focused. The scale is what makes a glass grid feel tactile:
 * the focused card reads as a physical tile rising off the wall rather than a picture with a
 * brighter outline.
 */
@Composable
fun glassCardScale(): CardScale =
    if (LocalLiquidGlass.current) {
        CardDefaults.scale(focusedScale = 1.06f, pressedScale = 0.99f)
    } else {
        CardDefaults.scale()
    }

@Composable
fun glassCardGlow(): CardGlow =
    if (LocalLiquidGlass.current) {
        CardDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow.None,
        )
    } else {
        CardDefaults.glow()
    }
