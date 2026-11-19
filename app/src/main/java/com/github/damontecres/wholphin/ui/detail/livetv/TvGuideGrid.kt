package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.LiveTvPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import eu.wewox.programguide.ProgramGuide
import eu.wewox.programguide.ProgramGuideDimensions
import eu.wewox.programguide.ProgramGuideItem
import eu.wewox.programguide.rememberSaveableProgramGuideState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.abs

@Composable
fun TvGuideGrid(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
    /** The Live TV filters (favourites, sports channels) in front of the whole tuner. */
    channelFilter: (TvChannel) -> Boolean = { true },
    /** The channel to focus when the viewer asks for the guide from another surface. */
    initialChannelId: UUID? = null,
    /** The selected page tab, used for explicit Up navigation from the guide toolbar. */
    tabFocusRequester: FocusRequester,
    /** The focused programme, used by the page's own masthead. */
    onFocusedProgramChange: (TvProgram?) -> Unit = {},
    /** Hide the in-page toolbar: Live TV's masthead owns that room now. */
    showToolbar: Boolean = true,
    /** The flat broadcast layout from the reference EPG: black, dense, no slate inset. */
    dense: Boolean = false,
    /** Long press on a programme: details, recording, reminder, favourite channel. */
    onProgramActions: (TvProgram) -> Unit = {},
    /** Long press on a channel: the channel's own actions, not a second OK path. */
    onChannelActions: (TvChannel) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val preferences by viewModel.preferences.data.collectAsState(preferences.appPreferences)
    val tvPrefs = preferences.interfacePreferences.liveTvPreferences
    var showViewOptions by remember { mutableStateOf(false) }
    var focusedChannel by remember { mutableStateOf<TvChannel?>(null) }
    var hourWidth by rememberSaveable { mutableIntStateOf(if (dense) 300 else 240) }
    var jumpRequest by remember { mutableStateOf<GuideJump?>(null) }

    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Loading,
        LoadingState.Success,
        -> {
            val context = LocalContext.current
            val focusRequester = remember { FocusRequester() }
            val buttonFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            // The header describes the programme the viewer is actually on. It used to be
            // derived from a saved (row, column) pair, but a column in the guide is a global
            // programme index, not an index into that channel's own list, so lookups ran off the
            // end and the header stayed hidden. The focused cell reports itself instead.
            var focusedProgram by remember { mutableStateOf<TvProgram?>(null) }
            // A tuner usually has listings for only some of its channels, and a sports tuner is
            // the extreme case: every fixture gets a channel and a programme days early, while
            // the rest of the list has no schedule at all. Drawing all 34 channels turned twelve
            // games into thirty rows of "No data" and buried the guide. Channels with nothing to
            // say are left to the Channels tab, which is the view built for browsing them.
            val filteredChannels =
                remember(state.channels, channelFilter) {
                    state.channels.filter(channelFilter)
                }
            val guideChannels =
                remember(filteredChannels, state.programs) {
                    val listed =
                        filteredChannels.filter { channel ->
                            state.programs.programsByChannel[channel.id].orEmpty().any { !it.isFake }
                        }
                    // No listings anywhere is not an empty guide: the fallback below draws the
                    // channels themselves, and hiding them would leave a blank page.
                    listed.ifEmpty { filteredChannels }
                }
            // A tuner with no listings is not a broken guide: it is a tuner that has not been
            // refreshed yet. Forty-eight hours of "No data" per channel is worse than a sentence,
            // because it hides the channels that already play.
            val hasRealPrograms =
                remember(state.programs.programs) {
                    state.programs.programs.any { !it.isFake }
                }
            val showChannelFallback =
                guideChannels.isNotEmpty() &&
                    !hasRealPrograms &&
                    state.loading == LoadingState.Success &&
                    !state.refreshing
            if (showChannelFallback) {
                NoGuideData(
                    channels = guideChannels,
                    onClickChannel = { channel ->
                        viewModel.navigationManager.navigateTo(
                            Destination.Playback(itemId = channel.id, positionMs = 0L),
                        )
                    },
                    modifier = modifier,
                )
            } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier,
            ) {
                if (guideChannels.isEmpty()) {
                    ErrorMessage(
                        if (state.channels.isEmpty()) {
                            "Live TV is enabled, but no channels were found."
                        } else {
                            stringResource(R.string.live_tv_filter_empty)
                        },
                        null,
                    )
                } else {
                    if (showToolbar && tvPrefs.showHeader) {
                        GuideToolbar(
                            program = focusedProgram?.takeUnless { it.isFake },
                            channel = focusedChannel,
                            now = rememberTickingNow(),
                            zoomLabel = guideZoomLabel(hourWidth),
                            tabFocusRequester = tabFocusRequester,
                            onNow = { jumpRequest = GuideJump.NOW },
                            onTonight = { jumpRequest = GuideJump.TONIGHT },
                            onTomorrow = { jumpRequest = GuideJump.TOMORROW },
                            onZoomIn = { hourWidth = (hourWidth - 60).coerceAtLeast(120) },
                            onZoomOut = { hourWidth = (hourWidth + 60).coerceAtMost(360) },
                            onViewOptions = { showViewOptions = true },
                            modifier =
                                Modifier.padding(
                                    start = HeaderUtils.startPadding,
                                    end = 24.dp,
                                    bottom = 6.dp,
                                ),
                        )
                    }
                    TvGuideGridContent(
                        preferences = tvPrefs,
                        refreshing = state.refreshing,
                        channels = guideChannels,
                        programs = state.programs,
                        channelProgramCount = state.channelProgramCount,
                        guideTimes = state.guideTimes,
                        initialChannelId = initialChannelId,
                        hourWidth = hourWidth,
                        dense = dense,
                        jumpRequest = jumpRequest,
                        onJumpHandled = { jumpRequest = null },
                        onClickChannel = { _, channel ->
                            viewModel.tuneChannel(channel)
                        },
                        onLongClickChannel = onChannelActions,
                        onFocus = {
                            // The grid draws a subset but the ViewModel pages over the tuner's
                            // whole channel list, so the row is translated back before asking for
                            // more listings; otherwise deep scrolling stops fetching.
                            val originalRow =
                                guideChannels.getOrNull(it.row)
                                    ?.let { channel -> state.channels.indexOfFirst { u -> u.id == channel.id } }
                                    ?.takeIf { index -> index >= 0 } ?: it.row
                            viewModel.onFocusChannel(RowColumn(originalRow, it.column))
                        },
                        onFocusProgram = { program ->
                            focusedProgram = program
                            focusedChannel = program?.let { value ->
                                guideChannels.firstOrNull { it.id == value.channelId }
                            }
                            onFocusedProgramChange(program)
                        },
                        onFocusChannel = { channel -> focusedChannel = channel },
                        onClickProgram = { index, program ->
                            val now = LocalDateTime.now()
                            when {
                                program.isFake -> {
                                    if (now.isAfter(program.start) && now.isBefore(program.end)) {
                                        guideChannels.firstOrNull { it.id == program.channelId }
                                            ?.let(viewModel::tuneChannel)
                                    } else {
                                        Toast
                                            .makeText(
                                                context,
                                                "No guide data found!",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                }
                                // A programme that is on now is the thing the viewer pressed
                                // OK for: tune it. Making the dialog the only path meant every
                                // channel change was two presses through a sheet, which is the
                                // wrong shape on a remote and worse on a tuner where every
                                // channel is a live fixture. A future programme still opens the
                                // sheet, because its channel is showing something else now.
                                !program.start.isAfter(now) && now.isBefore(program.end) -> {
                                    viewModel.tuneChannel(
                                        guideChannels.firstOrNull { it.id == program.channelId } ?: return@TvGuideGridContent,
                                    )
                                }

                                else -> onProgramActions(program)
                            }
                        },
                        onLongClickProgram = { _, program ->
                            // Hold for the sheet: details, record, series record. The quick path
                            // is watching; the slow path is everything else.
                            onProgramActions(program)
                        },
                        focusRequester = focusRequester,
                        // The page keeps the artwork behind it in glass mode; the guide itself
                        // draws one smoked sheet in TvGuideGridContent. Painting the theme
                        // background here turned the whole page into an opaque slab.
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            }
        }
    }
    if (showViewOptions) {
        LiveTvViewOptionsDialog(
            preferences = preferences,
            onDismissRequest = { showViewOptions = false },
            onViewOptionsChange = { newPrefs ->
                scope.launchIO {
                    viewModel.preferences.updateData {
                        newPrefs
                    }
                }
            },
        )
    }
}

/**
 * The reference EPG is denser than the glass guide: 44dp channel rows and 40dp programme blocks,
 * a narrow number-plus-logo identity column, and half-hour columns wide enough for a full title.
 */
private val epgGuideDimensions =
    ProgramGuideDimensions(
        timelineHourWidth = 300.dp,
        timelineHeight = 24.dp,
        channelWidth = 166.dp,
        channelHeight = 44.dp,
        // One pixel of time, not a stripe through the programme titles.
        currentTimeWidth = 1.dp,
    )

val tvGuideDimensions =
    ProgramGuideDimensions(
        timelineHourWidth = 240.dp,
        timelineHeight = 28.dp,
        channelWidth = 120.dp,
        channelHeight = 56.dp,
        currentTimeWidth = 2.dp,
    )

/**
 * The same schedule, with a wider channel column.
 *
 * Glass mode draws the channel name at a readable two-line size, and a sports tuner names its
 * channels after the fixture — "Washington Commanders at New York Giants" does not fit 120dp at
 * any legible size. The 30dp comes out of the timeline, which is 24 hours wide and can spare it.
 */
private val glassGuideDimensions =
    ProgramGuideDimensions(
        timelineHourWidth = 240.dp,
        timelineHeight = 28.dp,
        channelWidth = 150.dp,
        channelHeight = 56.dp,
        currentTimeWidth = 2.dp,
    )

private fun guideZoomLabel(hourWidth: Int): String =
    when {
        hourWidth <= 120 -> "1x"
        hourWidth <= 180 -> "2x"
        else -> "3x"
    }

internal enum class GuideJump { NOW, TONIGHT, TOMORROW }

@Composable
internal fun TvGuideGridContent(
    preferences: LiveTvPreferences,
    refreshing: Boolean,
    channels: List<TvChannel>,
    programs: FetchedPrograms,
    channelProgramCount: Map<UUID, Int>,
    guideTimes: List<LocalDateTime>,
    onClickChannel: (Int, TvChannel) -> Unit,
    onLongClickChannel: (TvChannel) -> Unit = {},
    onClickProgram: (Int, TvProgram) -> Unit,
    onLongClickProgram: ((Int, TvProgram) -> Unit)? = null,
    onFocus: (RowColumn) -> Unit,
    onFocusProgram: (TvProgram?) -> Unit,
    onFocusChannel: (TvChannel) -> Unit = {},
    initialChannelId: UUID? = null,
    hourWidth: Int = 240,
    dense: Boolean = false,
    jumpRequest: GuideJump? = null,
    onJumpHandled: () -> Unit = {},
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = rememberSaveableProgramGuideState()
    val scope = rememberCoroutineScope()
    val guideStart = remember(guideTimes) { guideTimes.first() }
    // One tick for the whole board, not one per cell: the cells use it to mark what is on air and
    // to draw how far through it is, and a hundred independent clocks would be a hundred
    // coroutines for the same fact.
    val now = rememberTickingNow()

    var focusedChannelIndex by rememberInt(-1)
    var focusedItem by rememberPosition(RowColumn(0, 0))
    // Start on the best live thing, not slot zero. Drilling into the guide from a programme should
    // land on that channel's now playing cell; opening Guide from the tab strip should land on the
    // last channel the viewer watched if it is still present, then on a live programme. A tuner
    // that publishes one listing per channel used to open on a gap before kickoff, which made a
    // full schedule look empty.
    val initialProgramIndex =
        remember(programs.programs, channels, initialChannelId) {
            val at = LocalDateTime.now()
            val preferred = initialChannelId
            val liveForPreferred =
                preferred?.let { channelId ->
                    programs.programs.indexOfFirst { program ->
                        program.channelId == channelId &&
                            !program.isFake &&
                            !program.start.isAfter(at) &&
                            at.isBefore(program.end)
                    }
                }
            when {
                liveForPreferred != null && liveForPreferred >= 0 -> liveForPreferred
                else ->
                    programs.programs.indexOfFirst {
                        it.category == ProgramCategory.SPORTS && !it.isFake && !it.start.isAfter(at) && at.isBefore(it.end)
                    }.takeIf { it >= 0 }
                        ?: programs.programs.indexOfFirst { !it.isFake && !it.start.isAfter(at) && at.isBefore(it.end) }
                            .takeIf { it >= 0 }
                        ?: programs.programs.indexOfFirst { !it.isFake }.coerceAtLeast(0)
            }
        }
    var focusedProgramIndex by rememberInt(initialProgramIndex)
    var focusSettled by remember { mutableStateOf(false) }
    // The opening programme is scrolled into place before it is focused. Its focus event would
    // otherwise centre it again and drag the first channel row up under the sticky time ruler.
    var suppressFocusScroll by remember { mutableStateOf(true) }
    val glass = LocalLiquidGlass.current
    val dimensions =
        (when {
            dense -> epgGuideDimensions
            glass -> glassGuideDimensions
            else -> tvGuideDimensions
        }).copy(
            timelineHourWidth = hourWidth.dp,
        )
    val programFocusRequester = remember { FocusRequester() }
    LaunchedEffect(programs.programs, initialProgramIndex) {
        if (!focusSettled && programs.programs.isNotEmpty()) {
            focusedProgramIndex = initialProgramIndex.coerceIn(0, programs.programs.lastIndex)
            try {
                withContext(WholphinDispatchers.Default) {
                    // Put the opening row at the top of the list instead of centering it. On a
                    // sports tuner the first live game can be several channels down, and centering
                    // left the top channel sliced under the sticky time ruler by half a row. A
                    // complete row is easier to scan than a partial one, and the guide still
                    // opens on the live game the viewer came for.
                    state.animateToProgram(focusedProgramIndex, Alignment.TopCenter)
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Couldn't scroll to the opening programme")
            }
            withFrameNanos { }
            suppressFocusScroll = true
            programFocusRequester.tryRequestFocus()
            withFrameNanos { }
            suppressFocusScroll = false
            focusSettled = true
        }
    }
    LaunchedEffect(jumpRequest) {
        when (jumpRequest) {
            GuideJump.NOW -> {
                val channelId = programs.programs.getOrNull(focusedProgramIndex)?.channelId
                val index =
                    channelId?.let { id ->
                        programs.programs.indexOfFirst { program ->
                            program.channelId == id &&
                                !program.isFake &&
                                !program.start.isAfter(now) &&
                                now.isBefore(program.end)
                        }
                    } ?: -1
                if (index >= 0) {
                    focusedProgramIndex = index
                    state.animateToProgram(index, Alignment.Center)
                    withFrameNanos { }
                    programFocusRequester.tryRequestFocus()
                } else {
                    state.animateToCurrentTime(Alignment.CenterHorizontally)
                }
            }

            GuideJump.TONIGHT,
            GuideJump.TOMORROW,
            -> {
                val day =
                    if (jumpRequest == GuideJump.TOMORROW) {
                        LocalDateTime.now().toLocalDate().plusDays(1)
                    } else {
                        LocalDateTime.now().toLocalDate()
                    }
                val target = day.atTime(20, 0)
                val index =
                    guideTimes.indexOfFirst { !it.isBefore(target) }
                        .takeIf { it >= 0 }
                        ?: guideTimes.lastIndex.coerceAtLeast(0)
                state.animateToTimeline(index, Alignment.CenterHorizontally)
            }

            null -> Unit
        }
        if (jumpRequest != null) onJumpHandled()
    }

    LaunchedEffect(onFocus, focusedProgramIndex) {
        withContext(WholphinDispatchers.Default) {
            val program = programs.programs.getOrNull(focusedProgramIndex)
            if (program != null) {
                val channelIndex = channels.indexOfFirst { it.id == program.channelId }
                val programsBefore =
                    (0..<channelIndex)
                        .mapNotNull {
                            val channel = channels[it]
                            channelProgramCount[channel.id]
                        }.sum()
                val programIndex = focusedProgramIndex - programsBefore
                focusedItem = RowColumn(channelIndex, programIndex)
                onFocus.invoke(focusedItem)
            }
        }
    }
    Box(
        modifier = modifier,
    ) {
        // A guide is a spreadsheet of small white type, not a control that floats over artwork, so
        // it is the one surface in the app that is deliberately opaque: the slate (see
        // [GuideSlate]) keeps every programme label on quiet material and lets the live red be the
        // only colour on the board. The glass stays where it belongs — the bar, the tab strip and
        // the dialogs above it. The guide is a *child* of the slate rather than carrying it as a
        // modifier, so its scrolling canvas is clipped by the board's rounded edge.
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    when {
                        dense -> Modifier.background(GuideEpg.background)
                        glass -> Modifier.padding(12.dp).guideSlate()
                        else -> Modifier
                    },
                ),
        ) {
        ProgramGuide(
            state = state,
            dimensions = dimensions,
            modifier = Modifier.fillMaxSize(),
        ) {
            guideStartHour = 0f
            currentTime(
                layoutInfo = {
                    ProgramGuideItem.CurrentTime(
                        hoursBetween(guideStart, LocalDateTime.now()),
                    )
                },
            ) {
                // The only saturated colour on the board. A schedule without a "now" line makes
                // the viewer do the arithmetic on every row; at 25% of a theme colour it was so
                // faint it may as well not have been drawn.
                if (dense) {
                    // The now line is the one piece of the schedule that has to stay visible on
                    // every cell, including the white focused one. White at 42% disappeared into
                    // that fill; the GUIDE blue is the same accent the rest of the page uses.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(GuideEpg.accent.copy(alpha = 0.6f)),
                    )
                } else if (glass) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(LiveRed.copy(alpha = 0.9f)),
                    )
                } else {
                    Surface(
                        colors =
                            SurfaceDefaults.colors(
                                MaterialTheme.colorScheme.tertiary.copy(
                                    alpha = .25f,
                                ),
                            ),
                        modifier = Modifier,
                    ) {
                        // Empty
                    }
                }
            }
            timeline(
                count = guideTimes.size,
                layoutInfo = { index ->
                    val start = guideTimes[index]
                    val end =
                        if (index < guideTimes.lastIndex) {
                            guideTimes[index + 1]
                        } else {
                            start.plusHours(1)
                        }
                    ProgramGuideItem.Timeline(
                        startHour = hoursBetween(guideStart, start),
                        endHour = hoursBetween(guideStart, end),
                    )
                },
            ) { index ->
                Box(
                                            modifier =
                            Modifier
                                // Intentionally set background twice
                                // The second is padded so there are gaps between times
                                // The first covers those gaps
                                .background(
                                    when {
                                        dense -> GuideEpg.ruler
                                        glass -> GuideSlate.headerBand
                                        else -> MaterialTheme.colorScheme.background
                                    },
                                ).padding(horizontal = if (glass) 0.dp else 2.dp)
                                .fillMaxSize()
                                .then(
                                    when {
                                        dense ->
                                            Modifier
                                                .bottomHairline(GuideEpg.rule)
                                                .endHairline(GuideEpg.rule.copy(alpha = 0.55f))
                                        glass ->
                                            // One continuous band rather than a row of chips: a time
                                            // ruler is a ruler, and separated tiles made it look like a
                                            // row of buttons that did nothing. It is also the mask that
                                            // rows slide under, so the gaps must not be transparent.
                                            Modifier.bottomHairline()
                                        else ->
                                            Modifier.background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp),
                                            )
                                    },
                                ),
                    ) {
                        val guideTime = guideTimes[index]
                        val differentDay = guideTime.toLocalDate() != guideStart.toLocalDate()
                        val time =
                            DateUtils.formatDateTime(
                                context,
                                guideTime
                                    .toInstant(OffsetDateTime.now().offset)
                                    .epochSecond * 1000,
                                DateUtils.FORMAT_SHOW_TIME or if (differentDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0,
                            )
                        Text(
                            text = time.toString(),
                            style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                            color = if (glass) Color.White.copy(alpha = 0.80f) else MaterialTheme.colorScheme.onSurface,
                            fontSize = if (dense) 10.5.sp else TextUnit.Unspecified,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = if (dense) 3.dp else 6.dp),
                        )
                    }
            }
            programs(
                count = programs.programs.size,
                layoutInfo = { programIndex ->
                    val program = programs.programs[programIndex]
                    val channelIndex = channels.indexOfFirst { it.id == program.channelId }
                    // Using the duration for endHour accounts for daylight savings switch
                    // Eg a 1:30am-1:00am show
                    val duration = abs(program.endHours - program.startHours)
                    ProgramGuideItem.Program(
                        channelIndex,
                        program.startHours,
                        program.startHours + duration,
                    )
                },
            ) { programIndex ->
                val program = programs.programs[programIndex]
                Program(
                    guideStart = guideStart,
                    program = program,
                    now = now,
                    colorCode = preferences.colorCodePrograms,
                    onClick = {
                        onClickProgram.invoke(programIndex, program)
                    },
                    onLongClick =
                        onLongClickProgram?.let { longClick ->
                            { longClick.invoke(programIndex, program) }
                        },
                    modifier =
                        Modifier
                            .ifElse(
                                programIndex == focusedProgramIndex,
                                Modifier.focusRequester(programFocusRequester),
                            ).ifElse(
                                focusedChannelIndex < 0 && programIndex == focusedProgramIndex,
                                Modifier.focusRequester(focusRequester),
                            ).onFocusChanged {
                                if (it.isFocused) {
                                    focusedChannelIndex = -1
                                    focusedProgramIndex = programIndex
                                    // Report the cell in the terms the header understands: the
                                    // channel's row and the programme's place in that channel's
                                    // own list, not its index in the flat programme array.
                                    val channelIndex = channels.indexOfFirst { it.id == program.channelId }
                                    val column =
                                        programs.programsByChannel[program.channelId]
                                            ?.indexOfFirst { it.id == program.id }
                                            ?.takeIf { it >= 0 } ?: 0
                                    focusedItem = RowColumn(channelIndex, column)
                                    onFocus.invoke(focusedItem)
                                    onFocusProgram.invoke(program)
                                    onFocusChannel.invoke(channels.getOrNull(channelIndex) ?: return@onFocusChanged)
                                    if (!suppressFocusScroll) {
                                        scope.launch {
                                            try {
                                                state.animateToProgram(
                                                    programIndex,
                                                    Alignment.Center,
                                                )
                                            } catch (ex: Exception) {
                                                Timber.e(ex, "Couldn't scroll to $programIndex")
                                            }
                                        }
                                    }
                                }
                            },
                )
            }

            channels(
                count = channels.size,
                layoutInfo = { channelIndex ->
                    ProgramGuideItem.Channel(channelIndex)
                },
            ) { channelIndex ->
                val channel = channels[channelIndex]
                Channel(
                    channel = channel,
                    channelIndex = channelIndex,
                    onClick = { onClickChannel.invoke(channelIndex, channel) },
                    onLongClick = { onLongClickChannel.invoke(channel) },
                    modifier =
                        Modifier
                            .ifElse(
                                focusedChannelIndex == channelIndex,
                                Modifier.focusRequester(focusRequester),
                            ).onFocusChanged {
                                if (it.isFocused) {
                                    focusedChannelIndex = channelIndex
                                    scope.launch {
                                        try {
                                            state.animateToChannel(
                                                channelIndex,
                                                Alignment.CenterVertically,
                                            )
                                        } catch (ex: Exception) {
                                            Timber.e(ex, "Couldn't scroll to $channelIndex")
                                        }
                                    }
                                    focusedItem = RowColumn(channelIndex, 0)
                                    onFocus.invoke(focusedItem)
                                    onFocusProgram.invoke(
                                        programs.programsByChannel[channel.id]
                                            .orEmpty()
                                            .firstOrNull {
                                                !it.isFake && !it.start.isAfter(now) && now.isBefore(it.end)
                                            },
                                    )
                                    onFocusChannel.invoke(channel)
                                }
                            },
                )
            }

            topCorner {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            when {
                                dense ->
                                    Modifier
                                        .background(GuideEpg.ruler)
                                        .bottomHairline(GuideEpg.rule)
                                        .endHairline(GuideEpg.divider)
                                glass ->
                                    Modifier
                                        .background(GuideSlate.headerBand)
                                        .bottomHairline()
                                        .endHairline()
                                else -> Modifier.background(MaterialTheme.colorScheme.surface)
                            },
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    if (dense) {
                        Text(
                            text = stringResource(R.string.live_tv_today),
                            color = GuideEpg.text.copy(alpha = 0.88f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
        }
        // The guide's sticky timeline does not fully occlude the channel column: a channel row
        // sliding under the ruler can paint its number over "Today". Mask the corner above the
        // whole board, then redraw the same label on top. This keeps the top-left clean at every
        // scroll position instead of only when the first channel happens to be at the top.
        if (dense) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .width(dimensions.channelWidth)
                    .height(dimensions.timelineHeight)
                    .background(GuideEpg.ruler)
                    .bottomHairline(GuideEpg.rule)
                    .endHairline(GuideEpg.divider),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = stringResource(R.string.live_tv_today),
                    color = GuideEpg.text.copy(alpha = 0.88f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                )
            }
        }
        if (refreshing) {
            CircularProgress(
                Modifier
                    .background(
                        if (glass) {
                            Color.Black.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.background.copy(alpha = .5f)
                        },
                        shape = CircleShape,
                    ).size(64.dp)
                    .padding(16.dp)
                    .align(Alignment.BottomEnd),
            )
        }
    }
}
