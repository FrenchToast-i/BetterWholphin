package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.OnAirPlanner
import com.github.damontecres.wholphin.services.OnAirProgram
import com.github.damontecres.wholphin.services.OnAirService
import com.github.damontecres.wholphin.services.OnAirService.ChannelData
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.livetv.LiveRed
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.theme.GlassPanel
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

/**
 * The channel page's state. Channels load independently, so a library without a particular genre
 * simply has fewer rows rather than an error page.
 */
@HiltViewModel
class OnAirViewModel
    @Inject
    constructor(
        private val service: OnAirService,
        private val serverRepository: ServerRepository,
        private val backdropService: BackdropService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        sealed interface State {
            data object Loading : State

            data class Error(
                val exception: Throwable? = null,
            ) : State

            data class Ready(
                val channels: List<ChannelData>,
            ) : State
        }

        private val _state = MutableStateFlow<State>(State.Loading)
        val state = _state.asStateFlow()

        private var job: Job? = null
        private var loadedAt = 0L
        private var loadedFor: UUID? = null

        fun refresh(force: Boolean = false) {
            // Re-entering the page should not re-run five queries when the loop is still fresh.
            // The playlist is week-seeded, so the only thing that goes stale is the items' own
            // metadata, and fifteen minutes is well inside one evening.
            if (!force &&
                _state.value is State.Ready &&
                System.currentTimeMillis() - loadedAt < CACHE_MS
            ) {
                return
            }
            job?.cancel()
            job =
                viewModelScope.launch {
                    val keep = _state.value is State.Ready
                    try {
                        val userId =
                            withTimeoutOrNull(USER_TIMEOUT_MS) {
                                serverRepository.currentUserFlow.filterNotNull().first()
                            }?.id ?: return@launch
                        if (loadedFor != null && loadedFor != userId) {
                            loadedAt = 0L
                        }
                        val channels =
                            withContext(WholphinDispatchers.IO) {
                                service.load(userId)
                            }
                        loadedFor = userId
                        loadedAt = System.currentTimeMillis()
                        _state.value = State.Ready(channels)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // Only the class: an SDK exception can carry a request URL.
                        Timber.w("On Air could not load: %s", ex.javaClass.simpleName)
                        if (!keep) _state.value = State.Error(ex)
                    }
                }
        }

        fun submitBackdrop(item: BaseItem) {
            viewModelScope.launch {
                runCatching { backdropService.submit(item) }
                    .onFailure { Timber.w("On Air could not submit backdrop") }
            }
        }

        /** Join [channel] where it currently is, like tuning a television to a broadcast. */
        fun tuneIn(
            channel: ChannelData,
            nowMs: Long,
        ) {
            val program = channel.tuneIn(nowMs) ?: return
            val item = channel.itemFor(program.entry.id)?.item ?: return
            navigationManager.navigateTo(
                Destination.Playback(
                    itemId = item.id,
                    positionMs = max(0L, nowMs - program.startMs),
                ),
            )
        }

        fun openDetails(channel: ChannelData, program: OnAirProgram) {
            channel.itemFor(program.entry.id)?.item?.let { item ->
                navigationManager.navigateTo(item.destination())
            }
        }

        companion object {
            private const val USER_TIMEOUT_MS = 8_000L
            private const val CACHE_MS = 15 * 60_000L
        }
    }

/**
 * The lean-back half of Tonight: the library as always-on channels.
 *
 * Nothing here is a decision. A channel is already running, the page shows where it is, and one
 * press joins it — the television equivalent of finding a film halfway through on a Sunday
 * afternoon. The page is a tab rather than a destination so it needs no new bar slot and stays
 * one press from the page a viewer already opens to decide what to watch.
 */
@Composable
fun OnAirPage(
    modifier: Modifier = Modifier,
    viewModel: OnAirViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(Unit) {
        viewModel.refresh()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        OnAirViewModel.State.Loading -> LoadingPage(modifier)

        is OnAirViewModel.State.Error ->
            OnAirMessage(
                title = stringResource(R.string.on_air_error_title),
                body = stringResource(R.string.on_air_error_body),
                action = stringResource(R.string.retry),
                onAction = { viewModel.refresh(force = true) },
                modifier = modifier,
            )

        is OnAirViewModel.State.Ready ->
            if (current.channels.isEmpty()) {
                OnAirMessage(
                    title = stringResource(R.string.on_air_empty_title),
                    body = stringResource(R.string.on_air_empty_body),
                    action = stringResource(R.string.retry),
                    onAction = { viewModel.refresh(force = true) },
                    modifier = modifier,
                )
            } else {
                OnAirReady(
                    channels = current.channels,
                    viewModel = viewModel,
                    modifier = modifier,
                )
            }
    }
}

@Composable
private fun OnAirReady(
    channels: List<ChannelData>,
    viewModel: OnAirViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    // The page owns the clock: a channel's schedule is pure arithmetic, so a tick is cheaper than
    // another round trip and the progress rule moves while the viewer watches it.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            nowMs = System.currentTimeMillis()
        }
    }

    val index = selectedIndex.coerceIn(0, channels.lastIndex)
    val channel = channels[index]
    val nowItem = remember(channel, nowMs) { channel.now(nowMs) }?.let { channel.itemFor(it.entry.id) }
    LaunchedEffect(nowItem?.item?.id) {
        nowItem?.item?.let(viewModel::submitBackdrop)
    }

    OnAirContent(
        channels = channels,
        selectedIndex = index,
        nowMs = nowMs,
        onSelect = { selectedIndex = it },
        onTune = { viewModel.tuneIn(it, nowMs) },
        onDetails = { data, program -> viewModel.openDetails(data, program) },
        modifier = modifier,
    )
}

/** Stateless body, so the page reads the same in a preview as it does with a ViewModel. */
@Composable
internal fun OnAirContent(
    channels: List<ChannelData>,
    selectedIndex: Int,
    nowMs: Long,
    onSelect: (Int) -> Unit,
    onTune: (ChannelData) -> Unit,
    onDetails: (ChannelData, OnAirProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = selectedIndex.coerceIn(0, channels.lastIndex)
    val channel = channels[index]
    val programs = remember(channel, nowMs) { channel.programs(nowMs, 4) }
    val current = programs.firstOrNull()
    val tune = remember(channel, nowMs) { channel.tuneIn(nowMs) }

    // One request for the rail, not one per card: a channel card's focus requester has to be
    // attached to a composition that stays put, or the page opens with the remote pointing
    // nowhere.
    val railFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        railFocus.tryRequestFocus()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier =
                Modifier
                    .width(CHANNEL_RAIL_WIDTH)
                    .fillMaxHeight()
                    .focusRestorer(),
        ) {
            itemsIndexed(channels, key = { _, c -> c.channel.key }) { i, data ->
                val program = remember(data, nowMs) { data.now(nowMs) }
                ChannelCard(
                    channel = data,
                    program = program,
                    nowMs = nowMs,
                    selected = i == index,
                    onFocus = { onSelect(i) },
                    onClick = { onTune(data) },
                    modifier =
                        Modifier.then(
                            if (i == 0) {
                                Modifier.focusRequester(railFocus)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }

        HeroPanel(
            channel = channel,
            current = current,
            tune = tune,
            programs = programs,
            nowMs = nowMs,
            onTune = { onTune(channel) },
            onDetails = { tune?.let { onDetails(channel, it) } },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelData,
    program: OnAirProgram?,
    nowMs: Long,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val item = program?.let { channel.itemFor(it.entry.id)?.item }
    val title = item?.let { nowTitle(it) } ?: stringResource(R.string.on_air_loading_program)
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = if (selected) 0.15f else 0.055f),
                contentColor = Color.White,
                focusedContainerColor = Color.White.copy(alpha = 0.22f),
                focusedContentColor = Color.White,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = channelBorder(if (selected) 0.32f else 0.08f),
                focusedBorder = channelBorder(0.40f),
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same red as the guide's live badge: a channel is always live, and the dot
                // is what says so before the viewer reads a word.
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LiveRed.copy(alpha = if (selected) 1f else 0.65f)),
                )
                Text(
                    text = channelTitle(channel),
                    style = GlassType.settingsTitle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = GlassType.meta,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (program != null) {
                OnAirProgress(
                    progress = program.progress(nowMs),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
                Text(
                    text =
                        "${formatClock(context, program.startMs)} – ${formatClock(context, program.endMs)}",
                    style = GlassType.settingsSummary,
                    color = Color.White.copy(alpha = 0.52f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HeroPanel(
    channel: ChannelData,
    current: OnAirProgram?,
    tune: OnAirProgram?,
    programs: List<OnAirProgram>,
    nowMs: Long,
    onTune: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUrlService = LocalImageUrlService.current
    val item = current?.let { channel.itemFor(it.entry.id)?.item }
    val backdropUrl = imageUrlService.rememberImageUrl(item, org.jellyfin.sdk.model.api.ImageType.BACKDROP)
    val join = tune ?: current
    val joinItem = join?.let { channel.itemFor(it.entry.id)?.item }

    GlassPanel(
        modifier = modifier,
        tint = GlassTokens.heroTint,
        scrim = 0.8f,
        chromaticAberration = false,
    ) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.62f),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = 0.20f),
                            1f to Color.Black.copy(alpha = 0.90f),
                        ),
                    ),
        )
        if (item == null || join == null || joinItem == null) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(26.dp),
            ) {
                Text(
                    text = stringResource(R.string.on_air_loading_program),
                    style = GlassType.sectionTitle,
                    color = Color.White,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 22.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(LiveRed.copy(alpha = 0.94f))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.on_air_badge).uppercase(),
                            style = GlassType.posterBadge,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = channelTitle(channel),
                        style = GlassType.chip,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
                Text(
                    text = item.title.orEmpty(),
                    style = GlassType.pageTitle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                    if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                        item.subtitleLong
                    } else {
                        item.subtitle
                    }
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = GlassType.meta,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.data.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = GlassType.settingsSummary,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                ) {
                    OnAirProgress(
                        progress = current.progress(nowMs),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            startedLabel(context, current, nowMs),
                        style = GlassType.settingsSummary,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Button(
                        onClick = onTune,
                        glassProminent = true,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(GlassTokens.pillRadius)),
                        contentHeight = 40.dp,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = stringResource(R.string.on_air_tune_in),
                            style = GlassType.button,
                        )
                    }
                    Button(
                        onClick = onDetails,
                        glassFilled = true,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(GlassTokens.pillRadius)),
                        contentHeight = 40.dp,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = stringResource(R.string.liquid_glass_details),
                            style = GlassType.button,
                        )
                    }
                }
                val upcoming = programs.drop(1).take(2)
                if (upcoming.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.padding(top = 3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.on_air_up_next).uppercase(),
                            style = GlassType.sectionLabel,
                            color = Color.White.copy(alpha = 0.50f),
                        )
                        upcoming.forEach { next ->
                            val nextItem = channel.itemFor(next.entry.id)?.item
                            Text(
                                text =
                                    listOfNotNull(
                                        formatClock(context, next.startMs),
                                        nextItem?.let { nowTitle(it) },
                                    ).joinToString(" · "),
                                style = GlassType.settingsSummary,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun channelBorder(alpha: Float) =
    androidx.tv.material3.Border(
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = alpha)),
        shape = RoundedCornerShape(18.dp),
    )

@Composable
private fun OnAirMessage(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        GlassPanel(
            modifier = Modifier.width(520.dp),
            scrim = 0.4f,
            chromaticAberration = false,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 26.dp),
            ) {
                Text(
                    text = title,
                    style = GlassType.sectionTitle,
                    color = Color.White,
                )
                Text(
                    text = body,
                    style = GlassType.settingsSummary,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    glassFilled = true,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(GlassTokens.pillRadius)),
                    contentHeight = 40.dp,
                    contentPadding = PaddingValues(horizontal = 22.dp),
                ) {
                    Text(text = action, style = GlassType.button)
                }
            }
        }
    }
}

@Composable
private fun OnAirProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    Box(
        modifier =
            modifier
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(if (glass) Color.White else MaterialTheme.colorScheme.border),
        )
    }
}

@Composable
private fun channelTitle(channel: ChannelData): String =
    stringResource(
        when (channel.channel) {
            OnAirService.Channel.MOVIES -> R.string.on_air_channel_movies
            OnAirService.Channel.BINGE -> R.string.on_air_channel_binge
            OnAirService.Channel.COMFORT -> R.string.on_air_channel_comfort
            OnAirService.Channel.ANIMATION -> R.string.on_air_channel_animation
            OnAirService.Channel.LATE_NIGHT -> R.string.on_air_channel_late
        },
    )

private fun nowTitle(item: BaseItem): String =
    when (item.type) {
        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> {
            val series = item.data.seriesName ?: item.name.orEmpty()
            val episode = item.data.seasonEpisode
            if (episode != null) "$series · $episode" else series
        }

        else -> item.name.orEmpty()
    }

private fun formatClock(
    context: android.content.Context,
    epochMs: Long,
): String =
    formatTime(
        context,
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime(),
    )

private fun startedLabel(
    context: android.content.Context,
    program: OnAirProgram,
    nowMs: Long,
): String {
    val minutes = (program.elapsedMs(nowMs) / 60_000L).toInt()
    return if (minutes <= 0) {
        context.getString(R.string.on_air_just_started)
    } else {
        context.getString(R.string.on_air_started, minutes)
    }
}

private const val TICK_MS = 20_000L

private val CHANNEL_RAIL_WIDTH = 300.dp
