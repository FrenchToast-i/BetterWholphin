package com.github.damontecres.wholphin.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.detail.livetv.ChannelInfoDialog
import com.github.damontecres.wholphin.ui.detail.livetv.ChannelNumberDialog
import com.github.damontecres.wholphin.ui.detail.livetv.DvrSchedule
import com.github.damontecres.wholphin.ui.detail.livetv.GuideEpg
import com.github.damontecres.wholphin.ui.detail.livetv.GuideInfoHeader
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvFilterDialog
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvFilters
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvSearchDialog
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvViewModel
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramCategory
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramDialog
import com.github.damontecres.wholphin.ui.detail.livetv.TvChannel
import com.github.damontecres.wholphin.ui.detail.livetv.TvGuideGrid
import com.github.damontecres.wholphin.ui.detail.livetv.TvProgram
import com.github.damontecres.wholphin.ui.detail.livetv.filterChannels
import com.github.damontecres.wholphin.ui.detail.livetv.liveProgramsByChannel
import com.github.damontecres.wholphin.ui.detail.livetv.nextProgramForChannel
import com.github.damontecres.wholphin.ui.detail.livetv.rememberTickingNow
import com.github.damontecres.wholphin.ui.nav.Destination

/**
 * Live TV as the listing page, and only the listing page.
 *
 * The previous page was five tabs sharing a header: Now, Guide, Channels, Sports, DVR. The
 * reference makes the guide itself the page, with the focused programme's masthead above it and a
 * GUIDE key for the filters. Sports has moved to its own destination. This composable keeps one
 * tuning path, one programme-action path and one filter object behind that simpler surface.
 */
@Composable
fun CollectionFolderLiveTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
) {
    val viewModel: LiveTvViewModel = hiltViewModel()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast
                    .makeText(
                        context,
                        R.string.live_tv_reminders_permission_denied,
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    val state by viewModel.state.collectAsState()
    val lastChannelId by viewModel.lastChannelId.collectAsState()
    val remindedProgramIds by viewModel.remindedProgramIds.collectAsState()
    val programState by viewModel.programDialogState.collectAsState()
    val now = rememberTickingNow()

    var filters by remember { mutableStateOf(LiveTvFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showNumberPad by remember { mutableStateOf(false) }
    var showDvr by remember { mutableStateOf(false) }
    var numberDigits by remember { mutableStateOf("") }
    var channelActions by remember { mutableStateOf<TvChannel?>(null) }
    var programActions by remember { mutableStateOf<TvProgram?>(null) }
    var headerProgram by remember { mutableStateOf<TvProgram?>(null) }
    val filterFocusRequester = remember { FocusRequester() }
    val dvrFocusRequester = remember { FocusRequester() }

    val channelsById = remember(state.channels) { state.channels.associateBy { it.id } }
    val liveById =
        remember(state.currentPrograms, state.programs.programsByChannel, now) {
            liveProgramsByChannel(state.currentPrograms, state.programs.programsByChannel, now)
        }
    val sportsChannelIds =
        remember(state.currentPrograms, state.programs.programs) {
            state.programs.programs
                .asSequence()
                .filter { !it.isFake && it.category == ProgramCategory.SPORTS }
                .map { it.channelId }
                .toSet() +
                state.currentPrograms.values
                    .filter { it.category == ProgramCategory.SPORTS && !it.isFake }
                    .map { it.channelId }
        }
    val availableCategories =
        remember(state.currentPrograms, state.programs.programs) {
            buildSet {
                state.currentPrograms.values
                    .mapNotNull { it.category }
                    .filter { it != ProgramCategory.FAKE }
                    .forEach(::add)
                state.programs.programs
                    .asSequence()
                    .filterNot { it.isFake }
                    .mapNotNull { it.category }
                    .filter { it != ProgramCategory.FAKE }
                    .forEach(::add)
            }
        }
    val filteredChannels =
        remember(state.channels, filters, liveById, state.programs, sportsChannelIds, now) {
            filterChannels(
                channels = state.channels,
                filters = filters,
                currentPrograms = liveById,
                allProgramsByChannel = state.programs.programsByChannel,
                sportsChannelIds = sportsChannelIds,
                now = now,
            )
        }
    val filteredIds = remember(filteredChannels) { filteredChannels.map { it.id }.toSet() }
    val lastUpdated =
        state.lastUpdated?.let {
            DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString()
        }
    // The masthead is never blank while there is something to describe: before the grid reports a
    // focused cell, it names the first live programme in the visible list.
    val initialProgram =
        remember(filteredChannels, liveById, state.programs.programsByChannel) {
            filteredChannels.firstNotNullOfOrNull { channel ->
                liveById[channel.id]
                    ?: state.programs.programsByChannel[channel.id]
                        .orEmpty()
                        .firstOrNull { !it.isFake }
            }
        }
    val header = headerProgram ?: initialProgram
    val headerChannel = header?.let { channelsById[it.channelId] }

    fun openProgram(program: TvProgram) {
        programActions = program
        viewModel.fetchProgramForDialog(program.id)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(GuideEpg.background)
            // Filters belong to the page, not only to the grid. The masthead's FILTER disc is
            // the path that needs no special remote; the key handling below is the shortcut for
            // viewers who map an existing button with Shizuku.
            .onPreviewKeyEvent { event ->
                // GUIDE is a system-level key on some television builds: the framework can
                // consume its key-up while still passing the down. Handle either action, and
                // accept the keys a Shizuku remapper can synthesize (INFO and MENU). A guide
                // button is not required; INFO/MENU are what an existing remote button can send.
                val guideKey =
                    event.nativeKeyEvent.keyCode in
                        intArrayOf(
                            AndroidKeyEvent.KEYCODE_GUIDE,
                            AndroidKeyEvent.KEYCODE_INFO,
                            AndroidKeyEvent.KEYCODE_MENU,
                        )
                if (guideKey) {
                    showFilters = true
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val digit =
                    when (event.key) {
                        Key.Zero, Key.NumPad0 -> "0"
                        Key.One, Key.NumPad1 -> "1"
                        Key.Two, Key.NumPad2 -> "2"
                        Key.Three, Key.NumPad3 -> "3"
                        Key.Four, Key.NumPad4 -> "4"
                        Key.Five, Key.NumPad5 -> "5"
                        Key.Six, Key.NumPad6 -> "6"
                        Key.Seven, Key.NumPad7 -> "7"
                        Key.Eight, Key.NumPad8 -> "8"
                        Key.Nine, Key.NumPad9 -> "9"
                        else -> null
                    }
                if (digit == null) {
                    false
                } else {
                    numberDigits = (numberDigits + digit).takeLast(6)
                    showNumberPad = true
                    true
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            GuideInfoHeader(
                program = header,
                channel = headerChannel,
                filters = filters,
                now = now,
                onOpenFilters = { showFilters = true },
                filterFocusRequester = filterFocusRequester,
            )
            TvGuideGrid(
                preferences = preferences,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                channelFilter = { it.id in filteredIds },
                initialChannelId = lastChannelId,
                tabFocusRequester = filterFocusRequester,
                onFocusedProgramChange = { focused ->
                    headerProgram = focused
                },
                onProgramActions = ::openProgram,
                onChannelActions = { channelActions = it },
                showToolbar = false,
                dense = true,
            )
        }
    }

    if (showFilters) {
        LiveTvFilterDialog(
            filters = filters,
            hasLive = liveById.isNotEmpty(),
            hasSports = sportsChannelIds.isNotEmpty(),
            availableCategories = availableCategories,
            lastUpdated = lastUpdated,
            onFiltersChange = { filters = it },
            onRefreshGuide = {
                viewModel.refreshGuide()
                showFilters = false
            },
            onSearch = {
                showFilters = false
                showSearch = true
            },
            onOpenDvr = {
                showFilters = false
                showDvr = true
            },
            onDismiss = { showFilters = false },
        )
    }

    if (showSearch) {
        LiveTvSearchDialog(
            channels = state.channels,
            currentPrograms = liveById,
            programs = state.programs.programs.filter { !it.isFake },
            now = now,
            onTunes = {
                showSearch = false
                viewModel.tuneChannel(it)
            },
            onProgramActions = {
                showSearch = false
                openProgram(it)
            },
            onDismiss = { showSearch = false },
        )
    }

    if (showNumberPad) {
        ChannelNumberDialog(
            channels = state.channels,
            currentPrograms = liveById,
            initialDigits = numberDigits,
            onTune = {
                showNumberPad = false
                numberDigits = ""
                viewModel.tuneChannel(it)
            },
            onDismiss = {
                showNumberPad = false
                numberDigits = ""
            },
        )
    }

    if (showDvr) {
        Dialog(
            onDismissRequest = { showDvr = false },
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(GuideEpg.background)
                    .padding(16.dp),
            ) {
                DvrSchedule(
                    requestFocusAfterLoading = true,
                    tabFocusRequester = dvrFocusRequester,
                    onOpenGuide = { showDvr = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    channelActions?.let { channel ->
        ChannelInfoDialog(
            channel = channel,
            current = liveById[channel.id] ?: state.currentPrograms[channel.id],
            next = nextProgramForChannel(state.programs.programsByChannel, channel.id, now),
            isFavorite = channel.favorite,
            onWatch = {
                channelActions = null
                viewModel.tuneChannel(channel)
            },
            onToggleFavorite = {
                val index = state.channels.indexOfFirst { it.id == channel.id }
                if (index >= 0) viewModel.toggleFavorite(index, channel)
                channelActions = null
            },
            onOpenGuide = { channelActions = null },
            onDismiss = { channelActions = null },
        )
    }

    programActions?.let { program ->
        val channel = channelsById[program.channelId]
        ProgramDialog(
            state = programState.loading,
            canRecord = true,
            onDismissRequest = { programActions = null },
            onWatch = {
                programActions = null
                channel?.let(viewModel::tuneChannel)
            },
            onRecord = { item, series ->
                viewModel.record(item.id, series)
                programActions = null
            },
            onCancelRecord = { item, series ->
                viewModel.cancelRecording(
                    series = series,
                    timerId = if (series) item.data.seriesTimerId else item.data.timerId,
                )
                programActions = null
            },
            channelName = channel?.name,
            isFavoriteChannel = channel?.favorite == true,
            isReminded = program.id in remindedProgramIds,
            onFavoriteChannel = {
                if (channel != null) {
                    val index = state.channels.indexOfFirst { it.id == channel.id }
                    if (index >= 0) viewModel.toggleFavorite(index, channel)
                }
                programActions = null
            },
            onOpenGuide = { programActions = null },
            onRemind = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.toggleReminded(program, channel?.name)
            },
        )
    }
}
