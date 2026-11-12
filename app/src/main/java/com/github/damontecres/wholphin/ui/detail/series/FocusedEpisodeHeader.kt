package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.theme.GlassPanel
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

@Composable
fun FocusedEpisodeHeader(
    preferences: UserPreferences,
    ep: BaseItem?,
    chosenStreams: ChosenStreams?,
    overviewOnClick: () -> Unit,
    overviewOnFocus: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = ep?.data
    val copy: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EpisodeName(dto, modifier = Modifier.padding(start = HeaderUtils.startPadding))

            ep?.ui?.quickDetails?.let {
                QuickDetails(
                    it,
                    ep.timeRemainingOrRuntime,
                    Modifier.padding(start = HeaderUtils.startPadding),
                )
            }

            if (dto != null) {
                VideoStreamDetails(
                    chosenStreams = chosenStreams,
                    numberOfVersions = dto.mediaSourceCount ?: 0,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }
            OverviewText(
                overview = dto?.overview ?: "",
                maxLines = 3,
                onClick = overviewOnClick,
                // The default box is three 20dp lines, which is the classic type scale. Glass
                // type is taller, so that box clips the third line and paints the ellipsis on a
                // line of its own; let the text measure itself instead.
                textBoxHeight = Dp.Unspecified,
                modifier = Modifier.onFocusChanged(overviewOnFocus),
            )
        }
    }
    if (LocalLiquidGlass.current) {
        // The episode copy is the densest block of type on the series page and the show's own
        // artwork decides what is behind it: a white title over a pale frame has nothing holding
        // it. The pane gives this block the same surface as the library headers, so the page is
        // made of the material rather than of labels lying on a photograph.
        GlassPanel(modifier = modifier, scrim = 1f) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                copy()
            }
        }
    } else {
        Column(modifier = modifier) { copy() }
    }
}
