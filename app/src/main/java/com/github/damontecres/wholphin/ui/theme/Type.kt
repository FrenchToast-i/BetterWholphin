package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.github.damontecres.wholphin.R

val AppTypography = Typography()

/**
 * The typeface used by Liquid Glass mode.
 *
 * Roboto is a phone face: its short x-height and loose default spacing read as thin from a sofa.
 * Inter was drawn for interfaces — taller lowercase, tighter rhythm, curved terminals — and its
 * display optical size is what gives the large labels their clean, TV-like silhouette.
 *
 * Bundled as four static weights rather than one variable font so every API level gets real
 * weights instead of synthetic emboldening.
 */
val InterFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
    )

/**
 * Text that is centred by its own box rather than by its font padding.
 *
 * Android reserves extra space above and below a line for ascenders and legacy font padding. In a
 * fixed-height pill that space is the difference between a label that sits in the middle and one
 * that looks a couple of pixels low — which is exactly the kind of misalignment you notice from a
 * sofa once there is a bright rim to read against.
 */
private val centred =
    PlatformTextStyle(includeFontPadding = false) to
        LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun glass(
    size: Int,
    weight: FontWeight,
    tracking: Float,
    lineHeight: Int = (size * 1.2f).toInt(),
): TextStyle =
    TextStyle(
        fontFamily = InterFamily,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        lineHeight = lineHeight.sp,
        platformStyle = centred.first,
        lineHeightStyle = centred.second,
    )

/**
 * Type for Liquid Glass mode.
 *
 * A tvOS-like hierarchy: few sizes, heavy weights where the eye lands, and optical tracking that
 * tightens as the type grows. No text shadows — depth on a television comes from the material and
 * from weight, and a blurred drop shadow behind every label is what makes an interface read as
 * frosted 2006 rather than clear glass.
 */
object GlassType {
    /** Destinations in the top bar and their sub-tabs. */
    val tab = glass(size = 17, weight = FontWeight.Medium, tracking = 0.1f, lineHeight = 21)
    val tabSelected = tab.copy(fontWeight = FontWeight.SemiBold)

    /** The clock. */
    val clock = glass(size = 15, weight = FontWeight.Medium, tracking = 0.2f)

    /** Shelf headings. */
    val rowTitle = glass(size = 21, weight = FontWeight.SemiBold, tracking = -0.2f)

    /** The number on a ranked poster. Small, but it has to hold two digits. */
    val rankBadge = glass(size = 13, weight = FontWeight.Bold, tracking = 0.2f, lineHeight = 16)

    /** A short status word on a poster, e.g. "Downloading". */
    val posterBadge = glass(size = 11, weight = FontWeight.SemiBold, tracking = 0.2f, lineHeight = 14)

    /**
     * Group labels above an inset settings card, the way iOS labels "General" and "Privacy".
     *
     * Smaller than a row title on purpose: the label organises the card beneath it instead of
     * competing with the rows, and the tracking opens it up so caps read as a caption rather
     * than a shout.
     */
    val sectionLabel = glass(size = 13, weight = FontWeight.SemiBold, tracking = 1.1f, lineHeight = 16)

    /** One row in a settings card. */
    val settingsTitle = glass(size = 17, weight = FontWeight.Medium, tracking = -0.1f, lineHeight = 21)

    /** The value or explanation under a settings row. */
    val settingsSummary = glass(size = 13, weight = FontWeight.Normal, tracking = 0.1f, lineHeight = 17)

    /** The chevron that marks a row as leading somewhere. */
    val chevron = glass(size = 20, weight = FontWeight.Medium, tracking = 0f, lineHeight = 20)

    /** Metadata line under a hero title: runtime, rating, resolution. */
    val meta = glass(size = 14, weight = FontWeight.Medium, tracking = 0.2f)

    /** Episode/season line under the logo. */
    val subtitle = glass(size = 20, weight = FontWeight.Medium, tracking = -0.1f, lineHeight = 24)

    /** Pill buttons. */
    val button = glass(size = 16, weight = FontWeight.SemiBold, tracking = 0.1f)

    /** Page title. */
    val pageTitle = glass(size = 30, weight = FontWeight.Bold, tracking = -0.8f)

    /**
     * The one display line on a landing screen ("Who's watching?").
     *
     * A television is read from three metres; this is the size at which the setup screen has a
     * subject instead of a row of discs floating in the middle of a dark field.
     */
    val hero = glass(size = 52, weight = FontWeight.Bold, tracking = -1.8f, lineHeight = 56)

    /** Supporting copy under [hero]. */
    val heroSub = glass(size = 19, weight = FontWeight.Normal, tracking = 0.1f, lineHeight = 26)

    /** Group heading, e.g. "Interface" in settings. */
    val sectionTitle = glass(size = 22, weight = FontWeight.Bold, tracking = -0.4f)

    /** Labels on chips and filter controls. */
    val chip = glass(size = 15, weight = FontWeight.Medium, tracking = 0.0f, lineHeight = 19)
}

/**
 * Material's type roles, restated in Inter.
 *
 * Glass mode is a whole interface, not a few widgets, so the family has to reach the screens that
 * are still written against `MaterialTheme.typography` — otherwise the bar is set in Inter and the
 * dialog that opens from it is set in Roboto.
 */
private fun TextStyle.forGlass(): TextStyle =
    // Family only. Scaling every Material role up is tempting — a filter pill built on `labelLarge`
    // does stay small next to a 17sp tab — but those roles also set the fixed-size setup screens,
    // where one extra point is enough to wrap a server name mid-word. The chrome that matters is
    // sized explicitly in [GlassType].
    copy(fontFamily = InterFamily)

val GlassTypography: Typography =
    Typography(
        displayLarge = AppTypography.displayLarge.forGlass(),
        displayMedium = AppTypography.displayMedium.forGlass(),
        displaySmall = AppTypography.displaySmall.forGlass(),
        headlineLarge = AppTypography.headlineLarge.forGlass(),
        headlineMedium = AppTypography.headlineMedium.forGlass(),
        headlineSmall = AppTypography.headlineSmall.forGlass(),
        titleLarge = AppTypography.titleLarge.forGlass(),
        titleMedium = AppTypography.titleMedium.forGlass(),
        titleSmall = AppTypography.titleSmall.forGlass(),
        bodyLarge = AppTypography.bodyLarge.forGlass(),
        bodyMedium = AppTypography.bodyMedium.forGlass(),
        bodySmall = AppTypography.bodySmall.forGlass(),
        labelLarge = AppTypography.labelLarge.forGlass(),
        labelMedium = AppTypography.labelMedium.forGlass(),
        labelSmall = AppTypography.labelSmall.forGlass(),
    )
