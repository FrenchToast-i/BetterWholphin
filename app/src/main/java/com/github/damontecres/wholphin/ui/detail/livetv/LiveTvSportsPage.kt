package com.github.damontecres.wholphin.ui.detail.livetv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

/**
 * Sports, as its own destination.
 *
 * The guide is deliberately absent. A fixture list is a calendar, not a 48-hour spreadsheet:
 * what is on, what is next and which day it is are the questions, and the guide's time columns
 * made the viewer read every row to answer them. The page keeps the Live TV filter and tuning
 * paths, so a game starts on the same channel and the same player as a channel in the guide.
 */
@Composable
fun LiveTvSportsPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val programState by viewModel.programDialogState.collectAsState()
    val remindedProgramIds by viewModel.remindedProgramIds.collectAsState()
    val now = rememberTickingNow()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    var filters by remember { mutableStateOf(LiveTvFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    var programActions by remember { mutableStateOf<TvProgram?>(null) }
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

    val channelsById = remember(state.channels) { state.channels.associateBy { it.id } }
    val liveById =
        remember(state.currentPrograms, state.programs.programsByChannel, now) {
            liveProgramsByChannel(state.currentPrograms, state.programs.programsByChannel, now)
        }
    // The targeted sport query when it has an answer, the guide otherwise. The page used to
    // wait for 48 hours of every channel to find a dozen games.
    val sportPrograms =
        remember(state.sportFixtures, state.programs.programs) { state.sportPrograms }
    val sportsChannelIds =
        remember(sportPrograms, state.currentPrograms) {
            sportPrograms.map { it.channelId }.toSet() +
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
    val sports = remember(sportPrograms) { sportPrograms }
    val liveCount = sports.count { it.isLiveAt(now) }
    val upcoming = sports.count { it.start.isAfter(now) }

    fun openProgram(program: TvProgram) {
        programActions = program
        viewModel.fetchProgramForDialog(program.id)
    }

    Column(modifier.fillMaxSize()) {
        SportsPageHeader(
            liveCount = liveCount,
            upcoming = upcoming,
            activeFilterCount = filters.activeCount,
            onFilters = { showFilters = true },
            modifier = Modifier.fillMaxWidth(),
        )
        LiveTvSportsTab(
            onClickProgram = { program ->
                if (program.isLiveAt(now)) {
                    channelsById[program.channelId]?.let(viewModel::tuneChannel)
                } else {
                    openProgram(program)
                }
            },
            onClickChannel = viewModel::tuneChannel,
            onLongClickProgram = ::openProgram,
            channelFilter = { it.id in filteredIds },
            liveOnly = filters.liveOnly,
            focusRequester = focusRequester,
            viewModel = viewModel,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
        )
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
            onDismiss = { showFilters = false },
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
            onOpenGuide = null,
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

@Composable
private fun SportsPageHeader(
    liveCount: Int,
    upcoming: Int,
    activeFilterCount: Int,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    val quiet = if (glass) Color.White.copy(alpha = 0.66f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.sports),
            style = if (glass) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
            color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        if (liveCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier =
                    Modifier
                        .background(LiveRed.copy(alpha = 0.16f), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Box(Modifier.size(7.dp).background(LiveRed, CircleShape))
                Text(
                    text = stringResource(R.string.live_tv_header_live, liveCount),
                    style = if (glass) GlassType.meta else MaterialTheme.typography.labelMedium,
                    color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (upcoming > 0) {
            Text(
                text = stringResource(R.string.live_tv_sports_upcoming_count, upcoming),
                style = if (glass) GlassType.meta else MaterialTheme.typography.labelMedium,
                color = quiet,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onFilters,
            glassFilled = true,
            shape =
                androidx.tv.material3.ClickableSurfaceDefaults.shape(
                    RoundedCornerShape(GlassTokens.pillRadius),
                ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            contentHeight = 40.dp,
        ) {
            Text(
                text =
                    if (activeFilterCount > 0) {
                        stringResource(R.string.live_tv_filters_active, activeFilterCount)
                    } else {
                        stringResource(R.string.live_tv_filters)
                    },
                style = if (glass) GlassType.button else MaterialTheme.typography.labelLarge,
            )
                }
    }
}
