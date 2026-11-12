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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val preferences by viewModel.preferences.data.collectAsState(preferences.appPreferences)
    val tvPrefs = preferences.interfacePreferences.liveTvPreferences
    var showViewOptions by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf<Pair<Int, TvChannel>?>(null) }

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
            val programDialogState =
                viewModel.programDialogState
                    .collectAsState()
                    .value.loading

            var showItemDialog by remember { mutableStateOf<Int?>(null) }
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
            val guideChannels =
                remember(state.channels, state.programs) {
                    val listed =
                        state.channels.filter { channel ->
                            state.programs.programsByChannel[channel.id].orEmpty().any { !it.isFake }
                        }
                    // No listings anywhere is not an empty guide: the fallback below draws the
                    // channels themselves, and hiding them would leave a blank page.
                    listed.ifEmpty { state.channels }
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
                    ErrorMessage("Live TV is enabled, but no channels were found.", null)
                } else {
                    AnimatedVisibility(
                        // A cell between games has nothing to headline. Keeping the slot open
                        // spent 30% of the page on the words "No data" — at the top of a sparse
                        // schedule that is most of what the viewer could see.
                        visible = tvPrefs.showHeader && focusedProgram?.isFake != true && focusedProgram != null,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        TvGuideHeader(
                            program = focusedProgram,
                            modifier =
                                Modifier
                                    .padding(
                                        top = HeaderUtils.topPadding,
                                        bottom = 0.dp,
                                        start = HeaderUtils.startPadding,
                                        end = 24.dp,
                                    )
                                    // A fixed slot so the grid does not jump on every D-pad press,
                                    // but tall enough for a wrapped title, the time line and the
                                    // channel picture. At 30% of the page the last line was sliced
                                    // in half by the header's own edge.
                                    .height(132.dp),
                            trailing = {
                                ExpandableFaButton(
                                    title = R.string.view_options,
                                    iconStringRes = R.string.fa_sliders,
                                    onClick = { showViewOptions = true },
                                    modifier = Modifier.focusRequester(buttonFocusRequester),
                                )
                            },
                        )
                    }
                    TvGuideGridContent(
                        preferences = tvPrefs,
                        refreshing = state.refreshing,
                        channels = guideChannels,
                        programs = state.programs,
                        channelProgramCount = state.channelProgramCount,
                        guideTimes = state.guideTimes,
                        onClickChannel = { index, channel ->
                            showChannelDialog = index to channel
                        },
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
                        onFocusProgram = { focusedProgram = it },
                        onClickProgram = { index, program ->
                            if (program.isFake) {
                                val now = LocalDateTime.now()
                                if (now.isAfter(program.start) && now.isBefore(program.end)) {
                                    viewModel.navigationManager.navigateTo(
                                        Destination.Playback(
                                            itemId = program.channelId,
                                            positionMs = 0L,
                                        ),
                                    )
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            "No guide data found!",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            } else {
                                viewModel.fetchProgramForDialog(program.id)
                                showItemDialog = index
                            }
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
            if (showItemDialog != null) {
                val onDismissRequest = { showItemDialog = null }
                ProgramDialog(
                    state = programDialogState,
                    canRecord = true,
                    onDismissRequest = onDismissRequest,
                    onWatch = {
                        onDismissRequest.invoke()
                        val channelId = it.data.channelId
                        if (channelId != null) {
                            viewModel.navigationManager.navigateTo(
                                Destination.Playback(
                                    itemId = channelId,
                                    positionMs = 0L,
                                ),
                            )
                        } else {
                            Toast
                                .makeText(context, "Program has no channel ID", Toast.LENGTH_LONG)
                                .show()
                        }
                    },
                    onRecord = { program, series ->
                        viewModel.record(
                            programId = program.id,
                            series = series,
                        )
                        onDismissRequest.invoke()
                    },
                    onCancelRecord = { program, series ->
                        viewModel.cancelRecording(
                            series = series,
                            timerId = if (series) program.data.seriesTimerId else program.data.timerId,
                        )
                        onDismissRequest.invoke()
                    },
                )
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
    showChannelDialog?.let { (index, channel) ->
        val watchLiveStr = stringResource(R.string.watch_live)
        val dialogItems =
            remember {
                listOf(
                    DialogItem(
                        watchLiveStr,
                        Icons.Default.PlayArrow,
                        dismissOnClick = true,
                    ) {
                        viewModel.navigationManager.navigateTo(
                            Destination.Playback(
                                itemId = channel.id,
                                positionMs = 0L,
                            ),
                        )
                    },
                    DialogItem(
                        text = if (channel.favorite) R.string.remove_favorite else R.string.add_favorite,
                        iconStringRes = R.string.fa_heart,
                        iconColor = if (channel.favorite) Color.Red else Color.Unspecified,
                        dismissOnClick = true,
                    ) {
                        viewModel.toggleFavorite(index, channel)
                    },
                )
            }
        DialogPopup(
            onDismissRequest = { showChannelDialog = null },
            params = DialogParams(false, channel.name ?: "", dialogItems),
            elevation = 3.dp,
        )
    }
}

val tvGuideDimensions =
    ProgramGuideDimensions(
        timelineHourWidth = 240.dp,
        timelineHeight = 32.dp,
        channelWidth = 120.dp,
        channelHeight = 64.dp,
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
        timelineHeight = 32.dp,
        channelWidth = 150.dp,
        channelHeight = 64.dp,
        currentTimeWidth = 2.dp,
    )

@Composable
fun TvGuideGridContent(
    preferences: LiveTvPreferences,
    refreshing: Boolean,
    channels: List<TvChannel>,
    programs: FetchedPrograms,
    channelProgramCount: Map<UUID, Int>,
    guideTimes: List<LocalDateTime>,
    onClickChannel: (Int, TvChannel) -> Unit,
    onClickProgram: (Int, TvProgram) -> Unit,
    onFocus: (RowColumn) -> Unit,
    onFocusProgram: (TvProgram) -> Unit,
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
    // Start on the first real programme rather than on slot 0: on a tuner that publishes one
    // listing per channel, slot 0 is usually a gap before kickoff, and opening a guide with
    // focus on an empty block is a page that looks like it has nothing in it.
    val firstRealProgram =
        remember(programs.programs) {
            programs.programs.indexOfFirst { !it.isFake }.coerceAtLeast(0)
        }
    var focusedProgramIndex by rememberInt(firstRealProgram)

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
    val programFocusRequester = remember { FocusRequester() }

    BackHandler(focusedItem.row > 0) {
        scope.launch {
            focusedProgramIndex = 0
            state.animateToProgram(0, Alignment.Center)
            programFocusRequester.tryRequestFocus()
        }
    }

    BackHandler(focusedItem.column > 0) {
        scope.launch {
            val programIndex =
                (0..<focusedItem.row)
                    .mapNotNull {
                        val channel = channels[it]
                        channelProgramCount[channel.id]
                    }.sum()
            focusedProgramIndex = programIndex
            state.animateToProgram(programIndex, Alignment.Center)
            programFocusRequester.tryRequestFocus()
        }
    }

    val glass = LocalLiquidGlass.current
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
                .then(if (glass) Modifier.padding(12.dp).guideSlate() else Modifier),
        ) {
        ProgramGuide(
            state = state,
            dimensions = if (glass) glassGuideDimensions else tvGuideDimensions,
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
                if (glass) {
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
                                if (glass) {
                                    GuideSlate.headerBand
                                } else {
                                    MaterialTheme.colorScheme.background
                                },
                            ).padding(horizontal = if (glass) 0.dp else 2.dp)
                            .fillMaxSize()
                            .then(
                                if (glass) {
                                    // One continuous band rather than a row of chips: a time
                                    // ruler is a ruler, and separated tiles made it look like a
                                    // row of buttons that did nothing. It is also the mask that
                                    // rows slide under, so the gaps must not be transparent.
                                    Modifier.bottomHairline()
                                } else {
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                    onLongClick = {},
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
                    onLongClick = {},
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
                                }
                            },
                )
            }

            topCorner {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (glass) {
                                Modifier
                                    .background(GuideSlate.headerBand)
                                    .bottomHairline()
                                    .endHairline()
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.surface)
                            },
                        ),
                )
            }
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
