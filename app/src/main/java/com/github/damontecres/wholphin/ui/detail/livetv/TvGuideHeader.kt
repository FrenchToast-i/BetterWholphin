package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetailsText
import com.github.damontecres.wholphin.ui.components.StreamLabel
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoilApi::class)
@Composable
fun TvGuideHeader(
    program: TvProgram?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        // The copy takes the leftover width. At a fixed 75% the picture and the options disc
        // together measured wider than what was left, so the disc was sliced by the screen edge.
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(1f),
        ) {
            TitleOrLogo(
                // `program?.id.toString()` on a null programme is the string "null": the guide
                // shows this header while focus moves across empty cells between games, so an
                // absent programme has to be an empty title, not a sentinel that renders.
                title = program?.let { it.name ?: it.id.toString() }.orEmpty(),
                logoImageUrl = null,
                showLogo = false,
                modifier =
                    Modifier
                        .padding(start = HeaderUtils.startPadding)
                        .fillMaxWidth(.75f),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(.6f),
            ) {
                program?.subtitle?.let {
                    EpisodeName(
                        episodeName = program.subtitle,
                        modifier = Modifier.padding(start = HeaderUtils.startPadding),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                ) {
                    program?.quickDetails?.let {
                        QuickDetailsText(
                            it,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    if (program?.isRepeat == true) {
                        StreamLabel(stringResource(R.string.live_tv_repeat))
                    }
                }
                OverviewText(
                    overview = program?.overview ?: "",
                    // Two lines: the header is a fixed slot above a grid that needs the rest of
                    // the page, and a third line is the one that was always clipped.
                    maxLines = 2,
                    onClick = {},
                    enabled = false,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A fixed window, not the image's natural size: a programme picture arrives in
            // whatever shape the tuner publishes, and an unconstrained AsyncImage drew a stray
            // 90dp thumbnail floating at the far right of the header.
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(program?.imageUrl)
                        .transitionFactory(CrossFadeFactory(300.milliseconds))
                        .useExistingImageAsPlaceholder(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(width = 208.dp, height = 117.dp)
                        .clip(RoundedCornerShape(12.dp)),
            )
            // The page's view-options control lives here rather than in a row of its own under
            // the header: a single floating disc between the copy and the grid cost 44dp of the
            // schedule and read as a button that had lost its toolbar.
            trailing?.invoke()
        }
    }
}
