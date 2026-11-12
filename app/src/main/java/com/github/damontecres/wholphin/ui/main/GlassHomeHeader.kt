package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.theme.GlassPanel
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import org.jellyfin.sdk.model.api.BaseItemKind

/** The pane's own inset. Large enough that copy never crowds the corner radius. */
private val HeroInset = 24.dp

/**
 * The Apple TV-style header used by Liquid Glass mode.
 *
 * Everything about the focused title lives in one pane of glass: logo, metadata, synopsis, and
 * the two actions a viewer actually wants. On a TV that is important, because reaching a play
 * button otherwise means pressing right through a row of cards first.
 */
@Composable
fun GlassHomeHeader(
    item: BaseItem?,
    showLogo: Boolean,
    onPlay: (BaseItem) -> Unit,
    onDetails: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.EPISODE,
                BaseItemKind.SEASON,
                -> item.data.seriesName

                else -> item?.title
            }
        }
    val subtitle =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
                -> item.name

                else -> null
            }
        }
    val logoImageUrl = rememberLogoUrl(item)
    val resumable = (item?.data?.userData?.playedPercentage ?: 0.0) > 0.0
    val playedPercent = item?.data?.userData?.playedPercentage ?: 0.0

    GlassPanel(
        // Fixed width and height rather than wrapped content: a pane that changes size as focus
        // moves across a shelf makes the whole page feel like it is reflowing under the D-pad.
        //
        // The height is the important number. A wrapped hero grew to nearly 330dp — more than
        // half a 540dp screen — and the first shelf could only show a clipped row of posters.
        // A TV hero is a masthead: title, one metadata line, two actions, and the artwork gets
        // the rest of the frame.
        modifier =
            modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth(0.56f)
                .height(178.dp),
        shape = RoundedCornerShape(GlassTokens.panelRadius),
        // Chromatic aberration stays off: the library's dispersion shader takes seven displaced
        // samples per pixel, which turns every high-contrast edge behind the pane into a rainbow
        // smear. A single refraction tap plus the rim is what actually reads as thick glass.
        chromaticAberration = false,
        // A hero carries a title logo as well as copy, and a logo is drawn in its own colours:
        // clear glass over artwork of the same hue makes it vanish, so the hero is tinted where
        // every other content pane stays clear. Pale art still smokes through the copy scrim.
        tint = GlassTokens.heroTint,
        scrim = 1f,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = HeroInset, vertical = 12.dp),
        ) {
            TitleOrLogo(
                title = title,
                logoImageUrl = logoImageUrl,
                showLogo = showLogo,
                modifier = Modifier.fillMaxWidth(0.82f),
                // Bigger than it looks like it needs to be, because a logo image carries its own
                // transparent margin, and multi-line logos (three stacked lines is common) lose
                // their first line to sub-pixel thinning at small sizes. 44dp keeps a wordmark
                // readable at ten feet and still leaves the pane's centred content block loose
                // inside its 178dp.
                logoHeight = 44.dp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = GlassType.tab,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            QuickDetails(
                details = item?.ui?.quickDetails,
                timeRemaining = item?.timeRemainingOrRuntime,
                endsAt = item?.data?.endDate,
                // The hero is identity, not a plan: the wall-clock end time belongs to the detail
                // page and the player. In a pane that is 56% of the page it was the clause that
                // fell off the end of the ellipsis — "8.4 ★ · Ends a…" — which reads as a
                // rendering fault rather than as a line that ends deliberately.
                showEndsAt = false,
                textStyle = GlassType.meta.copy(color = Color.White.copy(alpha = 0.90f)),
                // The pane is a fixed 56% of the page, so a long line has to end in an ellipsis
                // inside it rather than run under the rounded corner: "S1E18 · Nov 4 · 26m ·
                // TV-Y" already nearly reaches the edge on an ordinary episode.
                oneLine = true,
            )
            // Resume position, drawn as a thin rule rather than a chunky media bar so it reads
            // as part of the material instead of a widget dropped onto it.
            if (playedPercent > 0.0 && playedPercent < 99.0) {
                // Bounded, not full width: a rule spanning the whole pane reads as a divider
                // under the metadata rather than as a resume position.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.34f)
                            .padding(top = 4.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((playedPercent / 100.0).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.92f)),
                    )
                }
            }
            if (item != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassActionButton(
                        text =
                            stringResource(
                                if (resumable) R.string.liquid_glass_resume else R.string.liquid_glass_play,
                            ),
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        onClick = { onPlay(item) },
                    )
                    GlassActionButton(
                        text = stringResource(R.string.liquid_glass_details),
                        icon = Icons.Default.Info,
                        primary = false,
                        onClick = { onDetails(item) },
                    )
                }
            }
        }
    }
}

/**
 * A pill button that reads as glass when idle and floods white when focused, which is the
 * clearest focus signal at ten feet.
 */
@Composable
private fun GlassActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    primary: Boolean,
) {
    // The secondary action is smoked glass rather than white frost: a 20% white fill disappears
    // onto a bright backdrop, which is exactly where a hero most often sits.
    val idleContainer = if (primary) Color.White.copy(alpha = 0.96f) else Color.Black.copy(alpha = 0.34f)
    val idleContent = if (primary) Color.Black else Color.White
    val shape = RoundedCornerShape(GlassTokens.pillRadius)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = idleContainer,
                contentColor = idleContent,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
                pressedContainerColor = Color.White,
                pressedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        glow =
            ClickableSurfaceDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow.None,
                pressedGlow = Glow.None,
            ),
        // No border, idle or focused. A white outline around a pill is the same drawn-stroke
        // read as around a pane; focus is already unmistakable because the fill floods white
        // and the surface grows.
        border = ClickableSurfaceDefaults.border(),
    ) {
        // Fill the surface's height: the TV Surface pins content to the top-left rather than
        // centring it, which is enough to make a pill's label sit high.
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    // An explicit height, not fillMaxHeight: the Surface wraps its content, so
                    // filling the axis would size the pill to the whole panel.
                    .height(40.dp)
                    .padding(horizontal = 22.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = text, style = GlassType.button)
        }
    }
}
