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
import androidx.compose.runtime.collectAsState
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
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * The Sports surface inside Live TV.
 *
 * A sports guide needs no sports service of its own: Jellyfin already carries the fixtures as
 * programmes with `IsSports` (an XMLTV `<category>sports</category>` becomes it after a guide
 * refresh), and the channel that carries each one. What the viewer wants is the summary the grid
 * cannot give — what is on now, what is next, and what day it is on — laid out as a page rather
 * than as a 48-hour spreadsheet.
 *
 * It reads the guide data the Live TV page has already loaded, so opening the tab costs nothing
 * and there is one story about what is live. A card opens `Destination.Playback(channelId)`, the
 * same path a channel takes from the guide, so there is one player and one resume behaviour.
 *
 * Before the guide has been refreshed the channel list is real and the programmes are not; the
 * tab says so and shows the channels, because that is the part that works.
 */
@Composable
fun LiveTvSportsTab(
    onClickProgram: (TvProgram) -> Unit,
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
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.live_tv_channels_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = copyColor(0.75f),
                )
            }
        }

        LoadingState.Success -> {
            val now = rememberTickingNow()
            val sports =
                remember(state.programs.programs) {
                    state.programs.programs
                        .filter { it.category == ProgramCategory.SPORTS && !it.isFake }
                        .sortedBy { it.start }
                }
            if (sports.isEmpty()) {
                SportsWithoutListings(
                    channels = state.channels,
                    // Two different answers: a guide with no listings at all is a tuner that has
                    // not been refreshed, while a guide full of soaps and news is a tuner that
                    // simply does not carry sport. Sending the second viewer to refresh the
                    // guide is advice that cannot work.
                    hasAnyListings = state.programs.programs.any { !it.isFake },
                    onClickChannel = onClickChannel,
                    modifier = modifier,
                    focusRequester = focusRequester,
                )
            } else {
                val onNow = remember(sports, now) { sports.filter { it.start <= now && now.isBefore(it.end) } }
                val later = remember(sports, now) { sports.filter { it.start.isAfter(now) } }
                val channelsById = remember(state.channels) { state.channels.associateBy { it.id } }
                val featured = onNow.firstOrNull() ?: later.firstOrNull()
                val featureIsLive = featured != null && onNow.any { it.id == featured.id }
                // The feature card already *is* the first live game, so the shelf underneath
                // carries the rest. Repeating the same fixture one tile lower reads as a page
                // that does not know what it has already shown.
                val otherLive = remember(onNow, featured) { onNow.filterNot { it.id == featured?.id } }
                val upcoming = remember(later, featured) { later.filterNot { it.id == featured?.id } }
                // One row per day rather than one long shelf: a week of fixtures is a calendar,
                // and "Sunday" is the bit a viewer scans for. Days are the grouping the guide
                // grid already uses, so the two views agree about when a game is on.
                val days =
                    remember(upcoming) {
                        upcoming
                            .groupBy { it.start.toLocalDate() }
                            .toSortedMap()
                            .map { (day, games) -> day to games }
                    }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = modifier,
                ) {
                    item(key = "header") {
                        SportsHeader(
                            liveCount = onNow.size,
                            upcomingCount = later.size,
                        )
                    }
                    if (featured != null) {
                        item(key = "feature") {
                            SportsFeature(
                                program = featured,
                                channel = channelsById[featured.channelId],
                                now = now,
                                onClick = { onClickProgram(featured) },
                                focusRequester = focusRequester,
                            )
                        }
                    }
                    if (otherLive.isNotEmpty()) {
                        item(key = "on_now") {
                            SportsRow(
                                title =
                                    stringResource(
                                        if (featureIsLive) R.string.live_tv_sports_also_live else R.string.live_tv_sports_on_now,
                                    ),
                                programs = otherLive,
                                channelsById = channelsById,
                                onClick = onClickProgram,
                                now = now,
                                live = true,
                            )
                        }
                    }
                    days.forEach { (day, games) ->
                        item(key = "day_$day") {
                            SportsRow(
                                title = dayLabel(day, now.toLocalDate()),
                                programs = games,
                                channelsById = channelsById,
                                onClick = onClickProgram,
                                now = now,
                                live = false,
                            )
                        }
                    }
                    if (onNow.isEmpty() && later.isEmpty()) {
                        item(key = "later") {
                            SportsLater(
                                firstStart = sports.firstOrNull { it.start.isAfter(now) }?.start,
                            )
                        }
                    }
                }
                if (focusRequester != null) {
                    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                }
            }
        }
    }
}

/**
 * The page's own heading, and the count that makes it worth a glance.
 *
 * The tab strip above says "Sports"; this says how much sport there is, which is the question a
 * viewer opens the tab with. "3 live · 14 coming up" is one line and saves scanning every shelf.
 */
@Composable
private fun SportsHeader(
    liveCount: Int,
    upcomingCount: Int,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = pageStartPadding()),
    ) {
        Text(
            text = stringResource(R.string.sports),
            style = if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
            color = copyColor(1f),
        )
        if (liveCount > 0 || upcomingCount > 0) {
            Text(
                text =
                    if (liveCount > 0) {
                        stringResource(R.string.live_tv_sports_summary_live, liveCount, upcomingCount)
                    } else {
                        stringResource(R.string.live_tv_sports_summary_next, upcomingCount)
                    },
                style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodyMedium,
                color = copyColor(0.72f),
            )
        }
    }
}

/**
 * The one game worth the biggest tile: live now if anything is, otherwise the next kickoff.
 *
 * A page of equal-sized cards makes the viewer read the whole page to find the thing that is
 * happening, and on a sports page that is always the same question. The feature card answers it
 * before any shelf does, and carries the two facts a sofa needs — whether it is live, and when
 * coverage starts.
 */
@Composable
private fun SportsFeature(
    program: TvProgram,
    channel: TvChannel?,
    now: LocalDateTime,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    val context = LocalContext.current
    val live = program.start <= now && now.isBefore(program.end)
    val glass = LocalLiquidGlass.current
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .padding(horizontal = pageStartPadding())
                .fillMaxWidth()
                .height(FEATURE_HEIGHT)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        onLongClick = {},
        shape = glassCardShape(),
        border = glassCardBorder(),
        glow = glassCardGlow(),
        scale = glassCardScale(),
        colors = CardDefaults.colors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (glass) 0.28f else 0.55f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f),
            ) {
                if (program.imageUrl != null) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(context)
                                .data(program.imageUrl)
                                .crossfade(true)
                                .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = program.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(20.dp),
                    )
                }
                // The art is two team logos on a bright field, so the copy gets its own dark
                // wedge rather than trusting the picture to be quiet where the type lands.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = if (glass) 0.30f else 0.55f),
                                    ),
                                ),
                            ),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (live) {
                        LiveBadge()
                    } else {
                        Text(
                            text =
                                if (now.toLocalDate() == program.start.toLocalDate()) {
                                    stringResource(R.string.live_tv_sports_next_up)
                                } else {
                                    dayLabel(program.start.toLocalDate(), now.toLocalDate())
                                },
                            style = if (glass) GlassType.sectionLabel else MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Text(
                    text = program.name.orEmpty(),
                    style = if (glass) GlassType.sectionTitle else MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = program.quickDetails,
                    style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // A watch affordance, not a second control: the whole card is the target, so
                    // this is drawn as part of the billboard rather than as a focusable pill
                    // inside one (two stops for one action).
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Color.White)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.live_tv_sports_watch),
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
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
            }
        }
    }
}

/**
 * Sports programmes exist, none of them is on yet, and the guide has nothing later inside the
 * window. Saying when coverage starts is the whole point: an empty tab reads as a broken page,
 * and a game that starts at 8:15 is an answer.
 */
@Composable
private fun SportsLater(firstStart: LocalDateTime?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = pageStartPadding(), end = pageStartPadding()),
    ) {
        Text(
            text = stringResource(R.string.live_tv_sports_no_games_now),
            style = if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
            color = copyColor(1f),
        )
        firstStart?.let {
            Text(
                text = stringResource(R.string.live_tv_sports_coverage_starts, formatTime(it)),
                style = MaterialTheme.typography.titleMedium,
                color = copyColor(0.82f),
            )
        }
    }
}

/**
 * The tab before there is anything to schedule.
 *
 * The channel grid is not a consolation prize: on a tuner like Jellysports every channel is a
 * fixture, and pressing one is exactly what a sports surface would do anyway. The copy names the
 * guide as the missing piece so a viewer knows the tuner is working — the failure they would
 * otherwise diagnose is their own setup.
 */
@Composable
private fun SportsWithoutListings(
    channels: List<TvChannel>,
    hasAnyListings: Boolean,
    onClickChannel: (TvChannel) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(
                start = pageStartPadding(),
                end = pageStartPadding(),
                top = 4.dp,
            ),
        ) {
            Text(
                text = stringResource(if (hasAnyListings) R.string.live_tv_sports_none_title else R.string.live_tv_sports_empty_title),
                style = if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                color = copyColor(1f),
            )
            Text(
                text =
                    stringResource(
                        if (hasAnyListings) R.string.live_tv_sports_none_message else R.string.live_tv_sports_empty_message,
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = copyColor(0.75f),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
        }
        if (channels.isNotEmpty()) {
            TvChannelGrid(
                channels = channels,
                onClickChannel = onClickChannel,
                modifier = Modifier.fillMaxSize(),
                focusRequester = focusRequester,
                contentPadding = PaddingValues(top = 10.dp),
            )
        }
    }
}

/**
 * One shelf of games.
 *
 * The focused card is pulled into view by Compose, which is why the heading stays outside the
 * LazyRow: a heading that scrolls away is a shelf with no name. The card is fixed height so a
 * shelf of mixed titles still has one baseline.
 */
@Composable
private fun SportsRow(
    title: String,
    programs: List<TvProgram>,
    channelsById: Map<UUID, TvChannel>,
    onClick: (TvProgram) -> Unit,
    now: LocalDateTime,
    live: Boolean,
) {
    val listState = rememberLazyListState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = if (LocalLiquidGlass.current) GlassType.rowTitle else MaterialTheme.typography.titleLarge,
            color = copyColor(1f),
            modifier = Modifier.padding(start = pageStartPadding()),
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding =
                PaddingValues(
                    start = pageStartPadding(),
                    end = 24.dp,
                    bottom = 6.dp,
                    top = 2.dp,
                ),
        ) {
            itemsIndexed(programs, key = { _, program -> program.id }) { _, program ->
                SportsProgramCard(
                    program = program,
                    channel = channelsById[program.channelId],
                    live = live || (program.start <= now && now.isBefore(program.end)),
                    onClick = { onClick(program) },
                )
            }
        }
    }
}

@Composable
private fun SportsProgramCard(
    program: TvProgram,
    channel: TvChannel?,
    live: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val glass = LocalLiquidGlass.current
    Card(
        onClick = onClick,
        modifier = modifier.width(CARD_WIDTH).aspectRatio(16f / 9f),
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
                    // A programme has no artwork of its own in an XMLTV tuner, so the card is a
                    // dark bed for type with the channel's own colour underneath when it has one.
                    .background(
                        brush =
                            Brush.linearGradient(
                                listOf(
                                    Color.Black.copy(alpha = if (glass) 0.22f else 0.55f),
                                    Color.Black.copy(alpha = if (glass) 0.46f else 0.74f),
                                ),
                            ),
                    ),
        ) {
            if (program.imageUrl != null) {
                // The logo is the only artwork a fixture has before it airs, so it is the card's
                // image rather than a faint watermark: dimming it to nothing leaves a black tile
                // with a sentence in the corner, which is what a broken card looks like.
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(context)
                            .data(program.imageUrl)
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (live) {
                LiveBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = program.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = program.quickDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // A sports tuner names a channel after the fixture ("Away at Home"), so the
                // channel line is usually the title again. Printing it twice costs a third of the
                // card for no information; only a genuinely different channel name is worth it.
                val channelName = channel?.name?.takeIf { !it.equals(program.name, ignoreCase = true) }
                channelName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The live mark, in the one red the rest of the app uses.
 *
 * It is a filled dot with a word rather than a red card: a whole tile tinted red is an alarm, and
 * two of them on a shelf is a page that looks angry. The dot carries at a glance from the sofa;
 * the word carries when the dot is too small.
 */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(LiveRed, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.sports_live),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

/**
 * "Today", "Tomorrow", then the date.
 *
 * A shelf headed "Sunday 15 November" is correct and forgettable; the two days a viewer actually
 * thinks in are Today and Tomorrow, and the date only matters after that. Uses the platform's own
 * weekday/date formatting so a non-English locale gets its own words.
 */
@Composable
private fun dayLabel(
    day: LocalDate,
    today: LocalDate,
): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, day)
    return when (days) {
        0L -> stringResource(R.string.live_tv_sports_today)
        1L -> stringResource(R.string.live_tv_sports_tomorrow)
        else ->
            remember(day) {
                day.format(
                    DateTimeFormatter.ofPattern(
                        android.text.format.DateFormat.getBestDateTimePattern(
                            Locale.getDefault(),
                            "EEEEdMMM",
                        ),
                    ),
                )
            }
    }
}

/** A clock that ticks so "On now" ages correctly while the page stays open. */
@Composable
internal fun rememberTickingNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK)
            now = LocalDateTime.now()
        }
    }
    return now
}

private fun formatTime(time: LocalDateTime): String =
    android.text.format.DateUtils.formatDateTime(
        com.github.damontecres.wholphin.WholphinApplication.instance,
        time.atZone(java.time.ZoneId.systemDefault()).toInstant().epochSecond * 1000,
        android.text.format.DateUtils.FORMAT_SHOW_TIME,
    )

private val CARD_WIDTH = 300.dp
private val FEATURE_HEIGHT = 176.dp
private val TICK = 30.seconds
