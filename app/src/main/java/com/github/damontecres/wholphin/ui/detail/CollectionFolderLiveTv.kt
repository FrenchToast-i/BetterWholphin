package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.detail.livetv.DvrSchedule
import com.github.damontecres.wholphin.ui.detail.livetv.LiveRed
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvChannelsTab
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvSportsTab
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvViewModel
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramCategory
import com.github.damontecres.wholphin.ui.detail.livetv.TvChannel
import com.github.damontecres.wholphin.ui.detail.livetv.TvGuideGrid
import com.github.damontecres.wholphin.ui.detail.livetv.rememberTickingNow
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LiveTvCollectionViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(LiveTvRecordingFolderState())

        init {
            viewModelScope.launchIO {
                try {
                    val folders =
                        api.liveTvApi
                            .getRecordingFolders(userId = serverRepository.currentUser?.id)
                            .content.items
                            .map { TabId(it.name ?: "Recordings", it.id) }
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Success(folders))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching recording folders")
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Error(ex))
                }
            }
        }
    }

data class LiveTvRecordingFolderState(
    val folders: DataLoadingState<List<TabId>> = DataLoadingState.Pending,
)

data class TabId(
    val title: String,
    val id: UUID,
)

@Composable
fun CollectionFolderLiveTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: LiveTvCollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // The recordings list is a shortcut, not a prerequisite. The page used to hold a spinner
    // over the whole tuner until `getRecordingFolders` answered, so a slow or unavailable
    // recordings endpoint delayed the guide, the channels and the sports board — the three
    // views a sports tuner exists for. The folders now append their tabs when they arrive,
    // and their absence costs nothing but the shortcut.
    val folders = (state.folders as? DataLoadingState.Success)?.data.orEmpty()
    run {
            val tabs =
                remember(folders) {
                    listOf(
                        // This fork's tuner is a sports tuner: every channel is a fixture, and
                        // the question a viewer opens Live TV with is "what game is on?". The
                        // schedule board answers it; the guide is the detail view, not the
                        // landing page.
                        TabDetails(R.string.sports),
                        TabDetails(R.string.tv_guide),
                        // A tuner can arrive before its guide does. Without these two, a fresh
                        // Live TV page is 48 hours of "No data" with nothing in it to press.
                        TabDetails(R.string.live_tv_channels),
                        TabDetails(R.string.tv_dvr_schedule),
                    ) +
                        folders.map {
                            TabDetails(StringStringProvider(it.title))
                        }
                }
            val onClickItem =
                remember {
                    { position: Int, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(item.destination())
                    }
                }
            // The header reads the guide the tabs already load. It is the answer to "is there
            // anything on?", and it belongs above the tabs rather than inside one: a viewer
            // opening Live TV to watch a game should not have to open the guide to find out
            // whether the game is on, and the DVR or Channels tabs are not an answer.
            val liveViewModel: LiveTvViewModel = hiltViewModel()
            val tvState by liveViewModel.state.collectAsState()
            val now = rememberTickingNow()
            val sports =
                remember(tvState.programs.programs) {
                    tvState.programs.programs.filter {
                        it.category == ProgramCategory.SPORTS && !it.isFake
                    }
                }
            val liveCount = sports.count { it.start <= now && now.isBefore(it.end) }
            val nextStart =
                remember(sports, now) {
                    sports.map { it.start }.filter { it.isAfter(now) }.minOrNull()
                }
            // The tuner is a sports tuner, but it still carries the channels that came with it.
            // Two filters cover every question a viewer asks of a list that size: "only my
            // favourites" and "only the channels that actually show sport". They apply to the
            // guide, the channel grid and the sports board together, so switching tabs never
            // silently changes the set of channels being talked about.
            var favoritesOnly by rememberSaveable { mutableStateOf(false) }
            var sportsOnly by rememberSaveable { mutableStateOf(false) }
            var liveOnly by rememberSaveable { mutableStateOf(false) }
            val sportsChannelIds =
                remember(tvState.programs.programs) {
                    tvState.programs.programs
                        .filter { it.category == ProgramCategory.SPORTS && !it.isFake }
                        .map { it.channelId }
                        .toSet()
                }
            // The channels carrying a game right now. "Sports only" answers "which channels
            // show sport at all"; this answers "where is the game that is on", which is the one
            // question a viewer opens Live TV with while a match is running.
            val liveChannelIds =
                remember(sports, now) {
                    sports.filter { it.start <= now && now.isBefore(it.end) }
                        .map { it.channelId }
                        .toSet()
                }
            // A filter that can only ever show nothing is a trap. When the last game ends, the
            // chip leaves the band and the filter clears with it, rather than leaving the page
            // empty with no visible reason.
            LaunchedEffect(liveCount) {
                if (liveCount == 0) liveOnly = false
            }
            val channelFilter: (TvChannel) -> Boolean =
                remember(favoritesOnly, sportsOnly, liveOnly, sportsChannelIds, liveChannelIds) {
                    { channel ->
                        (!favoritesOnly || channel.favorite) &&
                            (!sportsOnly || channel.id in sportsChannelIds) &&
                            (!liveOnly || channel.id in liveChannelIds)
                    }
                }

            // The strip stays put on every tab. It used to hide itself as the guide scrolled,
            // which left a viewer deep in a schedule with no way to reach Channels or Sports
            // except Back — and those two are the whole point of a sports tuner.
            TabbedPage(
                itemId = destination.itemId.toString(),
                tabs = tabs,
                modifier = modifier,
                header = {
                    LiveTvHeader(
                        liveCount = liveCount,
                        nextStart = nextStart,
                        favoritesOnly = favoritesOnly,
                        sportsOnly = sportsOnly,
                        liveOnly = liveOnly,
                        showLiveFilter = liveCount > 0,
                        showSportsFilter = sportsChannelIds.isNotEmpty(),
                        onToggleFavorites = { favoritesOnly = !favoritesOnly },
                        onToggleSports = { sportsOnly = !sportsOnly },
                        onToggleLive = { liveOnly = !liveOnly },
                    )
                },
            ) { tabIndex, tabDetails ->
                when (tabIndex) {
                    0 -> {
                        LiveTvSportsTab(
                            favoritesOnly = favoritesOnly,
                            liveOnly = liveOnly,
                            onClickProgram = { program ->
                                viewModel.navigationManager.navigateTo(
                                    Destination.Playback(itemId = program.channelId, positionMs = 0L),
                                )
                            },
                            onClickChannel = { channel ->
                                viewModel.navigationManager.navigateTo(
                                    Destination.Playback(itemId = channel.id, positionMs = 0L),
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            focusRequester = tabDetails.contentFocusRequester,
                        )
                    }

                    1 -> {
                        TvGuideGrid(
                            preferences = preferences,
                            Modifier
                                .fillMaxSize()
                                .focusRequester(tabDetails.contentFocusRequester),
                            channelFilter = channelFilter,
                        )
                    }

                    2 -> {
                        LiveTvChannelsTab(
                            channelFilter = channelFilter,
                            onClickChannel = { channel ->
                                viewModel.navigationManager.navigateTo(
                                    Destination.Playback(itemId = channel.id, positionMs = 0L),
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            focusRequester = tabDetails.contentFocusRequester,
                        )
                    }

                    3 -> {
                        DvrSchedule(
                            requestFocusAfterLoading = true,
                            focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    else -> {
                        val folderIndex = tabIndex - 4
                        if (folderIndex in folders.indices) {
                            CollectionFolderView(
                                preferences = preferences,
                                onClickItem = onClickItem,
                                itemId = folders[folderIndex].id,
                                initialFilter = CollectionFolderFilter(),
                                showTitle = false,
                                recursive = false,
                                sortOptions = VideoSortOptions,
                                modifier = Modifier.focusRequester(tabDetails.contentFocusRequester),
                                positionCallback = { _, _ -> },
                                playEnabled = false,
                                defaultViewOptions = ViewOptions(),
                                focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            )
                        } else {
                            ErrorMessage("Invalid tab index $tabIndex", null)
                        }
                    }
                }
            }
    }
}

private val liveTvHeaderTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * The band above the Live TV tabs.
 *
 * It carries the one fact every tab needs: whether a game is on right now, and if not, when the
 * next one starts. The count is drawn as a live chip rather than a sentence because it is read
 * from across the room, and an empty tuner says so instead of leaving a blank space where a
 * number would be.
 */
@Composable
private fun LiveTvHeader(
    liveCount: Int,
    nextStart: LocalDateTime?,
    favoritesOnly: Boolean,
    sportsOnly: Boolean,
    liveOnly: Boolean,
    showLiveFilter: Boolean,
    showSportsFilter: Boolean,
    onToggleFavorites: () -> Unit,
    onToggleSports: () -> Unit,
    onToggleLive: () -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val quiet = if (glass) Color.White.copy(alpha = 0.58f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.live_tv),
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
                Box(
                    modifier =
                        Modifier
                            .size(7.dp)
                            .background(LiveRed, CircleShape),
                )
                Text(
                    text = stringResource(R.string.live_tv_header_live, liveCount),
                    style = if (glass) GlassType.meta else MaterialTheme.typography.labelMedium,
                    color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.live_tv_header_quiet),
                style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                color = quiet,
            )
        }
        LiveTvFilterChip(
            label = stringResource(R.string.favorites),
            selected = favoritesOnly,
            onClick = onToggleFavorites,
        )
        if (showLiveFilter) {
            LiveTvFilterChip(
                label = stringResource(R.string.live_tv_filter_live),
                selected = liveOnly,
                onClick = onToggleLive,
            )
        }
        if (showSportsFilter) {
            LiveTvFilterChip(
                label = stringResource(R.string.live_tv_filter_sports),
                selected = sportsOnly,
                onClick = onToggleSports,
            )
        }
        Spacer(Modifier.weight(1f))
        nextStart?.let {
            Text(
                text = stringResource(R.string.live_tv_header_next, it.format(liveTvHeaderTime)),
                style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                color = quiet,
            )
        }
    }
}

/**
 * A toggle in the Live TV band.
 *
 * Selected is a **white fill with black type**, the same signal the bar uses for the current
 * page, because on a television a tint alone does not read as "on" from the sofa. Focus is a
 * dark rim rather than a lighter fill: white-on-white would make a focused selected chip
 * indistinguishable from an unfocused one.
 */
@Composable
private fun LiveTvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(percent = 50)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .clip(shape)
                .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color =
                        if (focused) {
                            Color.Black.copy(alpha = 0.78f)
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        },
                    shape = shape,
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style =
                if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.labelMedium,
            color = if (selected) Color.Black else Color.White,
        )
    }
}
