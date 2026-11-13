package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.TonightData
import com.github.damontecres.wholphin.services.TonightPick
import com.github.damontecres.wholphin.services.TonightPlan
import com.github.damontecres.wholphin.services.TonightReason
import com.github.damontecres.wholphin.services.TonightShelf
import com.github.damontecres.wholphin.services.TonightShelfKind
import com.github.damontecres.wholphin.services.TonightService
import com.github.damontecres.wholphin.services.TonightWindow
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.theme.GlassPanel
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/** A refusal next to a title that already fits the evening: the warmest possible "not now". */
private val LateAmber = Color(0xFFE8A33D)

private const val TAB_TONIGHT = 0
private const val TAB_ON_AIR = 1

/**
 * The page's loaded state. The plan is rebuilt locally on every refusal, so a reroll is instant.
 */
@HiltViewModel
class TonightViewModel
    @Inject
    constructor(
        private val service: TonightService,
        private val serverRepository: ServerRepository,
        private val backdropService: BackdropService,
        val navigationManager: NavigationManager,
        @param:IoCoroutineScope private val ioScope: CoroutineScope,
    ) : ViewModel() {
        sealed interface State {
            data object Loading : State

            data class Error(
                val exception: Throwable? = null,
            ) : State

            data class Ready(
                val plan: TonightPlan,
            ) : State
        }

        private val _state = MutableStateFlow<State>(State.Loading)
        val state = _state.asStateFlow()

        private var data: TonightData? = null
        private var dismissed: Set<UUID> = emptySet()
        private var loadedFor: UUID? = null
        private var job: Job? = null

        fun refresh(bedtimeMinutes: Int) {
            job?.cancel()
            job =
                viewModelScope.launch {
                    // Keep a plan on screen while the same profile refreshes: a page that blinks
                    // to a spinner every time the viewer comes back reads as a slow server.
                    val keep = _state.value is State.Ready
                    try {
                        val userId =
                            withTimeoutOrNull(USER_TIMEOUT_MS) {
                                serverRepository.currentUserFlow.filterNotNull().first()
                            }?.id ?: return@launch
                        if (loadedFor != null && loadedFor != userId) {
                            data = null
                            dismissed = emptySet()
                        }
                        val loaded =
                            withContext(WholphinDispatchers.IO) {
                                service.load(userId, bedtimeMinutes)
                            }
                        val refused =
                            withContext(WholphinDispatchers.IO) {
                                service.dismissedIds(userId)
                            }
                        data = loaded
                        dismissed = refused
                        loadedFor = userId
                        val plan = service.buildPlan(loaded, refused)
                        _state.value = State.Ready(plan)
                        plan.pick?.let { submitBackdrop(it.item) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // Only the class: an SDK exception can carry a request URL.
                        Timber.w("Tonight could not load: %s", ex.javaClass.simpleName)
                        if (!keep) _state.value = State.Error(ex)
                    }
                }
        }

        /** Refuse the current pick: the next one is chosen and stored without another request. */
        fun notTonight() {
            val current = data ?: return
            val pick = (_state.value as? State.Ready)?.plan?.pick?.item ?: return
            dismissed = dismissed + pick.id
            val plan = service.buildPlan(current, dismissed)
            _state.value = State.Ready(plan)
            plan.pick?.let { submitBackdrop(it.item) }
            val userId = loadedFor ?: return
            // The page can be left immediately after a refusal; the write must not be cancelled
            // by the ViewModel that made it.
            ioScope.launch {
                runCatching { service.dismiss(userId, pick.id) }
                    .onFailure { Timber.w("Tonight could not save the refusal: %s", it.javaClass.simpleName) }
            }
        }

        fun submitBackdrop(item: BaseItem) {
            viewModelScope.launchIO { backdropService.submit(item) }
        }

        companion object {
            private const val USER_TIMEOUT_MS = 8_000L
        }
    }

/**
 * "Can I finish this tonight?"
 *
 * The rest of the app recommends from taste. This page adds the second half of the question — the
 * clock — and answers it with one title, a finish time, and a rule that runs from now to bedtime.
 * Everything below the pick exists to make the rest of the evening legible: what fits, what is
 * worth a rewatch, and what is one lane over from the usual.
 */
@Composable
fun TonightPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: TonightViewModel = hiltViewModel(),
) {
    // The stored zero is "never set", not midnight: the preference was added after the first
    // stores were written, and a page that planned every evening for 00:00 would be nonsense.
    val bedtimeMinutes =
        preferences.appPreferences.interfacePreferences.bedtimeMinutes
            .takeIf { it > 0 } ?: AppPreference.DEFAULT_BEDTIME_MINUTES
    LifecycleStartEffect(bedtimeMinutes) {
        viewModel.refresh(bedtimeMinutes)
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Both tabs are the same evening question. On air is reachable from here rather than from
    // the bar, which is already full of libraries, and stays one press from the page a viewer
    // opens when they do not know what to watch.
    var tab by rememberSaveable { mutableStateOf(TAB_TONIGHT) }

    when (val current = state) {
        is TonightViewModel.State.Error -> {
            ErrorMessage(current.exception?.localizedMessage, current.exception, modifier)
        }

        TonightViewModel.State.Loading -> {
            LoadingPage(modifier)
        }

        is TonightViewModel.State.Ready -> {
            TonightContent(
                plan = current.plan,
                tab = tab,
                onTabChange = { tab = it },
                onAir = { OnAirPage(Modifier.fillMaxSize()) },
                onPlay = { item -> viewModel.navigationManager.navigateTo(Destination.Playback(item)) },
                onDetails = { item -> viewModel.navigationManager.navigateTo(item.destination()) },
                onNotTonight = viewModel::notTonight,
                onFocusItem = viewModel::submitBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TonightContent(
    plan: TonightPlan,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onAir: @Composable () -> Unit,
    onPlay: (BaseItem) -> Unit,
    onDetails: (BaseItem) -> Unit,
    onNotTonight: () -> Unit,
    onFocusItem: (BaseItem) -> Unit,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val playFocus = remember { FocusRequester() }
    // The page exists to start something; landing focus on the play pill is what makes the
    // promise true one press in.
    LaunchedEffect(plan.pick?.item?.id, tab) {
        if (tab == TAB_TONIGHT && plan.pick != null) {
            playFocus.tryRequestFocus()
            // Focus lands on the action, and the bring-into-view that follows scrolls the pick
            // card up under the heading. The card is the first thing in the list, so resetting
            // the list after the focus scroll has resolved is what keeps the masthead whole.
            withFrameNanos { }
            withFrameNanos { }
            listState.scrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // The heading lives outside the list. Compose scrolls a focused control into view rather
        // than scrolling to its item, so a heading inside the list was left above the viewport
        // the first time focus landed on the play pill — the page opened without its own title.
        TonightHeader(plan.window, Modifier.padding(bottom = 14.dp))
        val tabs =
            remember {
                listOf(
                    TabDetails(R.string.tonight),
                    TabDetails(R.string.on_air),
                )
            }
        TabRow(
            selectedTabIndex = tab,
            tabs = tabs,
            onClick = onTabChange,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        if (tab == TAB_ON_AIR) {
            Box(Modifier.weight(1f).fillMaxWidth()) { onAir() }
            return@Column
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = Cards.height2x3),
            modifier = Modifier.fillMaxSize().focusRestorer(),
        ) {
            plan.pick?.let { pick ->
                item(key = "pick_${pick.item.id}") {
                    TonightPickCard(
                        pick = pick,
                        window = plan.window,
                        showLogo = showLogo,
                        onPlay = { onPlay(pick.item) },
                        onDetails = { onDetails(pick.item) },
                        onNotTonight = onNotTonight,
                        playFocus = playFocus,
                        onFocusItem = onFocusItem,
                    )
                }
            }
            items(plan.shelves, key = { it.kind.name }) { shelf ->
                TonightShelfRow(
                    shelf = shelf,
                    window = plan.window,
                    onClickItem = onDetails,
                    onPlayItem = onPlay,
                    onFocusItem = onFocusItem,
                )
            }
            if (plan.isEmpty) {
                item(key = "empty") {
                    EmptyPlan()
                }
            }
        }
    }
}

@Composable
private fun TonightHeader(
    window: TonightWindow,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = context.resources
    // The clock ticks every couple of seconds through the provider, so the heading keeps up with
    // the evening without the page re-planning itself.
    val now by LocalClock.current.now
    val day = remember(now) { now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    val time = formatTime(context, now)
    val windowLine =
        if (window.late) {
            "${stringResource(R.string.tonight_late_night)} · ${resources.formatDuration((window.minutes.minutes))}"
        } else {
            stringResource(
                R.string.tonight_until_bedtime,
                resources.formatDuration(window.minutes.minutes),
            )
        }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        Text(
            text = stringResource(R.string.tonight),
            style = GlassType.pageTitle,
            color = Color.White,
        )
        Text(
            text = listOf(day, time, windowLine).joinToString(" · "),
            style = GlassType.meta.copy(fontWeight = FontWeight.Normal),
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun TonightPickCard(
    pick: TonightPick,
    window: TonightWindow,
    showLogo: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onNotTonight: () -> Unit,
    playFocus: FocusRequester,
    onFocusItem: (BaseItem) -> Unit,
) {
    val context = LocalContext.current
    val resources = context.resources
    val item = pick.item
    val logoUrl = rememberLogoUrl(item)
    val isEpisode = item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE
    val title = if (isEpisode) item.data.seriesName ?: item.name else item.name
    val episodeName = if (isEpisode) item.name else null
    val resumable = pick.reason is TonightReason.Continue

    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth(PICK_WIDTH)
                .onFocusChanged { if (it.hasFocus) onFocusItem(item) },
        shape = RoundedCornerShape(GlassTokens.panelRadius),
        chromaticAberration = false,
        tint = GlassTokens.heroTint,
        scrim = 1f,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp),
        ) {
            TitleOrLogo(
                title = title,
                logoImageUrl = logoUrl,
                showLogo = showLogo,
                modifier = Modifier.fillMaxWidth(0.78f),
                logoHeight = 42.dp,
            )
            if (episodeName != null) {
                Text(
                    text = episodeName,
                    style = GlassType.meta,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = reasonText(pick.reason),
                style = GlassType.subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            QuickDetails(
                details = item.ui.quickDetails,
                timeRemaining = item.timeRemainingOrRuntime,
                // The finish time belongs to the deadline rule below, which is this page's own
                // arithmetic. Letting the details line derive one as well printed two "Ends at"
                // lines that disagreed by a minute.
                endsAt = null,
                showEndsAt = false,
                textStyle = GlassType.meta.copy(color = Color.White.copy(alpha = 0.82f)),
                oneLine = true,
            )
            DeadlineRule(pick = pick, window = window)
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(
                    text =
                        stringResource(
                            if (resumable) R.string.liquid_glass_resume else R.string.liquid_glass_play,
                        ),
                    icon = Icons.Default.PlayArrow,
                    primary = true,
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playFocus),
                )
                PillButton(
                    text = stringResource(R.string.liquid_glass_details),
                    icon = Icons.Default.Info,
                    onClick = onDetails,
                )
                PillButton(
                    text = stringResource(R.string.tonight_not_tonight),
                    icon = Icons.Default.Refresh,
                    onClick = onNotTonight,
                )
            }
        }
    }
}

/**
 * The evening drawn as a rule: how much of the time before bed this title would take.
 *
 * The rule is the page's argument made visible — a two-hour film at 10:40 PM is not a
 * recommendation, and the bar says so before the viewer has to read a runtime.
 */
@Composable
private fun DeadlineRule(
    pick: TonightPick,
    window: TonightWindow,
) {
    val context = LocalContext.current
    val resources = context.resources
    val end = window.now.plusMinutes(pick.minutes)
    val fraction = (pick.minutes.toFloat() / window.minutes.toFloat()).coerceIn(0f, 1f)
    val overflow = (pick.minutes - window.minutes).coerceAtLeast(0L)
    val spare = (window.minutes - pick.minutes).coerceAtLeast(0L)
    val late = window.late || !pick.fits
    val barColor = if (late) LateAmber else Color.White.copy(alpha = 0.92f)
    val caption =
        when {
            window.late ->
                stringResource(
                    R.string.tonight_late_window,
                    formatTime(context, end),
                )

            pick.fits ->
                stringResource(
                    R.string.tonight_ends_at,
                    formatTime(context, end),
                ) + " · " +
                    if (spare >= PLENTY_OF_TIME_MINUTES) {
                        stringResource(R.string.tonight_plenty_of_time)
                    } else {
                        stringResource(
                            R.string.tonight_to_spare,
                            resources.formatDuration(spare.minutes),
                        )
                    }

            else ->
                stringResource(
                    R.string.tonight_ends_at,
                    formatTime(context, end),
                ) + " · " +
                    stringResource(
                        R.string.tonight_past_bedtime,
                        resources.formatDuration(overflow.minutes),
                    )
        }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(barColor),
            )
        }
        Text(
            text = caption,
            style = GlassType.settingsSummary,
            color = if (late && !window.late) LateAmber else Color.White.copy(alpha = 0.76f),
        )
    }
}

/**
 * A small action pill. The primary is the lit white of the hero; every other action is the same
 * smoked capsule the detail pages use.
 *
 * Not tonight used to pass `glass = true` alone, which is the clear-glass variant: on the pick
 * card's artwork the tint was invisible, so the row read as two capsules and a line of stranded
 * text — a disabled control rather than the second way out of the card.
 */
@Composable
private fun PillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        glassProminent = primary,
        glassFilled = !primary,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(GlassTokens.pillRadius)),
        contentHeight = 40.dp,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(text = text, style = GlassType.button)
    }
}

@Composable
private fun TonightShelfRow(
    shelf: TonightShelf,
    window: TonightWindow,
    onClickItem: (BaseItem) -> Unit,
    onPlayItem: (BaseItem) -> Unit,
    onFocusItem: (BaseItem) -> Unit,
) {
    val (title, subtitle) = shelfCopy(shelf, window)
    ItemRow(
        title = title,
        items = shelf.items,
        onClickItem = { _, item -> onClickItem(item) },
        // A dead long-press on a television remote reads as a broken row; the detail page is the
        // only action these cards have, so both gestures take the same path.
        onLongClickItem = { _, item -> onClickItem(item) },
        horizontalPadding = 0.dp,
        showViewMore = false,
        subtitle = {
            Text(
                text = subtitle,
                style = GlassType.settingsSummary,
                color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp),
            )
        },
        cardContent = { index, item, cardModifier, onClick, onLongClick ->
            if (item != null) {
                val onKey =
                    remember(item) {
                        { event: androidx.compose.ui.input.key.KeyEvent ->
                            if (isPlayKeyUp(event) && item.playable) {
                                onPlayItem(item)
                                true
                            } else {
                                false
                            }
                        }
                    }
                HomePageCardContent(
                    index = index,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    viewOptions = HomeRowViewOptions(),
                    modifier =
                        cardModifier
                            .onFocusChanged { if (it.isFocused) onFocusItem(item) }
                            .onKeyEvent { onKey(it) },
                )
            }
        },
        modifier = Modifier.fillMaxWidth().focusGroup(),
    )
}

@Composable
private fun shelfCopy(
    shelf: TonightShelf,
    window: TonightWindow,
): Pair<String, String> {
    val context = LocalContext.current
    return when (shelf.kind) {
        TonightShelfKind.FITS_TONIGHT ->
            stringResource(R.string.tonight_shelf_fits) to
                if (window.late) {
                    stringResource(R.string.tonight_shelf_fits_late)
                } else {
                    stringResource(
                        R.string.tonight_shelf_fits_subtitle,
                        formatTime(context, window.bedtime),
                    )
                }

        TonightShelfKind.FINISH_LINE ->
            stringResource(R.string.tonight_shelf_finish_line) to
                stringResource(R.string.tonight_shelf_finish_line_subtitle)

        TonightShelfKind.HIDDEN_GEMS ->
            stringResource(R.string.tonight_shelf_gems) to
                stringResource(R.string.tonight_shelf_gems_subtitle)

        TonightShelfKind.BECAUSE_YOU_WATCHED ->
            stringResource(R.string.tonight_shelf_because, shelf.seedTitle ?: "") to
                stringResource(R.string.tonight_shelf_because_subtitle)

        TonightShelfKind.DIFFERENT_LANE ->
            stringResource(R.string.tonight_shelf_lane) to
                stringResource(R.string.tonight_shelf_lane_subtitle)

        TonightShelfKind.COMFORT_REWATCH ->
            stringResource(R.string.tonight_shelf_comfort) to
                stringResource(R.string.tonight_shelf_comfort_subtitle)
    }
}

@Composable
private fun reasonText(reason: TonightReason): String =
    when (reason) {
        // Continue and NextEpisode name the show in the type, but the card already carries that
        // title above: the line states the reason instead of echoing the logo.
        is TonightReason.Continue -> stringResource(R.string.tonight_reason_continue)
        is TonightReason.NextEpisode -> stringResource(R.string.tonight_reason_next_episode)
        is TonightReason.MatchesTaste -> stringResource(R.string.tonight_reason_taste, reason.genre)
        TonightReason.TopPick -> stringResource(R.string.tonight_reason_top_pick)
    }

@Composable
private fun EmptyPlan() {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(0.58f),
        shape = RoundedCornerShape(GlassTokens.panelRadius),
        chromaticAberration = false,
        scrim = 1f,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
        ) {
            Text(
                text = stringResource(R.string.tonight_empty_title),
                style = GlassType.sectionTitle,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.tonight_empty_body),
                style = GlassType.settingsSummary,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

/** Past this, a countdown of spare minutes is noise: the evening is simply free. */
private const val PLENTY_OF_TIME_MINUTES = 120L

private const val PICK_WIDTH = 0.76f
