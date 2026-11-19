package com.github.damontecres.wholphin.ui.main

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.glassCardBorder
import com.github.damontecres.wholphin.ui.cards.glassCardGlow
import com.github.damontecres.wholphin.ui.cards.glassCardScale
import com.github.damontecres.wholphin.ui.cards.glassCardShape
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.livetv.LiveRed
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.theme.GlassPanel
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The new home surface.
 *
 * The page is deliberately a small number of distinct shapes rather than a stack of identical
 * rows: one lead panel, a live rail, one landscape Keep Going rail, one mixed New shelf, the
 * library's own recommendation rail, then the viewer's configured library rows. That rhythm is
 * what makes the page readable from the sofa before any card is focused.
 *
 * Every item is real. The live rail is fed only by actual `TvPrograms` rows with a channel id;
 * there is no synthetic schedule, no on-air channel, and no score placeholder. If the tuner has
 * nothing on, the rail is absent and the page becomes shorter rather than inventing a broadcast.
 */
@Composable
internal fun RenovatedHomeContent(
    homeRows: List<HomeRowLoadingState>,
    rowConfigs: List<HomeRowConfig>,
    onFocusPosition: (RowColumn) -> Unit,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onPlayItem: (BaseItem) -> Unit,
    onOpenItem: (BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
    forYouState: ForYouViewModel.State,
    showViewMore: Boolean,
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    val now = rememberTickingNow()
    val feed = remember(homeRows, rowConfigs, now) { buildHomeFeed(homeRows, rowConfigs, now) }
    val listState = rememberLazyListState()
    val leadFocus = remember { FocusRequester() }
    var initialFocusTaken by remember { mutableStateOf(false) }

    LaunchedEffect(feed.lead?.item?.id) {
        val item = feed.lead?.item ?: return@LaunchedEffect
        // The page opens on the action, not on a poster. A viewer sitting down should be able to
        // press Select once and start the thing the page is recommending.
        if (!initialFocusTaken) {
            repeat(12) {
                if (runCatching { leadFocus.requestFocus() }.isSuccess) {
                    initialFocusTaken = true
                    // Focus brings the action into view, and with it the whole lead card. The
                    // page should still open at its resting position, with the lead below the
                    // floating bar rather than tucked under it.
                    delay(120)
                    listState.scrollToItem(0)
                    return@LaunchedEffect
                }
                delay(50)
            }
        }
    }

    LaunchedEffect(feed.lead?.item?.id) {
        feed.lead?.item?.let(onUpdateBackdrop)
    }

    val excludeIds =
        remember(feed.lead, feed.keepGoing) {
            buildSet {
                feed.lead?.item?.let { add(it.data.seriesId ?: it.id) }
                feed.keepGoing.forEach { add(it.item.data.seriesId ?: it.item.id) }
                feed.newItems.forEach { add(it.item.data.seriesId ?: it.item.id) }
            }
        }
    val liveRail =
        if (feed.leadKind == HomeLeadKind.LIVE) {
            feed.live.filterNot { it.item.id == feed.lead?.item?.id }
        } else {
            feed.live
        }
    val forYouItems =
        remember(forYouState, excludeIds) {
            (forYouState as? ForYouViewModel.State.Ready)
                ?.visibleItems(excludeIds)
                .orEmpty()
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding =
                PaddingValues(
                    top = if (glass) GlassTokens.contentTop else 8.dp,
                    bottom = Cards.height2x3,
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "lead") {
                val lead = feed.lead
                if (lead != null) {
                    HomeLead(
                        feedItem = lead,
                        kind = feed.leadKind,
                        now = now,
                        focusRequester = leadFocus,
                        onPlay = onPlayItem,
                        onDetails = onOpenItem,
                        onUpdateBackdrop = onUpdateBackdrop,
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                } else if (homeRows.any { it is HomeRowLoadingState.Pending || it is HomeRowLoadingState.Loading }) {
                    HomeLeadSkeleton()
                } else if (feed.isEmpty) {
                    HomeEmptyState()
                }
            }

            if (liveRail.isNotEmpty()) {
                item(key = "live") {
                    LiveNowSection(
                        items = liveRail,
                        now = now,
                        onFocusPosition = onFocusPosition,
                        onClick = onClickItem,
                        onUpdateBackdrop = onUpdateBackdrop,
                    )
                }
            }

            if (feed.keepGoing.isNotEmpty()) {
                item(key = "keep_going") {
                    KeepGoingSection(
                        items = feed.keepGoing,
                        onFocusPosition = onFocusPosition,
                        onClick = onClickItem,
                        onLongClick = onLongClickItem,
                        onPlay = onPlayItem,
                        onUpdateBackdrop = onUpdateBackdrop,
                    )
                }
            }

            if (feed.newItems.isNotEmpty()) {
                item(key = "new") {
                    NewSection(
                        items = feed.newItems,
                        onFocusPosition = onFocusPosition,
                        onClick = onClickItem,
                        onLongClick = onLongClickItem,
                        onPlay = onPlayItem,
                        onUpdateBackdrop = onUpdateBackdrop,
                    )
                }
            }

            // For You is local library material; it hides itself when there is not enough history
            // or fewer than four suitable titles. Keeping it here, below New, means the page's
            // recommendations never push the household's own shelves below the fold. Checking
            // the visible count here also avoids emitting an empty lazy item and its spacing.
            if (forYouState is ForYouViewModel.State.Ready && forYouItems.size >= FOR_YOU_MIN_VISIBLE) {
                item(key = "for_you") {
                    ForYouShelf(
                        state = forYouState,
                        excludeIds = excludeIds,
                        onClickItem = onOpenItem,
                        onPlayItem = onPlayItem,
                        onFocusItem = { item -> item?.let(onUpdateBackdrop) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (feed.library.isNotEmpty()) {
                item(key = "library_label") {
                    HomeSectionHeader(
                        title = stringResource(R.string.your_library),
                        subtitle = null,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(
                    items = feed.library,
                    key = { "library_${it.index}" },
                ) { libraryRow ->
                    LibraryShelf(
                        entry = libraryRow,
                        showViewMore = showViewMore,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        onPlayItem = onPlayItem,
                        onFocusPosition = onFocusPosition,
                        onUpdateBackdrop = onUpdateBackdrop,
                        onClickViewMore = onClickViewMore,
                    )
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }

        if (glass) {
            HomeChromeBand(listState)
        }
    }
}

@Composable
private fun HomeLead(
    feedItem: HomeFeedItem,
    kind: HomeLeadKind,
    now: LocalDateTime,
    focusRequester: FocusRequester,
    onPlay: (BaseItem) -> Unit,
    onDetails: (BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = feedItem.item
    val imageService = LocalImageUrlService.current
    val isLive = kind == HomeLeadKind.LIVE
    val backgroundUrl =
        remember(item.id, isLive) {
            item.imageUrlOverride
                ?: imageService.getItemImageUrl(
                    item = item,
                    imageType = ImageType.BACKDROP,
                    fillWidth = 1280,
                    useSeriesForPrimary = true,
                )
        }
    val tileUrl =
        remember(item.id, isLive) {
            imageService.getItemImageUrl(
                item = item,
                imageType = ImageType.PRIMARY,
                fillHeight = if (isLive) 176 else 276,
                useSeriesForPrimary = true,
            )
        }
    val title = item.title.orEmpty()
    val subtitle =
        item.name
            ?.takeIf { it.isNotBlank() && it != title }
            ?.takeIf { item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE }
    val channel = item.data.channelName
    val timeRange = rememberLocalTimeRange(item.data.startDate, item.data.endDate)
    val remaining =
        remember(item.id, item.data.userData) {
            item.timeRemainingOrRuntime
        }
    val remainingLabel = rememberTimeLeft(remaining)
    val progress =
        when {
            isLive -> {
                val start = item.data.startDate
                val end = item.data.endDate
                if (start != null && end != null) {
                    val total = java.time.Duration.between(start, end).toMillis().coerceAtLeast(1L)
                    val elapsed = java.time.Duration.between(start, now).toMillis().coerceIn(0L, total)
                    elapsed.toFloat() / total
                } else {
                    0f
                }
            }

            else -> ((item.data.userData?.playedPercentage ?: 0.0) / 100.0).toFloat()
        }
    val meta =
        remember(kind, channel, timeRange, remainingLabel, item.subtitleLong) {
            buildList {
                if (channel != null) add(channel)
                if (isLive && timeRange != null) add(timeRange)
                if (!isLive) item.subtitleLong?.let(::add)
                if (remainingLabel != null && !isLive) add(remainingLabel)
            }.joinToString(" · ")
        }
    val kicker =
        when (kind) {
            HomeLeadKind.LIVE -> stringResource(R.string.home_live_now)
            HomeLeadKind.CONTINUE -> stringResource(R.string.home_right_now)
            HomeLeadKind.NEW -> stringResource(R.string.home_new)
            HomeLeadKind.START -> stringResource(R.string.home_start_something)
        }.uppercase()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(204.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.55f)),
    ) {
        if (backgroundUrl != null) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF262330), Color(0xFF0E0D12)),
                        ),
                    ),
            )
        }
        // Two gradients, not a flat scrim: the left keeps the copy legible over a bright still,
        // the bottom grounds the action row. The artwork is still visible in the right half.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.48f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLive) {
                LiveLeadTile(channel = channel, modifier = Modifier.size(92.dp))
            } else if (tileUrl != null) {
                AsyncImage(
                    model = tileUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .width(92.dp)
                            .height(138.dp)
                            .clip(RoundedCornerShape(14.dp)),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = if (isLive || tileUrl != null) 18.dp else 0.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (isLive) {
                        Box(Modifier.size(7.dp).background(LiveRed, CircleShape))
                    }
                    Text(
                        text = kicker,
                        style = GlassType.sectionLabel,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp,
                    )
                }
                Text(
                    text = title,
                    style =
                        GlassType.hero.copy(
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            letterSpacing = (-0.7).sp,
                        ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = GlassType.subtitle,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = GlassType.meta,
                        color = Color.White.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.hasFocus) onUpdateBackdrop(item)
                        },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassActionButton(
                            text =
                                stringResource(
                                    when {
                                        isLive -> R.string.watch_live
                                        kind == HomeLeadKind.CONTINUE -> R.string.liquid_glass_resume
                                        else -> R.string.liquid_glass_play
                                    },
                                ),
                            icon = Icons.Default.PlayArrow,
                            primary = true,
                            onClick = { onPlay(item) },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                        if (!isLive) {
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

        if (progress > 0f) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.58f)
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(if (isLive) LiveRed else Color.White.copy(alpha = 0.92f)),
                )
            }
        }
    }
}

@Composable
private fun LiveLeadTile(
    channel: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.50f)),
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(LiveRed, CircleShape),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.sports_live).uppercase(),
            style = GlassType.sectionLabel,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        channel?.let {
            Text(
                text = it,
                style = GlassType.posterBadge,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LiveNowSection(
    items: List<HomeFeedItem>,
    now: LocalDateTime,
    onFocusPosition: (RowColumn) -> Unit,
    onClick: (RowColumn, BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(LiveRed, CircleShape))
            Text(
                text = stringResource(R.string.live_tv_sports_on_now).uppercase(),
                style = GlassType.sectionLabel,
                color = Color.White.copy(alpha = 0.84f),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
            )
            Text(
                text = stringResource(R.string.home_live_count, items.size),
                style = GlassType.meta,
                color = Color.White.copy(alpha = 0.56f),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp),
        ) {
            items(items, key = { it.item.id }) { live ->
                LiveNowCard(
                    feedItem = live,
                    now = now,
                    onFocusPosition = onFocusPosition,
                    onClick = onClick,
                    onUpdateBackdrop = onUpdateBackdrop,
                )
            }
        }
    }
}

@Composable
private fun LiveNowCard(
    feedItem: HomeFeedItem,
    now: LocalDateTime,
    onFocusPosition: (RowColumn) -> Unit,
    onClick: (RowColumn, BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
) {
    val item = feedItem.item
    val timeRange = rememberLocalTimeRange(item.data.startDate, item.data.endDate)
    val title = item.name ?: item.title.orEmpty()
    Card(
        onClick = { onClick(feedItem.position, item) },
        onLongClick = {},
        modifier =
            Modifier
                .width(296.dp)
                .height(76.dp)
                .onFocusChanged {
                    if (it.isFocused) {
                        onFocusPosition(feedItem.position)
                        onUpdateBackdrop(item)
                    }
                }
                .onKeyEvent { event ->
                    if (isPlayKeyUp(event)) {
                        onClick(feedItem.position, item)
                        true
                    } else {
                        false
                    }
                },
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Black.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LiveRed),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sports_live).uppercase(),
                        style = GlassType.posterBadge,
                        color = LiveRed,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                    item.data.channelName?.let {
                        Text(
                            text = it,
                            style = GlassType.posterBadge,
                            color = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = title,
                    style = GlassType.tab.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(timeRange, remainingLiveLabel(item, now)).joinToString(" · "),
                    style = GlassType.posterBadge,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KeepGoingSection(
    items: List<HomeFeedItem>,
    onFocusPosition: (RowColumn) -> Unit,
    onClick: (RowColumn, BaseItem) -> Unit,
    onLongClick: (RowColumn, BaseItem) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_keep_going),
            subtitle = stringResource(R.string.home_pick_up),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            items(items, key = { it.item.data.seriesId ?: it.item.id }) { feedItem ->
                KeepGoingCard(
                    feedItem = feedItem,
                    onFocusPosition = onFocusPosition,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onPlay = onPlay,
                    onUpdateBackdrop = onUpdateBackdrop,
                )
            }
        }
    }
}

@Composable
private fun KeepGoingCard(
    feedItem: HomeFeedItem,
    onFocusPosition: (RowColumn) -> Unit,
    onClick: (RowColumn, BaseItem) -> Unit,
    onLongClick: (RowColumn, BaseItem) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
) {
    val item = feedItem.item
    val imageUrl = rememberItemImage(item, ImageType.THUMB, fillWidth = 560, wideFallback = true)
    val series = item.title.orEmpty()
    val episode = item.name?.takeIf { it != series }
    val corner = item.ui.episodeCornerText
    val timeLeft = rememberTimeLeft(item.timeRemainingOrRuntime)
    val progress = ((item.data.userData?.playedPercentage ?: 0.0) / 100.0).toFloat()
    Card(
        onClick = { onClick(feedItem.position, item) },
        onLongClick = { onLongClick(feedItem.position, item) },
        modifier =
            Modifier
                .width(280.dp)
                .height(158.dp)
                .onFocusChanged {
                    if (it.isFocused) {
                        onFocusPosition(feedItem.position)
                        onUpdateBackdrop(item)
                    }
                }
                .onKeyEvent { event ->
                    if (isPlayKeyUp(event) && item.playable) {
                        onPlay(item)
                        true
                    } else {
                        false
                    }
                },
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Black.copy(alpha = 0.30f)),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF292532), Color(0xFF121116)),
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )
            if (corner != null) {
                Text(
                    text = corner,
                    style = GlassType.posterBadge,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    text = series,
                    style = GlassType.tab.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(episode, timeLeft).joinToString(" · "),
                    style = GlassType.posterBadge,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress > 0f) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(Color.White.copy(alpha = 0.92f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewSection(
    items: List<HomeFeedItem>,
    onFocusPosition: (RowColumn) -> Unit,
    onClick: (RowColumn, BaseItem) -> Unit,
    onLongClick: (RowColumn, BaseItem) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_new),
            subtitle = stringResource(R.string.home_new_subtitle),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.item.id }) { index, feedItem ->
                NewCard(
                    feedItem = feedItem,
                    featured = index == 0,
                    onFocusPosition = onFocusPosition,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onPlay = onPlay,
                    onUpdateBackdrop = onUpdateBackdrop,
                )
            }
        }
    }
}

@Composable
private fun NewCard(
    feedItem: HomeFeedItem,
    featured: Boolean,
    onFocusPosition: (RowColumn) -> Unit,
    onClick: (RowColumn, BaseItem) -> Unit,
    onLongClick: (RowColumn, BaseItem) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
) {
    val item = feedItem.item
    val imageType = if (featured) ImageType.THUMB else ImageType.PRIMARY
    val imageUrl = rememberItemImage(item, imageType, fillWidth = if (featured) 600 else 220, wideFallback = featured)
    val title = item.title.orEmpty()
    val meta = item.subtitleLong ?: item.subtitle
    Card(
        onClick = { onClick(feedItem.position, item) },
        onLongClick = { onLongClick(feedItem.position, item) },
        modifier =
            Modifier
                .width(if (featured) 300.dp else 100.dp)
                .height(169.dp)
                .onFocusChanged {
                    if (it.isFocused) {
                        onFocusPosition(feedItem.position)
                        onUpdateBackdrop(item)
                    }
                }
                .onKeyEvent { event ->
                    if (isPlayKeyUp(event) && item.playable) {
                        onPlay(item)
                        true
                    } else {
                        false
                    }
                },
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Black.copy(alpha = 0.28f)),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2B2734), Color(0xFF111015)),
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )
            if (featured) {
                Text(
                    text = stringResource(R.string.home_new).uppercase(),
                    style = GlassType.posterBadge,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(LiveRed.copy(alpha = 0.90f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = if (featured) 14.dp else 8.dp,
                            end = if (featured) 14.dp else 8.dp,
                            bottom = 10.dp,
                        ),
            ) {
                Text(
                    text = title,
                    style =
                        if (featured) {
                            GlassType.tab.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        } else {
                            GlassType.posterBadge.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        },
                    color = Color.White,
                    maxLines = if (featured) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (featured && meta != null) {
                    Text(
                        text = meta,
                        style = GlassType.posterBadge,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryShelf(
    entry: HomeLibraryRow,
    showViewMore: Boolean,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onPlayItem: (BaseItem) -> Unit,
    onFocusPosition: (RowColumn) -> Unit,
    onUpdateBackdrop: (BaseItem) -> Unit,
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit,
) {
    val row = entry.row
    val viewOptions = row.viewOptions
    ItemRow(
        title = row.title.getString(),
        items = row.items,
        onClickItem = { index, item ->
            val position = RowColumn(entry.index, index)
            onFocusPosition(position)
            onClickItem(position, item)
        },
        onLongClickItem = { index, item ->
            val position = RowColumn(entry.index, index)
            onFocusPosition(position)
            onLongClickItem(position, item)
        },
        showViewMore = showViewMore && row.showViewMore,
        horizontalPadding = viewOptions.spacing.dp,
        modifier = Modifier.fillMaxWidth(),
        cardContent = { index, item, cardModifier, onClick, onLongClick ->
            val position = RowColumn(entry.index, index)
            HomePageCardContent(
                index = index,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                viewOptions = viewOptions,
                modifier =
                    cardModifier
                        .onFocusChanged {
                            if (it.isFocused) {
                                onFocusPosition(position)
                                item?.let(onUpdateBackdrop)
                            }
                        }
                        .onKeyEvent { event ->
                            if (isPlayKeyUp(event) && item?.playable == true) {
                                onPlayItem(requireNotNull(item))
                                true
                            } else {
                                false
                            }
                        },
            )
        },
        viewMoreCardContent = { cardModifier ->
            HomePageViewMoreCard(
                isEpisode = row.items.lastOrNull()?.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE,
                onClick = { onClickViewMore(RowColumn(entry.index, row.items.size), row) },
                onLongClick = {},
                viewOptions = viewOptions,
                modifier =
                    cardModifier.onFocusChanged {
                        if (it.isFocused) onFocusPosition(RowColumn(entry.index, row.items.size))
                    },
            )
        },
    )
}

@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = GlassType.rowTitle,
            color = homeInk(1f),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = GlassType.chip.copy(fontSize = 13.sp),
                color = homeInk(0.58f),
            )
        }
    }
}

@Composable
private fun HomeChromeBand(listState: LazyListState) {
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 10
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(160),
        label = "home_chrome_band",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(GlassTokens.contentTop + 44.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.56f),
                            Color.Black.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                    ),
                ),
    )
}

@Composable
private fun HomeLeadSkeleton() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(204.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.06f)),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.CenterStart).padding(24.dp),
        ) {
            Box(Modifier.width(72.dp).height(10.dp).background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50)))
            Box(Modifier.width(320.dp).height(30.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)))
            Box(Modifier.width(220.dp).height(12.dp).background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50)))
            Box(Modifier.width(150.dp).height(38.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun HomeEmptyState() {
    val glass = LocalLiquidGlass.current
    val content: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.home_empty_title),
                style = GlassType.rowTitle,
                color = homeInk(1f),
            )
            Text(
                text = stringResource(R.string.home_empty_body),
                style = GlassType.meta,
                color = homeInk(0.64f),
            )
        }
    }
    if (glass) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth().height(132.dp),
            scrim = 1f,
        ) { content() }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(18.dp)),
        ) { content() }
    }
}

@Composable
private fun homeInk(alpha: Float): Color =
    if (LocalLiquidGlass.current) Color.White.copy(alpha = alpha) else MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)

@Composable
private fun rememberTickingNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    return now
}

@Composable
private fun rememberLocalTimeRange(
    start: LocalDateTime?,
    end: LocalDateTime?,
): String? {
    val context = LocalContext.current
    return remember(start, end) {
        if (start == null || end == null) {
            null
        } else {
            val formatter = DateFormat.getTimeFormat(context)
            fun format(value: LocalDateTime): String =
                formatter.format(Date.from(value.atZone(ZoneId.systemDefault()).toInstant()))
            "${format(start)} – ${format(end)}"
        }
    }
}

@Composable
private fun rememberTimeLeft(duration: Duration?): String? {
    val resources = LocalContext.current.resources
    return remember(duration) {
        duration
            ?.roundMinutes
            ?.takeIf { it > Duration.ZERO }
            ?.let { resources.getString(R.string.time_left, resources.formatDuration(it)) }
    }
}

@Composable
private fun remainingLiveLabel(
    item: BaseItem,
    now: LocalDateTime,
): String? {
    val end = item.data.endDate ?: return null
    val minutes = java.time.Duration.between(now, end).toMinutes().coerceAtLeast(0)
    if (minutes <= 0) return null
    val resources = LocalContext.current.resources
    return resources.getString(
        R.string.time_left,
        resources.formatDuration(minutes.minutes),
    )
}

@Composable
private fun rememberItemImage(
    item: BaseItem,
    imageType: ImageType,
    fillWidth: Int,
    wideFallback: Boolean,
): String? {
    val imageService = LocalImageUrlService.current
    return remember(item.id, imageType, fillWidth, wideFallback) {
        item.imageUrlOverride
            ?: imageService.getItemImageUrl(
                item = item,
                imageType = imageType,
                fillWidth = fillWidth,
                useSeriesForPrimary = true,
            )
            ?: if (wideFallback) {
                imageService.getItemImageUrl(
                    item = item,
                    imageType = ImageType.BACKDROP,
                    fillWidth = fillWidth,
                    useSeriesForPrimary = true,
                )
            } else {
                null
            }
    }
}
