package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.cards.glassCardBorder
import com.github.damontecres.wholphin.ui.cards.glassCardGlow
import com.github.damontecres.wholphin.ui.cards.glassCardScale
import com.github.damontecres.wholphin.ui.cards.glassCardShape
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import java.util.UUID

/**
 * Live TV when there is nothing to schedule.
 *
 * A tuner that has just been added has channels and no guide: Jellyfin fetches the stream list
 * first and the XMLTV programmes whenever the guide refresh runs, which can be hours later. The
 * grid has nothing to draw in that state, so the page used to be forty-eight hours of "No data"
 * per channel — a wall that looks like a broken app and hides the one thing that does work, which
 * is the channels themselves.
 *
 * This is the answer: every channel as a card, playable immediately, with a line explaining what is
 * missing. It is also the fallback body of the Sports tab, because before programmes exist there is
 * no way to tell a sports channel from any other (Jellyfin drops the M3U `group-title`), and a
 * channel grid that works beats a sports filter that cannot.
 */
@Composable
fun LiveTvChannelsTab(
    onClickChannel: (TvChannel) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    when (val loading = state.loading) {
        LoadingState.Pending,
        LoadingState.Loading,
        -> LoadingPage(modifier)

        is LoadingState.Error -> {
            NoChannelsMessage(
                message = stringResource(R.string.live_tv_channels_error),
                modifier = modifier,
            )
        }

        LoadingState.Success -> {
            if (state.channels.isEmpty()) {
                NoChannelsMessage(
                    message = stringResource(R.string.live_tv_no_channels_message),
                    modifier = modifier,
                )
            } else {
                // A channel tile is a name and a logo, which tells a viewer nothing about what
                // is on. The guide data is already loaded for the page, so the card can carry the
                // programme that is airing right now — a channel list that doubles as a
                // now/next board without a second request.
                val now = rememberTickingNow()
                val nowById =
                    remember(state.programs.programs, now) {
                        state.programs.programs
                            .filter { !it.isFake && it.start <= now && now.isBefore(it.end) }
                            .associateBy { it.channelId }
                    }
                Column(modifier = modifier) {
                    Text(
                        text = stringResource(R.string.live_tv_channel_count, state.channels.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = copyColor(0.72f),
                        modifier = Modifier.padding(start = pageStartPadding(), bottom = 12.dp),
                    )
                    TvChannelGrid(
                        channels = state.channels,
                        onClickChannel = onClickChannel,
                        modifier = Modifier.fillMaxSize(),
                        focusRequester = focusRequester,
                        nowById = nowById,
                    )
                }
            }
        }
    }
}

/**
 * The guide's own empty state: the tuner is there, the listings are not.
 *
 * Kept in this file beside the grid because the two are one answer: the message explains the
 * missing guide, and the grid underneath is what the viewer can actually press. The count is in
 * the sentence on purpose — "34 channels" tells a viewer the tuner is working, so the blank guide
 * reads as a server refresh away rather than as a failed setup.
 */
@Composable
fun NoGuideData(
    channels: List<TvChannel>,
    onClickChannel: (TvChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.live_tv_no_guide_title),
            style = if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineMedium,
            color = copyColor(1f),
            modifier = Modifier.padding(start = pageStartPadding()),
        )
        Text(
            text = stringResource(R.string.live_tv_no_guide_message, channels.size),
            style = MaterialTheme.typography.bodyLarge,
            color = copyColor(0.75f),
            modifier =
                Modifier
                    .padding(start = pageStartPadding(), end = pageStartPadding())
                    .fillMaxWidth(0.72f),
        )
        TvChannelGrid(
            channels = channels,
            onClickChannel = onClickChannel,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp),
        )
    }
}

/**
 * Every channel, as a grid of watchable cards.
 *
 * No hero, no programme: a channel is a name, a number and a logo, and the picture tells the rest.
 * The card is a media card rather than a pane of glass for the same reason posters are — a screen
 * of thirty panes would each want a blur pass.
 */
@Composable
fun TvChannelGrid(
    channels: List<TvChannel>,
    onClickChannel: (TvChannel) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    nowById: Map<UUID, TvProgram> = emptyMap(),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 232.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding =
            PaddingValues(
                start = pageStartPadding(),
                end = GlassTokens.screenInset,
                top = contentPadding.calculateTopPadding(),
                bottom = 32.dp,
            ),
        modifier = modifier,
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            TvChannelCard(
                channel = channel,
                now = nowById[channel.id],
                onClick = { onClickChannel(channel) },
                modifier =
                    if (index == 0 && focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
            )
        }
    }
    if (focusRequester != null && channels.isNotEmpty()) {
        LaunchedEffect(Unit) {
            focusRequester.tryRequestFocus()
        }
    }
}

@Composable
private fun TvChannelCard(
    channel: TvChannel,
    now: TvProgram?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val glass = LocalLiquidGlass.current
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        onLongClick = {},
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (glass) 0.30f else 0.55f)),
        ) {
            if (channel.imageUrl != null) {
                // A tuner's channel art is the tile: a fixture is two team logos split across a
                // 16:9 card. Fitting it inside a margin left a picture floating in a black box,
                // which is what a placeholder looks like. Crop lets the card be the artwork and
                // the name sit on the band at its foot.
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(channel.imageUrl)
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // A channel with no logo still has to be identifiable from the sofa: the name is
                // the card, set large and centred, instead of a blank tile.
                Text(
                    text = channel.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                            ),
                        ).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                channel.number?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.66f),
                    )
                }
                Text(
                    text = channel.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    // Two lines: on a sports tuner the channel name *is* the fixture, and one
                    // line sliced it to "Washington Comm…", which names neither team.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Repeating the fixture as "Now: …" is the same line twice. Only a genuinely
                // different programme is worth the third line.
                now?.name
                    ?.takeIf { !it.equals(channel.name, ignoreCase = true) }
                    ?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .background(Color.White.copy(alpha = 0.55f), CircleShape),
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun NoChannelsMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(0.7f).padding(pageStartPadding()),
        ) {
            Text(
                text = stringResource(R.string.live_tv_no_channels_title),
                style = if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                color = copyColor(1f),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = copyColor(0.75f),
            )
        }
    }
}

/** Glass pages put copy straight on artwork, so it has to be white; classic pages keep the ink. */
@Composable
internal fun copyColor(alpha: Float): Color =
    if (LocalLiquidGlass.current) {
        Color.White.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
    }

/** The page inset shared by the channel grid and its headings. */
@Composable
internal fun pageStartPadding() = if (LocalLiquidGlass.current) GlassTokens.screenInset else 24.dp
