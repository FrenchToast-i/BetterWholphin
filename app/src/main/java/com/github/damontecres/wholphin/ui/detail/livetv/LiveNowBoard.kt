package com.github.damontecres.wholphin.ui.detail.livetv

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.cards.glassCardBorder
import com.github.damontecres.wholphin.ui.cards.glassCardGlow
import com.github.damontecres.wholphin.ui.cards.glassCardScale
import com.github.damontecres.wholphin.ui.cards.glassCardShape
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * The answer to "what is on?".
 *
 * A live guide is a grid of decisions; this is the page that makes the first decision for the
 * viewer. The best live programme gets the largest card, every other live channel follows, and
 * programmes that are about to start sit beside them. Nothing is invented: every card comes from the
 * tuner's current programme or the next three hours of its guide.
 */
@Composable
fun LiveTvNowTab(
    channels: List<TvChannel>,
    channelsById: Map<UUID, TvChannel>,
    livePrograms: Map<UUID, TvProgram>,
    startingSoonPrograms: List<TvProgram>,
    recentChannelIds: List<UUID>,
    now: LocalDateTime,
    onTuneChannel: (TvChannel) -> Unit,
    onProgramActions: (TvProgram) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val visibleChannels = remember(channels) { channels.mapNotNull { it.id }.toSet() }
    val live =
        remember(livePrograms, visibleChannels, now) {
            livePrograms.values
                .filter { it.channelId in visibleChannels && it.isLiveAt(now) }
                .sortedWith(
                    compareByDescending<TvProgram> { it.category == ProgramCategory.SPORTS }
                        .thenByDescending { channelsById[it.channelId]?.favorite == true }
                        .thenByDescending { it.channelId in recentChannelIds },
                )
        }
    val upNext =
        remember(startingSoonPrograms, visibleChannels, now) {
            startingSoonPrograms.filter { it.channelId in visibleChannels }
                .sortedBy { it.start }
                .distinctBy { it.channelId }
                .take(20)
        }
    val next =
        remember(upNext, now) {
            upNext.firstOrNull { it.start.isAfter(now) }
        }
    val hero = live.firstOrNull() ?: next
    val otherLive = live.drop(1)
    val sportLive = otherLive.filter { it.category == ProgramCategory.SPORTS }
    val generalLive = otherLive.filterNot { it.category == ProgramCategory.SPORTS }
    val recent =
        remember(channels, recentChannelIds) {
            recentChannelIds.mapNotNull { id -> channels.firstOrNull { it.id == id } }.take(8)
        }
    val fallbackChannels = channels.take(12)
    val listState = rememberLazyListState()
    val heroFocus = remember { FocusRequester() }
    val fellBackFocus = remember { FocusRequester() }
    var focusSettled by remember { mutableStateOf(false) }
    LaunchedEffect(hero?.id) {
        if (!focusSettled) {
            val target = if (hero != null) heroFocus else fellBackFocus
            target.tryRequestFocus()
            focusSettled = true
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "now_title") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = pageStartPadding(), end = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.live_tv_now_title),
                    style = if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                    color = copyColor(1f),
                )
                Text(
                    text = stringResource(R.string.live_tv_now_summary, live.size, upNext.size),
                    style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodyMedium,
                    color = copyColor(0.62f),
                )
            }
        }
        if (hero != null) {
            item(key = "hero_${hero.id}") {
                NowHero(
                    program = hero,
                    channel = channelsById[hero.channelId],
                    now = now,
                    onWatch = {
                        if (hero.isLiveAt(now)) {
                            channelsById[hero.channelId]?.let(onTuneChannel)
                        } else {
                            onProgramActions(hero)
                        }
                    },
                    onLongClick = { onProgramActions(hero) },
                    modifier = Modifier.focusRequester(heroFocus),
                )
            }
        } else {
            item(key = "empty") {
                EmptyNow(
                    channels = fallbackChannels,
                    onTune = onTuneChannel,
                    focusRequester = fellBackFocus,
                )
            }
        }
        if (sportLive.isNotEmpty()) {
            item(key = "sport") {
                NowRail(
                    title = stringResource(R.string.live_tv_now_sport),
                    subtitle = stringResource(R.string.live_tv_now_sport_count, sportLive.size),
                    programs = sportLive,
                    channelsById = channelsById,
                    now = now,
                    onTune = { program -> channelsById[program.channelId]?.let(onTuneChannel) },
                    onLongClick = onProgramActions,
                    live = true,
                )
            }
        }
        if (generalLive.isNotEmpty()) {
            item(key = "live") {
                NowRail(
                    title = stringResource(R.string.live_tv_now_also),
                    subtitle = stringResource(R.string.live_tv_now_also_subtitle),
                    programs = generalLive,
                    channelsById = channelsById,
                    now = now,
                    onTune = { program -> channelsById[program.channelId]?.let(onTuneChannel) },
                    onLongClick = onProgramActions,
                    live = true,
                )
            }
        }
        if (upNext.isNotEmpty()) {
            item(key = "soon") {
                NowRail(
                    title = stringResource(R.string.live_tv_starting_soon),
                    subtitle = stringResource(R.string.live_tv_starting_soon_subtitle),
                    programs = upNext,
                    channelsById = channelsById,
                    now = now,
                    onTune = { program ->
                        if (program.start.isAfter(now)) onProgramActions(program) else {
                            channelsById[program.channelId]?.let(onTuneChannel)
                        }
                    },
                    onLongClick = onProgramActions,
                    live = false,
                )
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                ChannelRail(
                    title = stringResource(R.string.live_tv_recent_channels),
                    channels = recent,
                    now = now,
                    livePrograms = livePrograms,
                    onTune = onTuneChannel,
                    onLongClick = {},
                )
            }
        }
        if (live.isEmpty() && upNext.isEmpty() && fallbackChannels.isNotEmpty()) {
            item(key = "channels") {
                ChannelRail(
                    title = stringResource(R.string.live_tv_channels),
                    channels = fallbackChannels,
                    now = now,
                    livePrograms = livePrograms,
                    onTune = onTuneChannel,
                    onLongClick = {},
                )
            }
        }
    }
}

@Composable
private fun NowHero(
    program: TvProgram,
    channel: TvChannel?,
    now: LocalDateTime,
    onWatch: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val live = program.isLiveAt(now)
    val image = program.imageUrl ?: channel?.imageUrl
    val glass = LocalLiquidGlass.current
    Card(
        onClick = onWatch,
        onLongClick = onLongClick,
        modifier =
            modifier
                .padding(horizontal = pageStartPadding())
                .fillMaxWidth()
                .height(190.dp),
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (glass) 0.28f else 0.55f))) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.00f),
                                0.42f to Color.Black.copy(alpha = 0.58f),
                                1f to Color.Black.copy(alpha = 0.94f),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.58f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f),
                            ),
                        ),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.66f)
                        .padding(horizontal = 22.dp, vertical = 18.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (live) {
                        LiveBadge()
                    } else {
                        Text(
                            text = stringResource(R.string.live_tv_next_up),
                            style = if (glass) GlassType.sectionLabel else MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.76f),
                        )
                    }
                    channel?.number?.let {
                        Text(
                            text = "#$it",
                            style = if (glass) GlassType.meta else MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Text(
                    text = program.name.orEmpty(),
                    style = if (glass) GlassType.sectionTitle else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = program.quickDetails,
                    style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.White)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(if (live) R.string.watch_live else R.string.live_tv_details),
                            style = if (glass) GlassType.button else MaterialTheme.typography.labelLarge,
                            color = Color.Black,
                        )
                    }
                    channel?.name
                        ?.takeIf { !it.equals(program.name, ignoreCase = true) }
                        ?.let {
                            Text(
                                text = it,
                                style = if (glass) GlassType.meta else MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
            }
            if (live) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(program.progressAt(now))
                        .height(3.dp)
                        .background(LiveRed, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)),
                )
            }
        }
    }
}

@Composable
private fun NowRail(
    title: String,
    subtitle: String,
    programs: List<TvProgram>,
    channelsById: Map<UUID, TvChannel>,
    now: LocalDateTime,
    onTune: (TvProgram) -> Unit,
    onLongClick: (TvProgram) -> Unit,
    live: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(start = pageStartPadding(), end = 24.dp),
        ) {
            Text(
                text = title,
                style = if (LocalLiquidGlass.current) GlassType.rowTitle else MaterialTheme.typography.titleLarge,
                color = copyColor(1f),
            )
            Text(
                text = subtitle,
                style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodySmall,
                color = copyColor(0.5f),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = pageStartPadding(), end = 24.dp, top = 2.dp, bottom = 4.dp),
        ) {
            itemsIndexed(programs, key = { _, program -> program.id }) { _, program ->
                NowProgramCard(
                    program = program,
                    channel = channelsById[program.channelId],
                    now = now,
                    live = live || program.isLiveAt(now),
                    onClick = { onTune(program) },
                    onLongClick = { onLongClick(program) },
                )
            }
        }
    }
}

@Composable
private fun NowProgramCard(
    program: TvProgram,
    channel: TvChannel?,
    now: LocalDateTime,
    live: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val image = program.imageUrl ?: channel?.imageUrl
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.width(270.dp).aspectRatio(16f / 9f),
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.72f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (live) {
                LiveBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = soonLabel(program.start, now),
                        style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
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
                        ).padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = program.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                            channel?.number?.let { "#$it" },
                            channel?.name?.takeIf { !it.equals(program.name, ignoreCase = true) },
                        ).joinToString("  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (live) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(program.progressAt(now))
                        .height(2.dp)
                        .background(LiveRed),
                )
            }
        }
    }
}

@Composable
private fun ChannelRail(
    title: String,
    channels: List<TvChannel>,
    now: LocalDateTime,
    livePrograms: Map<UUID, TvProgram>,
    onTune: (TvChannel) -> Unit,
    onLongClick: (TvChannel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = if (LocalLiquidGlass.current) GlassType.rowTitle else MaterialTheme.typography.titleLarge,
            color = copyColor(1f),
            modifier = Modifier.padding(start = pageStartPadding()),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = pageStartPadding(), end = 24.dp, bottom = 4.dp),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { _, channel ->
                ChannelRailCard(
                    channel = channel,
                    current = livePrograms[channel.id],
                    now = now,
                    onClick = { onTune(channel) },
                    onLongClick = { onLongClick(channel) },
                )
            }
        }
    }
}

@Composable
private fun ChannelRailCard(
    channel: TvChannel,
    current: TvProgram?,
    now: LocalDateTime,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(230.dp).height(112.dp),
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.66f)),
                    ),
                ),
        ) {
            channel.imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.48f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
            ) {
                Text(
                    text = listOfNotNull(channel.number?.let { "#$it" }, channel.name).joinToString("  "),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                current?.let {
                    Text(
                        text =
                            if (it.isLiveAt(now)) {
                                it.name.orEmpty()
                            } else {
                                stringResource(R.string.live_tv_no_live_program)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.isLiveAt(now)) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (current?.isLiveAt(now) == true) {
                    Box(
                        Modifier
                            .fillMaxWidth(current.progressAt(now))
                            .height(2.dp)
                            .background(LiveRed, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNow(
    channels: List<TvChannel>,
    onTune: (TvChannel) -> Unit,
    focusRequester: FocusRequester,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = pageStartPadding(), end = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.live_tv_nothing_on),
            style = if (LocalLiquidGlass.current) GlassType.sectionTitle else MaterialTheme.typography.headlineSmall,
            color = copyColor(1f),
        )
        Text(
            text = stringResource(R.string.live_tv_nothing_on_message),
            style = MaterialTheme.typography.bodyLarge,
            color = copyColor(0.72f),
        )
        if (channels.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelRailCard(
                        channel = channel,
                        current = null,
                        now = LocalDateTime.now(),
                        onClick = { onTune(channel) },
                        onLongClick = {},
                        modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                    )
                }
            }
            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
        }
    }
}

@Composable
private fun soonLabel(
    start: LocalDateTime,
    now: LocalDateTime,
): String {
    val minutes = Duration.between(now, start).toMinutes().coerceAtLeast(0L)
    return when {
        start.toLocalDate() != now.toLocalDate() -> start.format(DateTimeFormatter.ofPattern("EEE h:mm a", Locale.getDefault()))
        minutes < 60 -> stringResource(R.string.live_tv_in_min, minutes)
        else -> start.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }
}
