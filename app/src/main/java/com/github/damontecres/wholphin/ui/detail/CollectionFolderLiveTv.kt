package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.detail.livetv.DvrSchedule
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvChannelsTab
import com.github.damontecres.wholphin.ui.detail.livetv.LiveTvSportsTab
import com.github.damontecres.wholphin.ui.detail.livetv.TvGuideGrid
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

    when (val st = state.folders) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<List<TabId>> -> {
            val tabs =
                remember {
                    listOf(
                        TabDetails(R.string.tv_guide),
                        // A tuner can arrive before its guide does. Without these two, a fresh
                        // Live TV page is 48 hours of "No data" with nothing in it to press.
                        TabDetails(R.string.live_tv_channels),
                        TabDetails(R.string.sports),
                        TabDetails(R.string.tv_dvr_schedule),
                    ) +
                        st.data.map {
                            TabDetails(StringStringProvider(it.title))
                        }
                }
            val onClickItem =
                remember {
                    { position: Int, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(item.destination())
                    }
                }

            // The strip stays put on every tab. It used to hide itself as the guide scrolled,
            // which left a viewer deep in a schedule with no way to reach Channels or Sports
            // except Back — and those two are the whole point of a sports tuner.
            TabbedPage(
                itemId = destination.itemId.toString(),
                tabs = tabs,
                modifier = modifier,
            ) { tabIndex, tabDetails ->
                when (tabIndex) {
                    0 -> {
                        TvGuideGrid(
                            preferences = preferences,
                            Modifier
                                .fillMaxSize()
                                .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    1 -> {
                        LiveTvChannelsTab(
                            onClickChannel = { channel ->
                                viewModel.navigationManager.navigateTo(
                                    Destination.Playback(itemId = channel.id, positionMs = 0L),
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            focusRequester = tabDetails.contentFocusRequester,
                        )
                    }

                    2 -> {
                        LiveTvSportsTab(
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
                        if (folderIndex in st.data.indices) {
                            CollectionFolderView(
                                preferences = preferences,
                                onClickItem = onClickItem,
                                itemId = st.data[folderIndex].id,
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
}
