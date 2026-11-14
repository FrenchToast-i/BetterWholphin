package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DiscoverFilter
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.Genre
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.discover.getDiscoverRating
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.listToDotString
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.ui.util.ResProviderStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.successValue
import com.google.common.cache.CacheBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SeerrDiscoverViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow<DiscoverState>(DiscoverState())
        val rating = MutableStateFlow<Map<Int, DiscoverRating>>(mapOf())

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
            }
            fetchAndUpdateState(seerrService::discoverMovies) {
                this.copy(
                    movies =
                        DiscoverRowData(
                            ResStringProvider(R.string.movies_title),
                            it,
                            DiscoverRequestType.DISCOVER_MOVIES,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::discoverTv) {
                this.copy(
                    tv =
                        DiscoverRowData(
                            ResStringProvider(R.string.tv_shows_title),
                            it,
                            DiscoverRequestType.DISCOVER_TV,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::trending) {
                this.copy(
                    trending =
                        DiscoverRowData(
                            ResStringProvider(R.string.trending),
                            it,
                            DiscoverRequestType.TRENDING,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::upcomingMovies) {
                this.copy(
                    upcomingMovies =
                        DiscoverRowData(
                            ResStringProvider(R.string.upcoming_movies),
                            it,
                            DiscoverRequestType.UPCOMING_MOVIES,
                        ),
                )
            }
            fetchAndUpdateState(seerrService::upcomingTv) {
                this.copy(
                    upcomingTv =
                        DiscoverRowData(
                            ResStringProvider(R.string.upcoming_tv),
                            it,
                            DiscoverRequestType.UPCOMING_TV,
                        ),
                )
            }
            viewModelScope.launchIO {
                val movieGenres =
                    try {
                        val result = seerrService.api.searchApi.discoverGenresliderMovieGet()
                        val genres =
                            result
                                .filter { it.id != null && it.name != null }
                                .map {
                                    val id = it.id!!.toUUID()
                                    val imageUrl =
                                        it.backdrops?.randomOrNull()?.let { path ->
                                            seerrService.createImageUrl(
                                                imageType = ImageType.BACKDROP,
                                                path = path,
                                                mediaInfo = null,
                                                // Don't need high resolution
                                                backdropWidth = 1280,
                                                backdropHeight = 720,
                                            )
                                        }
                                    Genre(
                                        id = id,
                                        name = it.name!!,
                                        imageUrl = imageUrl,
                                    )
                                }
                        Timber.v("Got %s movie genres", genres.size)
                        DataLoadingState.Success(genres)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error getting movie genres")
                        DataLoadingState.Error(ex)
                    }
                state.update {
                    it.copy(
                        movieGenres = movieGenres,
                    )
                }
            }

            viewModelScope.launchIO {
                val tvGenres =
                    try {
                        val result = seerrService.api.searchApi.discoverGenresliderTvGet()
                        val genres =
                            result
                                .filter { it.id != null && it.name != null }
                                .map {
                                    val id = it.id!!.toUUID()
                                    val imageUrl =
                                        it.backdrops?.randomOrNull()?.let { path ->
                                            seerrService.createImageUrl(
                                                imageType = ImageType.BACKDROP,
                                                path = path,
                                                mediaInfo = null,
                                                // Don't need high resolution
                                                backdropWidth = 1280,
                                                backdropHeight = 720,
                                            )
                                        }
                                    Genre(
                                        id = id,
                                        name = it.name!!,
                                        imageUrl = imageUrl,
                                    )
                                }
                        Timber.v("Got %s tv genres", genres.size)
                        DataLoadingState.Success(genres)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error getting tv genres")
                        DataLoadingState.Error(ex)
                    }
                state.update {
                    it.copy(
                        tvGenres = tvGenres,
                    )
                }
            }
        }

        private fun fetchAndUpdateState(
            getData: suspend () -> List<DiscoverItem>,
            copyFunc: DiscoverState.(DataLoadingState<List<DiscoverItem>>) -> DiscoverState,
        ) {
            viewModelScope.launchIO {
                state.update {
                    copyFunc.invoke(it, DataLoadingState.Loading)
                }
                try {
                    val results = getData.invoke()
                    state.update {
                        copyFunc.invoke(it, DataLoadingState.Success(results))
                    }
                } catch (ex: Exception) {
                    state.update {
                        copyFunc.invoke(it, DataLoadingState.Error(ex))
                    }
                }
            }
        }

        fun updateBackdrop(item: DiscoverFocusedItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                } else {
                    backdropService.clearBackdrop()
                }
            }
        }

        private val ratingCache =
            CacheBuilder
                .newBuilder()
                .maximumSize(100)
                .build<Int, DiscoverRating>()

        // TODO this is not very efficient
        fun fetchRating(
            id: Int,
            type: SeerrItemType,
        ) {
            viewModelScope.launchIO {
                val cachedResult = ratingCache.getIfPresent(id)
                if (cachedResult != null) {
                    return@launchIO
                }
                val result =
                    getDiscoverRating(id) {
                        when (type) {
                            SeerrItemType.MOVIE -> {
                                DiscoverRating(
                                    seerrService.api.moviesApi.movieMovieIdRatingsGet(movieId = id),
                                )
                            }

                            SeerrItemType.TV -> {
                                DiscoverRating(seerrService.api.tvApi.tvTvIdRatingsGet(tvId = id))
                            }

                            SeerrItemType.PERSON -> {
                                DiscoverRating(null, null)
                            }

                            SeerrItemType.UNKNOWN -> {
                                DiscoverRating(null, null)
                            }
                        }
                    } ?: DiscoverRating(null, null)

                ratingCache.put(id, result)
                rating.update {
                    ratingCache.asMap().toMap()
                }
            }
        }
    }

fun Int.toUUID() = UUID(0L, toLong())

fun UUID.toInt() = leastSignificantBits.toInt()

data class DiscoverRowData(
    val title: StringProvider,
    val items: DataLoadingState<List<DiscoverItem>>,
    val type: DiscoverRequestType,
) {
    companion object {
        val EMPTY =
            DiscoverRowData(
                EmptyStringProvider,
                DataLoadingState.Pending,
                DiscoverRequestType.UNKNOWN,
            )
    }
}

data class DiscoverState(
    val movies: DiscoverRowData = DiscoverRowData.EMPTY,
    val tv: DiscoverRowData = DiscoverRowData.EMPTY,
    val trending: DiscoverRowData = DiscoverRowData.EMPTY,
    val upcomingMovies: DiscoverRowData = DiscoverRowData.EMPTY,
    val upcomingTv: DiscoverRowData = DiscoverRowData.EMPTY,
    val movieGenres: DataLoadingState<List<Genre>> = DataLoadingState.Pending,
    val tvGenres: DataLoadingState<List<Genre>> = DataLoadingState.Pending,
)

private const val ROW_MOVIES = 0
private const val ROW_GENRES_MOVIE = ROW_MOVIES + 1
private const val ROW_TV = ROW_GENRES_MOVIE + 1
private const val ROW_GENRES_TV = ROW_TV + 1
private const val ROW_COMING_SOON = ROW_GENRES_TV + 1

private const val LAST_ROW = ROW_COMING_SOON

/**
 * Trending and the mainstream row answer one question — what is worth watching now — so they
 * are one shelf per medium. Trending titles lead their own medium's row and the rest follow;
 * a title in both lists appears once. Three rails of the same posters is what made this page
 * read as padded, and it also hid trending titles from the row whose heading describes them.
 */
private fun DiscoverRowData.leadingWith(
    other: DiscoverRowData,
    type: SeerrItemType,
): DiscoverRowData {
    val lead = (items as? DataLoadingState.Success)?.data.orEmpty().filter { it.type == type }
    val rest = (other.items as? DataLoadingState.Success)?.data.orEmpty().filter { it.type == type }
    val merged = (lead + rest).distinctBy { it.id }
    return copy(
        items =
            when {
                items is DataLoadingState.Success || other.items is DataLoadingState.Success ->
                    DataLoadingState.Success(merged)
                items is DataLoadingState.Error -> items
                other.items is DataLoadingState.Error -> other.items
                else -> DataLoadingState.Pending
            },
    )
}

/**
 * Films and shows that are not out yet are one question — "what is coming?" — so they are one
 * shelf, merged by date. Two shelves split by media type made the page ask the viewer to care
 * about a distinction they do not make when browsing a release calendar.
 */
private fun DiscoverState.comingSoon(): DiscoverRowData {
    val movies = (upcomingMovies.items as? DataLoadingState.Success)?.data.orEmpty()
    val shows = (upcomingTv.items as? DataLoadingState.Success)?.data.orEmpty()
    val merged =
        (movies + shows)
            .distinctBy { it.id }
            .sortedBy { it.releaseDate?.toString().orEmpty() }
    val loading =
        when {
            upcomingMovies.items is DataLoadingState.Success ||
                upcomingTv.items is DataLoadingState.Success -> DataLoadingState.Success(merged)
            upcomingMovies.items is DataLoadingState.Error -> upcomingMovies.items
            upcomingTv.items is DataLoadingState.Error -> upcomingTv.items
            else -> DataLoadingState.Pending
        }
    return DiscoverRowData(
        title = ResStringProvider(R.string.coming_soon),
        items = loading,
        type = DiscoverRequestType.UNKNOWN,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeerrDiscoverPage(
    preferences: UserPreferences,
    positionCallback: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeerrDiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val focusRequesters = remember { List(LAST_ROW + 1) { FocusRequester() } }
    var position by rememberPosition(0, -1)
    // Trending is not a shelf of its own: its films lead the film row and its shows lead the
    // television row. One heading per medium, and no poster appears twice on the page.
    val movieRow =
        remember(state.trending, state.movies) {
            state.trending.leadingWith(state.movies, SeerrItemType.MOVIE)
        }
    val tvRow =
        remember(state.trending, state.tv) {
            state.trending.leadingWith(state.tv, SeerrItemType.TV)
        }
    val comingSoonRow = remember(state.upcomingMovies, state.upcomingTv) { state.comingSoon() }
    val focusedItem: DiscoverFocusedItem? =
        remember(position, state) {
            position.let {
                val discoverRow =
                    when (position.row) {
                        ROW_MOVIES -> movieRow
                        ROW_TV -> tvRow
                        ROW_COMING_SOON -> comingSoonRow
                        else -> null
                    }
                if (discoverRow != null) {
                    (discoverRow.items as? DataLoadingState.Success)
                        ?.data
                        ?.getOrNull(it.column)
                        ?.let {
                            DiscoverFocusedItem.Item(it)
                        }
                } else {
                    when (position.row) {
                        ROW_GENRES_MOVIE -> {
                            state.movieGenres.successValue
                                ?.getOrNull(position.column)
                                ?.let {
                                    DiscoverFocusedItem.Genre(
                                        id = it.id.toInt(),
                                        name = it.name,
                                        type = SeerrItemType.MOVIE,
                                        backDropUrl = it.imageUrl,
                                    )
                                }
                        }

                        ROW_GENRES_TV -> {
                            // The TV row used the movie genres, so focusing a television genre
                            // looked up a film genre with the same index — usually a different
                            // name, and sometimes no entry at all.
                            state.tvGenres.successValue
                                ?.getOrNull(position.column)
                                ?.let {
                                    DiscoverFocusedItem.Genre(
                                        id = it.id.toInt(),
                                        name = it.name,
                                        type = SeerrItemType.TV,
                                        backDropUrl = it.imageUrl,
                                    )
                                }
                        }

                        else -> {
                            null
                        }
                    }
                }
            }
        }
    LaunchedEffect(focusedItem) {
        viewModel.updateBackdrop(focusedItem)
    }
    var firstFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.movies, state.trending) {
        if (!firstFocused &&
            (state.trending.items is DataLoadingState.Success<*> ||
                state.movies.items is DataLoadingState.Success<*>)
        ) {
            firstFocused = focusRequesters.getOrNull(0)?.tryRequestFocus("discover") == true
        } else if (firstFocused) {
            focusRequesters.getOrNull(position.row)?.tryRequestFocus()
        }
    }

    Column(
        modifier = modifier,
    ) {
        // No page hero. This is a browsing surface: the focused poster's artwork is already the
        // page's backdrop, and a half-page pane for a title nobody has opened pushed the first
        // shelf below the fold — the same fault as the old home screen. One compact line says
        // where the viewer is; the shelves are the page.
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.discover),
                style =
                    if (LocalLiquidGlass.current) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                color = if (LocalLiquidGlass.current) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.discover_seerr_subtitle),
                style =
                    if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodyMedium,
                color =
                    if (LocalLiquidGlass.current) {
                        Color.White.copy(alpha = 0.62f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        val density = LocalDensity.current
        val spaceAbovePx =
            with(density) {
                // The size of the row titles & spacing
                50.dp.toPx()
            }
        val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current

        @Composable
        fun discoverItemRow(
            rowIndex: Int,
            row: DiscoverRowData,
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                DiscoverRow(
                    row = row,
                    onClickItem = { index, item ->
                        position = RowColumn(rowIndex, index)
                        viewModel.navigationManager.navigateTo(item.destination)
                    },
                    onLongClickItem = { index, item -> },
                    onCardFocus = { index ->
                        position = RowColumn(rowIndex, index)
                        positionCallback.invoke(position)
                    },
                    focusRequester = focusRequesters[rowIndex],
                    enableViewMore = row.type != DiscoverRequestType.UNKNOWN,
                    onClickViewMore = {
                        (row.items as? DataLoadingState.Success<List<DiscoverItem>>)?.data?.size?.let {
                            position = RowColumn(rowIndex, it)
                            positionCallback.invoke(position)
                        }
                        viewModel.navigationManager.navigateTo(
                            Destination.DiscoverMoreResult(
                                type = row.type,
                            ),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
        }
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                modifier =
                    Modifier
                        .focusRestorer()
                        .fillMaxSize(),
            ) {
                item { discoverItemRow(ROW_MOVIES, movieRow) }
                item { discoverItemRow(ROW_TV, tvRow) }
                // What is coming is the question this page exists to answer for a household
                // that owns everything else; it sits above the genre browsers rather than
                // below two rails of posters.
                item { discoverItemRow(ROW_COMING_SOON, comingSoonRow) }
                item(key = "browse_label") {
                    Text(
                        text = stringResource(R.string.browse_by_genre).uppercase(),
                        style =
                            if (LocalLiquidGlass.current) GlassType.sectionLabel else MaterialTheme.typography.titleMedium,
                        color =
                            if (LocalLiquidGlass.current) {
                                Color.White.copy(alpha = 0.54f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 2.dp),
                    )
                }
                item {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                        DiscoverGenreRow(
                            title =
                                remember {
                                    ResProviderStringProvider(
                                        R.string.genres_in,
                                        ResStringProvider(R.string.movies_title),
                                    )
                                },
                            items = state.movieGenres,
                            onClickItem = { index, genre ->
                                position = RowColumn(ROW_GENRES_MOVIE, index)
                                viewModel.navigationManager.navigateTo(
                                    Destination.DiscoverMoreResult(
                                        type = DiscoverRequestType.DISCOVER_MOVIES,
                                        initialFilter =
                                            DiscoverFilter(
                                                genreIds = listOf(genre.id.toInt()),
                                            ),
                                        titleOverride =
                                            discoverGenreTitle(
                                                genre.name,
                                                SeerrItemType.MOVIE,
                                            ),
                                        startIndex = 0,
                                    ),
                                )
                            },
                            onCardFocus = { index ->
                                position = RowColumn(ROW_GENRES_MOVIE, index)
                                positionCallback.invoke(position)
                            },
                            modifier = Modifier.focusRequester(focusRequesters[ROW_GENRES_MOVIE]),
                        )
                    }
                }
                item {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                        DiscoverGenreRow(
                            title =
                                remember {
                                    ResProviderStringProvider(
                                        R.string.genres_in,
                                        ResStringProvider(R.string.tv_shows_title),
                                    )
                                },
                            items = state.tvGenres,
                            onClickItem = { index, genre ->
                                position = RowColumn(ROW_GENRES_TV, index)
                                viewModel.navigationManager.navigateTo(
                                    Destination.DiscoverMoreResult(
                                        type = DiscoverRequestType.DISCOVER_TV,
                                        initialFilter =
                                            DiscoverFilter(
                                                genreIds = listOf(genre.id.toInt()),
                                            ),
                                        titleOverride =
                                            discoverGenreTitle(
                                                genre.name,
                                                SeerrItemType.TV,
                                            ),
                                        startIndex = 0,
                                    ),
                                )
                            },
                            onCardFocus = { index ->
                                position = RowColumn(ROW_GENRES_TV, index)
                                positionCallback.invoke(position)
                            },
                            modifier = Modifier.focusRequester(focusRequesters[ROW_GENRES_TV]),
                        )
                    }
                }
            }
        }
    }
}
