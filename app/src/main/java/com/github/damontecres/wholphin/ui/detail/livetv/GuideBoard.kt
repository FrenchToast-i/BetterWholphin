package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The schedule's own surface.
 *
 * A guide is not a pane of glass. Glass earns its place where the artwork *behind* a control is
 * part of the control — a bar you read against a poster, a menu that floats over the film. A
 * forty-eight hour schedule is the opposite case: a spreadsheet of white type, two or three
 * hundred cells, most of them small. Frosting the whole board turned it into a pale sheet over a
 * bright cartoon frame — the artwork came through the programme titles, the gaps read as
 * rectangles of nothing, and the time ruler was invisible against its own header. That is not a
 * material problem, it is the wrong material.
 *
 * So the material stops at the chrome and the schedule is drawn as a **slate**: a near-opaque,
 * cool-black board with a single hairline at its edge, a header band for the time ruler and a
 * channel column with its own divider. It is the one surface in the app that is deliberately dark,
 * because it is the one surface whose job is to hold a lot of small type; contrast is the feature.
 * The board still floats (rounded, inset, on artwork) so it belongs to the same room as the glass
 * around it, and the live red is the only saturated colour on it.
 */
internal object GuideSlate {
    val shape: Shape = RoundedCornerShape(22.dp)

    /** Top and bottom of the board's gradient: smoke at the top, solid at the bottom. */
    val top: Color = Color(0xFF0C1119).copy(alpha = 0.94f)
    val bottom: Color = Color(0xFF05070B).copy(alpha = 0.985f)

    /** The board's own edge. One hairline, not a rim — the glass above holds the highlights. */
    val edge: Color = Color.White.copy(alpha = 0.11f)

    /**
     * The time ruler's band, and the line that separates it from the schedule.
     *
     * **Opaque, deliberately.** The ruler is sticky: rows scroll underneath it, and a translucent
     * band let the first channel's name read through the header, which is what made the board look
     * disjointed. A solid band is the mask; the hairline is what makes it look like a header and
     * not like the schedule simply stopped.
     */
    val headerBand: Color = Color(0xFF161C26)
    val hairline: Color = Color.White.copy(alpha = 0.08f)
    val divider: Color = Color.White.copy(alpha = 0.14f)

    /** A programme cell, and the same cell while it is on air. */
    val cell: Color = Color.White.copy(alpha = 0.10f)
    val cellOnAir: Color = Color.White.copy(alpha = 0.15f)

    /**
     * The channel column: a shade under the schedule so the divider has something to divide.
     *
     * **Opaque, deliberately.** The column is fixed horizontally while the schedule scrolls
     * behind it, so it has to be a mask; a translucent column let a programme from the previous
     * hour draw straight through a channel's name, which is what made the board look broken.
     */
    val column: Color = Color(0xFF11161F)

    /** A gap in the schedule: present, so the columns stay aligned, but not drawn as a cell. */
    val filler: Color = Color.Transparent

    /** Focused cells keep the app-wide white-fill / dark-glyph signal. */
    val focusFill: Color = Color.White
    val focusInk: Color = Color.Black
}

/**
 * The board itself: clipped rounded slate with a vertical smoke gradient and one hairline edge.
 *
 * The gradient matters more than it looks. A flat fill reads as a hole punched in the page; a
 * slight lift at the top and a deeper foot gives the board the same lighting direction as every
 * glass pane in the app, so it sits in the room instead of on top of it.
 */
internal fun Modifier.guideSlate(shape: Shape = GuideSlate.shape): Modifier =
    this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(GuideSlate.top, GuideSlate.bottom)))
        .border(1.dp, GuideSlate.edge, shape)

/** A hairline under a measured box, used by the ruler and the channel column. */
internal fun Modifier.bottomHairline(color: Color = GuideSlate.hairline): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        drawLine(
            color = color,
            start = Offset(0f, size.height - stroke / 2f),
            end = Offset(size.width, size.height - stroke / 2f),
            strokeWidth = stroke,
        )
    }

/** A hairline down the right edge, used to close the channel column off from the schedule. */
internal fun Modifier.endHairline(color: Color = GuideSlate.divider): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width - stroke / 2f, 0f),
            end = Offset(size.width - stroke / 2f, size.height),
            strokeWidth = stroke,
        )
    }

/**
 * A tiny live mark for a guide cell.
 *
 * The sports page has room for a word; a 64dp programme cell does not — its title is already the
 * most important thing in it. A red dot in the corner is the smallest thing that still says "this
 * is on now" from ten feet, and it uses [LiveRed] so the guide and the sports page agree.
 */
/**
 * One programme cell's height: the guide's 64dp row minus the 2dp inset at each edge.
 *
 * The guide measures a cell against an unbounded height, so the tile has to state its own size or
 * it shrinks to its text and floats in the top of the row.
 */
internal val GuideCellHeight: Dp = 60.dp
