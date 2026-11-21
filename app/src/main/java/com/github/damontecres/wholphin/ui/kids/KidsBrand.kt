package com.github.damontecres.wholphin.ui.kids

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.github.damontecres.wholphin.R
import java.text.Normalizer

/**
 * The PBS KIDS palette, taken from the public PBS KIDS style guide.
 *
 * These are the colours the app and the website use for their own chrome, so the page keeps the
 * same bright, high-contrast look instead of borrowing the app's dark theme.
 */
object PbsKids {
    val Blue = Color(0xFF2638C4)
    val Green = Color(0xFF99CF16)
    val Yellow = Color(0xFFF7E03B)
    val Pink = Color(0xFFE5296B)
    val Cyan = Color(0xFF2FC0EB)
    val Ink = Color(0xFF0F1E8C)
    val Orange = Color(0xFFF5B338)
    val OrangeDeep = Color(0xFFED712C)
}

/** PBS KIDS Headline: the chunky, rounded display face used for the app's headings. */
val PbsHeadline =
    FontFamily(
        Font(R.font.pbs_kids_headline, FontWeight.Normal),
        Font(R.font.pbs_kids_headline_bold, FontWeight.Bold),
    )

/** PBS Sans: the companion face for episode titles and running copy. */
val PbsSans =
    FontFamily(
        Font(R.font.pbs_sans_regular, FontWeight.Normal),
        Font(R.font.pbs_sans_bold, FontWeight.Bold),
    )

/**
 * One show's official PBS KIDS artwork and colour.
 *
 * The character cut-out is the same asset the PBS KIDS site uses for its show buttons, and the
 * backdrop is the show's own page background. The palette is the one PBS gives that show in its
 * public theme, so a tile and its hero caption match what a child sees in the PBS KIDS app.
 */
data class KidsShowBrand(
    val key: String,
    val names: List<String>,
    @DrawableRes val logo: Int,
    val logoAspect: Float,
    @DrawableRes val character: Int,
    @DrawableRes val backdrop: Int,
    val color: Color,
    val footer: Color,
    val onFooter: Color,
)

private val showBrands =
    listOf(
        KidsShowBrand(
            key = "wild-kratts",
            names = listOf("Wild Kratts"),
            logo = R.drawable.pbs_wild_kratts_logo,
            logoAspect = 498f / 480f,
            character = R.drawable.pbs_wild_kratts_character,
            backdrop = R.drawable.pbs_wild_kratts_backdrop,
            color = Color(0xFF6CD1D0),
            footer = Color(0xFF1A8549),
            onFooter = Color.White,
        ),
        KidsShowBrand(
            key = "odd-squad",
            names = listOf("Odd Squad"),
            logo = R.drawable.pbs_odd_squad_logo,
            logoAspect = 418f / 480f,
            character = R.drawable.pbs_odd_squad_character,
            backdrop = R.drawable.pbs_odd_squad_backdrop,
            color = Color(0xFF141314),
            footer = Color(0xFF00CDD4),
            onFooter = Color(0xFF141314),
        ),
        KidsShowBrand(
            key = "peg-cat",
            names = listOf("Peg + Cat"),
            logo = R.drawable.pbs_peg_cat_logo,
            logoAspect = 640f / 167f,
            character = R.drawable.pbs_peg_cat_character,
            backdrop = R.drawable.pbs_peg_cat_backdrop,
            color = Color(0xFF5BBAD6),
            footer = PbsKids.Blue,
            onFooter = Color.White,
        ),
    )

/**
 * Find the official PBS KIDS artwork for a library title.
 *
 * Matching is deliberately by title rather than by a Jellyfin metadata id: the show may live in
 * any library, be named with a leading "The", or carry a subtitle. Titles that are not PBS KIDS
 * shows return null and keep their own library artwork.
 */
fun kidsBrandFor(title: String?): KidsShowBrand? {
    val key = normalizeTitle(title) ?: return null
    return showBrands.firstOrNull { brand ->
        brand.names.any { alias ->
            val normalized = normalizeTitle(alias) ?: return@any false
            key == normalized || key.startsWith("$normalized ")
        }
    }
}

private fun normalizeTitle(title: String?): String? {
    if (title.isNullOrBlank()) return null
    val withoutMarks =
        Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace("+", " plus ")
    return withoutMarks
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { null }
}
