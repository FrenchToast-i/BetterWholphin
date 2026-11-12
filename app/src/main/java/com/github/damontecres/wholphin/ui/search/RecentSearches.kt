package com.github.damontecres.wholphin.ui.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.glassCapsule

/**
 * What this viewer looked for recently, as a row of capsules under the search field.
 *
 * The empty Search page is deliberately not a form: with nothing typed there is nothing to
 * search, so the page offers discovery instead. That is the right answer for someone who opened
 * Search to be shown something, and the wrong answer for someone who wants the show they looked
 * up yesterday and has to type its name again on a D-pad. History is the missing half.
 *
 * Drawn above the discovery shelf because it belongs to the viewer rather than to the catalogue:
 * personal, newest first, and one press away from the field it was typed into.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentSearches(
    searches: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The newest query's chip, so the page can route D-pad down from the field straight to it.
     *
     * Without an explicit target, focus search picks the geometrically nearest chip below the
     * field — which is "Clear", sitting at the end of the row. One press of down then one of
     * select would wipe the history the viewer was reaching for.
     */
    firstChipFocusRequester: FocusRequester? = null,
) {
    // Nothing to remember yet is the common first-run state, and a heading over an empty row
    // would be the first thing a new viewer saw on Search.
    if (searches.isEmpty()) return

    val glass = LocalLiquidGlass.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 26.dp),
    ) {
        Text(
            text = stringResource(R.string.recent_searches),
            style = if (glass) GlassType.sectionLabel else MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // Two rows at most: history is a shortcut, not a page of its own, and the discovery
            // shelf below has to keep its place.
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            searches.forEachIndexed { index, search ->
                RecentSearchChip(
                    text = search,
                    onClick = { onSelect(search) },
                    modifier =
                        if (index == 0 && firstChipFocusRequester != null) {
                            Modifier.focusRequester(firstChipFocusRequester)
                        } else {
                            Modifier
                        },
                )
            }
            RecentSearchChip(
                text = stringResource(R.string.clear_recent_searches),
                onClick = onClear,
                muted = true,
            )
        }
    }
}

/**
 * One remembered query: a capsule of the bar's own material.
 *
 * The label is the whole control, like the bar's destination pills — a leading glyph costs about
 * 25dp of a row that already has six entries in it. Focus lights the glass itself (a hotter rim
 * and a white wash) rather than drawing an outline around it, which is the same signal every
 * other control on the material uses.
 */
@Composable
private fun RecentSearchChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val glass = LocalLiquidGlass.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier =
            modifier
                .height(GlassTokens.chipHeight)
                .glassCapsule(
                    rim = if (focused) GlassTokens.focusHighlight else GlassTokens.pillHighlight,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(chipShape),
            colors =
                if (glass) {
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White.copy(alpha = if (muted) 0.62f else 0.94f),
                        // Focus is the bar's language, not a wash: the focused pill is white with
                        // dark type. A translucent lift alone sat too close to the unfocused
                        // chip to read from a sofa, and a focused chip has to be unmistakable or
                        // selecting one feels like a guess.
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                        pressedContainerColor = Color.White.copy(alpha = 0.86f),
                        pressedContentColor = Color.Black,
                    )
                } else {
                    ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                },
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (glass) 1.06f else 1f),
            glow =
                ClickableSurfaceDefaults.glow(
                    glow = Glow.None,
                    focusedGlow = Glow.None,
                    pressedGlow = Glow.None,
                ),
            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
            // Height only. A `fillMaxSize` surface inside a Box sizes itself to the row's whole
            // width, which turned every chip into a full-width bar; the capsule has to hug its
            // label the way a pill does.
            modifier = Modifier.fillMaxHeight(),
        ) {
            // A tv Material surface measures its slot at natural size and pins it top-left, so
            // the label centres itself inside the capsule.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Text(
                    text = text,
                    style = if (glass) GlassType.chip else MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.padding(
                            horizontal = GlassTokens.chipPadding,
                        ),
                )
            }
        }
    }
}

private val chipShape = RoundedCornerShape(percent = 50)
