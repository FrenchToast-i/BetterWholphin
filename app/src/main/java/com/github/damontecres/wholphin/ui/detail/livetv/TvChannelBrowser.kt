package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.cards.glassCardBorder
import com.github.damontecres.wholphin.ui.cards.glassCardGlow
import com.github.damontecres.wholphin.ui.cards.glassCardScale
import com.github.damontecres.wholphin.ui.cards.glassCardShape
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import java.time.LocalDateTime
import java.util.UUID

private enum class ChannelSort {
    NUMBER,
    NAME,
    RECENT,
}

/**
 * Live TV when there is nothing to schedule.
 *
 * A tuner that has just been added has channels and no guide. The grid below is the answer: every
 * channel plays immediately, and the copy names the missing piece instead of leaving forty-eight
 * hours of "No data" on screen.
 */
@Composable
fun LiveTvChannelsTab(
    onClickChannel: (TvChannel) -> Unit,
    onLongClickChannel: (TvChannel) -> Unit = {},
    onClearFilters: () -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    tabFocusRequester: FocusRequester,
    channelFilter: (TvChannel) -> Boolean = { true },
    categoryForChannel: (UUID) -> ProgramCategory? = { null },
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val recentIds by viewModel.recentChannelIds.collectAsState()
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
            val channels =
                remember(state.channels, channelFilter) {
                    state.channels.filter(channelFilter)
                }
            if (channels.isEmpty()) {
                NoChannelsMessage(
                    message =
                        if (state.channels.isEmpty()) {
                            stringResource(R.string.live_tv_no_channels_message)
                        } else {
                            stringResource(R.string.live_tv_filter_empty)
                        },
                    modifier = modifier,
                    action = if (state.channels.isEmpty()) null else onClearFilters,
                )
            } else {
                val now = rememberTickingNow()
                val liveById =
                    remember(state.currentPrograms, state.programs.programsByChannel, now) {
                        liveProgramsByChannel(state.currentPrograms, state.programs.programsByChannel, now)
                    }
                var showList by rememberSaveable { mutableStateOf<Boolean?>(null) }
                val list = showList ?: (channels.size > 60)
                var sort by rememberSaveable { mutableStateOf(ChannelSort.NUMBER) }
                // The sort controls sit at the far right of the toolbar while the first cards are
                // under its empty left half, so the D-pad's geometric focus search skipped them
                // and jumped to the tab strip. Cards and rows now name this requester as their Up
                // target, which guarantees the toolbar is one press away from every item.
                val listActionsFocus = remember { FocusRequester() }
                val sorted =
                    remember(channels, sort, recentIds, liveById) {
                        when (sort) {
                            ChannelSort.NUMBER ->
                                channels.sortedWith(
                                    compareBy(
                                        { it.number?.toDoubleOrNull() ?: Double.MAX_VALUE },
                                        { it.number.orEmpty() },
                                        { it.name.orEmpty() },
                                    ),
                                )
                            ChannelSort.NAME -> channels.sortedBy { it.name.orEmpty().lowercase() }
                            ChannelSort.RECENT ->
                                channels.sortedWith(
                                    compareBy(
                                        { recentIds.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
                                        { it.number?.toDoubleOrNull() ?: Double.MAX_VALUE },
                                    ),
                                )
                        }
                    }
                Column(modifier = modifier) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = pageStartPadding(), end = 24.dp, bottom = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.live_tv_channel_count, sorted.size),
                            style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodyMedium,
                            color = copyColor(0.72f),
                        )
                        val channelIds = remember(sorted) { sorted.map { it.id }.toSet() }
                        Text(
                            text = stringResource(R.string.live_tv_live_count, liveById.keys.count { it in channelIds }),
                            style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodySmall,
                            color = copyColor(0.5f),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = sortLabel(sort),
                            style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodySmall,
                            color = copyColor(0.55f),
                        )
                        TextButton(
                            onClick = {
                                sort =
                                    when (sort) {
                                        ChannelSort.NUMBER -> ChannelSort.NAME
                                        ChannelSort.NAME -> ChannelSort.RECENT
                                        ChannelSort.RECENT -> ChannelSort.NUMBER
                                    }
                            },
                            modifier =
                                Modifier
                                    .focusRequester(listActionsFocus)
                                    .focusProperties { up = tabFocusRequester },
                        ) {
                            Text(stringResource(R.string.live_tv_sort))
                        }
                        ExpandableFaButton(
                            title = if (list) R.string.live_tv_grid else R.string.live_tv_list,
                            iconStringRes = if (list) R.string.fa_table_cells_large else R.string.fa_list_ul,
                            onClick = { showList = !list },
                            size = 40.dp,
                            expandLabel = true,
                            modifier = Modifier.focusProperties { up = tabFocusRequester },
                        )
                    }
                    if (list) {
                        ChannelList(
                            channels = sorted,
                            liveById = liveById,
                            programsByChannel = state.programs.programsByChannel,
                            now = now,
                            categoryForChannel = categoryForChannel,
                            onClickChannel = onClickChannel,
                            onLongClickChannel = onLongClickChannel,
                            focusRequester = focusRequester,
                            upFocusRequester = listActionsFocus,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TvChannelGrid(
                            channels = sorted,
                            onClickChannel = onClickChannel,
                            onLongClickChannel = onLongClickChannel,
                            modifier = Modifier.fillMaxSize(),
                            focusRequester = focusRequester,
                            upFocusRequester = listActionsFocus,
                            nowById = liveById,
                            now = now,
                            nextById =
                                remember(state.programs.programsByChannel, now) {
                                    sorted.associate { channel ->
                                        channel.id to nextProgramForChannel(state.programs.programsByChannel, channel.id, now)
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<TvChannel>,
    liveById: Map<UUID, TvProgram>,
    programsByChannel: Map<UUID, List<TvProgram>>,
    now: LocalDateTime,
    categoryForChannel: (UUID) -> ProgramCategory?,
    onClickChannel: (TvChannel) -> Unit,
    onLongClickChannel: (TvChannel) -> Unit,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val grouped =
        remember(channels, categoryForChannel) {
            channels.groupBy { categoryForChannel(it.id) }
        }
    val order = listOf(ProgramCategory.SPORTS, ProgramCategory.NEWS, ProgramCategory.KIDS, ProgramCategory.MOVIE, null)
    val groups = order.mapNotNull { category -> grouped[category]?.let { category to it } }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(start = pageStartPadding(), end = 24.dp, bottom = 32.dp),
        modifier = modifier,
    ) {
        groups.forEachIndexed { groupIndex, (category, groupChannels) ->
            if (groups.size > 1) {
                item(key = "header_${category?.name ?: "other"}") {
                    Text(
                        text = category?.let { categoryLabel(it) } ?: stringResource(R.string.live_tv_category_other),
                        style = if (LocalLiquidGlass.current) GlassType.rowTitle else MaterialTheme.typography.titleMedium,
                        color = copyColor(0.9f),
                        modifier = Modifier.padding(top = if (groupIndex == 0) 2.dp else 14.dp, bottom = 4.dp),
                    )
                }
            }
            itemsIndexed(groupChannels, key = { _, channel -> channel.id }) { index, channel ->
                ChannelListRow(
                    channel = channel,
                    current = liveById[channel.id],
                    next = nextProgramForChannel(programsByChannel, channel.id, now),
                    now = now,
                    onClick = { onClickChannel(channel) },
                    onLongClick = { onLongClickChannel(channel) },
                    modifier =
                        Modifier
                            .then(
                                if (groupIndex == 0 && index == 0 && focusRequester != null) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                            ).then(
                                if (upFocusRequester != null) {
                                    Modifier.focusProperties { up = upFocusRequester }
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
        }
    }
    if (focusRequester != null && channels.isNotEmpty()) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    }
}

@Composable
private fun ChannelListRow(
    channel: TvChannel,
    current: TvProgram?,
    next: TvProgram?,
    now: LocalDateTime,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    val live = current?.isLiveAt(now) == true
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(1f, 1f, 0.99f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (glass) Color.White.copy(alpha = 0.055f) else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                contentColor = copyColor(1f),
                focusedContainerColor = if (glass) Color.White else MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = if (glass) Color.Black else MaterialTheme.colorScheme.inverseOnSurface,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 14.dp),
        ) {
            Text(
                text = channel.number.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.width(48.dp),
                maxLines = 1,
            )
            Box(
                modifier =
                    Modifier
                        .size(width = 76.dp, height = 46.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = if (glass) 0.18f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.imageUrl != null) {
                    AsyncImage(
                        model = channel.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                } else {
                    Text(
                        text = channel.name.orEmpty().take(2),
                        style = MaterialTheme.typography.titleSmall,
                        color = copyColor(0.7f),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = channel.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.favorite) {
                        Text(
                            text = stringResource(R.string.fa_heart),
                            fontFamily = FontAwesome,
                            fontSize = 11.sp,
                            color = LiveRed,
                        )
                    }
                }
                Text(
                    text =
                        when {
                            current == null && next == null -> stringResource(R.string.live_tv_no_guide_for_channel)
                            // Not every channel is on air between programmes. With a next
                            // programme known, "no guide data" was plainly wrong.
                            current == null || !current.isLiveAt(now) -> stringResource(R.string.live_tv_no_live_program)
                            // A sports tuner's channel name is the fixture. Repeating it under
                            // itself said nothing; the time window is the useful second line.
                            current.name.equals(channel.name, ignoreCase = true) -> current.quickDetails.toString()
                            else -> current.name.orEmpty()
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = if (live) 0.92f else 0.55f),
                )
                if (live) {
                    Box(
                        Modifier
                            .fillMaxWidth(current.progressAt(now))
                            .height(2.dp)
                            .background(LiveRed, RoundedCornerShape(1.dp)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(130.dp)) {
                next?.let {
                    Text(
                        text = stringResource(R.string.live_tv_next_at, formatClock(it.start)),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                    Text(
                        text = it.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = 0.68f),
                    )
                }
            }
        }
    }
}

/** Kept alongside the channel browser because the no-guide answer is the channel grid. */
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
 * The card carries the now programme when one is available, because a name and a logo do not tell a
 * viewer what they are tuning into. Long press reaches the channel actions; OK still tunes.
 */
@Composable
fun TvChannelGrid(
    channels: List<TvChannel>,
    onClickChannel: (TvChannel) -> Unit,
    modifier: Modifier = Modifier,
    onLongClickChannel: (TvChannel) -> Unit = {},
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    nowById: Map<UUID, TvProgram> = emptyMap(),
    nextById: Map<UUID, TvProgram?> = emptyMap(),
    now: LocalDateTime = LocalDateTime.now(),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 232.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding =
            PaddingValues(
                start = pageStartPadding(),
                end = 24.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = 32.dp,
            ),
        modifier = modifier,
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            TvChannelCard(
                channel = channel,
                current = nowById[channel.id],
                next = nextById[channel.id],
                now = now,
                onClick = { onClickChannel(channel) },
                onLongClick = { onLongClickChannel(channel) },
                modifier =
                    Modifier
                        .then(
                            if (index == 0 && focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                        ).then(
                            if (upFocusRequester != null) {
                                Modifier.focusProperties { up = upFocusRequester }
                            } else {
                                Modifier
                            },
                        ),
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
    current: TvProgram?,
    next: TvProgram?,
    now: LocalDateTime,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val glass = LocalLiquidGlass.current
    val live = current != null
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        onLongClick = onLongClick,
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
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                            ),
                        ).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    channel.number?.let {
                        Text(
                            text = "#$it",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.66f),
                        )
                    }
                    if (live) {
                        Box(Modifier.size(5.dp).background(LiveRed, CircleShape))
                    }
                }
                Text(
                    text = channel.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val nowName = current?.name?.takeIf { !it.equals(channel.name, ignoreCase = true) }
                val line =
                    nowName
                        ?: current?.takeIf { it.isLiveAt(now) }?.quickDetails?.toString()
                        ?: next?.let { stringResource(R.string.live_tv_next_label, it.name.orEmpty()) }
                line?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (live) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(current.progressAt(now))
                        .height(2.dp)
                        .background(LiveRed),
                )
            }
        }
    }
}

@Composable
private fun NoChannelsMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            action?.let {
                TextButton(onClick = it) {
                    Text(stringResource(R.string.live_tv_clear_filters))
                }
            }
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

@Composable
private fun sortLabel(sort: ChannelSort): String =
    when (sort) {
        ChannelSort.NUMBER -> stringResource(R.string.live_tv_sort_number)
        ChannelSort.NAME -> stringResource(R.string.live_tv_sort_name)
        ChannelSort.RECENT -> stringResource(R.string.live_tv_sort_recent)
    }

private fun formatClock(time: LocalDateTime): String =
    time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))
