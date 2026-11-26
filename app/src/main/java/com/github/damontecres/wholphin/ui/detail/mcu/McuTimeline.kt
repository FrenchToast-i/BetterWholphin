package com.github.damontecres.wholphin.ui.detail.mcu

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.cards.ItemCardImageFallback
import com.github.damontecres.wholphin.ui.cards.glassCardBorder
import com.github.damontecres.wholphin.ui.cards.glassCardGlow
import com.github.damontecres.wholphin.ui.cards.glassCardScale
import com.github.damontecres.wholphin.ui.cards.glassCardShape
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/** The fixed id of the MCU tile in a movie library's Collections tab. */
private val MCU_COLLECTION_ID: UUID = UUID.nameUUIDFromBytes("wholphin-mcu-collection".toByteArray())

/** Marvel's red, used for the timeline's dates and the request badges. */
private val McuRed = Color(0xFFE23636)

private val MdateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * One Marvel Studios film, in release order.
 *
 * Only films: the timeline is a release calendar, so nothing here is a series or a special.
 * Since the dates are fixed, the list is data rather than a server query — the server only
 * decides which of these the viewer already owns.
 */
data class McuFilm(
    val title: String,
    val releaseDate: LocalDate,
)

val McuFilms: List<McuFilm> =
    listOf(
        McuFilm("Iron Man", LocalDate.of(2008, 5, 2)),
        McuFilm("The Incredible Hulk", LocalDate.of(2008, 6, 13)),
        McuFilm("Iron Man 2", LocalDate.of(2010, 5, 7)),
        McuFilm("Thor", LocalDate.of(2011, 5, 6)),
        McuFilm("Captain America: The First Avenger", LocalDate.of(2011, 7, 22)),
        McuFilm("The Avengers", LocalDate.of(2012, 5, 4)),
        McuFilm("Iron Man 3", LocalDate.of(2013, 5, 3)),
        McuFilm("Thor: The Dark World", LocalDate.of(2013, 11, 8)),
        McuFilm("Captain America: The Winter Soldier", LocalDate.of(2014, 4, 4)),
        McuFilm("Guardians of the Galaxy", LocalDate.of(2014, 8, 1)),
        McuFilm("Avengers: Age of Ultron", LocalDate.of(2015, 5, 1)),
        McuFilm("Ant-Man", LocalDate.of(2015, 7, 17)),
        McuFilm("Captain America: Civil War", LocalDate.of(2016, 5, 6)),
        McuFilm("Doctor Strange", LocalDate.of(2016, 11, 4)),
        McuFilm("Guardians of the Galaxy Vol. 2", LocalDate.of(2017, 5, 5)),
        McuFilm("Spider-Man: Homecoming", LocalDate.of(2017, 7, 7)),
        McuFilm("Thor: Ragnarok", LocalDate.of(2017, 11, 3)),
        McuFilm("Black Panther", LocalDate.of(2018, 2, 16)),
        McuFilm("Avengers: Infinity War", LocalDate.of(2018, 4, 27)),
        McuFilm("Ant-Man and the Wasp", LocalDate.of(2018, 7, 6)),
        McuFilm("Captain Marvel", LocalDate.of(2019, 3, 8)),
        McuFilm("Avengers: Endgame", LocalDate.of(2019, 4, 26)),
        McuFilm("Spider-Man: Far From Home", LocalDate.of(2019, 7, 2)),
        McuFilm("Black Widow", LocalDate.of(2021, 7, 9)),
        McuFilm("Shang-Chi and the Legend of the Ten Rings", LocalDate.of(2021, 9, 3)),
        McuFilm("Eternals", LocalDate.of(2021, 11, 5)),
        McuFilm("Spider-Man: No Way Home", LocalDate.of(2021, 12, 17)),
        McuFilm("Doctor Strange in the Multiverse of Madness", LocalDate.of(2022, 5, 6)),
        McuFilm("Thor: Love and Thunder", LocalDate.of(2022, 7, 8)),
        McuFilm("Black Panther: Wakanda Forever", LocalDate.of(2022, 11, 11)),
        McuFilm("Ant-Man and the Wasp: Quantumania", LocalDate.of(2023, 2, 17)),
        McuFilm("Guardians of the Galaxy Vol. 3", LocalDate.of(2023, 5, 5)),
        McuFilm("The Marvels", LocalDate.of(2023, 11, 10)),
        McuFilm("Deadpool & Wolverine", LocalDate.of(2024, 7, 26)),
        McuFilm("Captain America: Brave New World", LocalDate.of(2025, 2, 14)),
        McuFilm("Thunderbolts*", LocalDate.of(2025, 5, 2)),
        McuFilm("The Fantastic Four: First Steps", LocalDate.of(2025, 7, 25)),
    )

/** A film on the timeline, with the library item that owns it if the server has it. */
data class McuFilmTimeline(
    val film: McuFilm,
    val item: BaseItem?,
)

/** One date on the timeline and the films released on it, stacked above the date. */
data class McuTimelineEntry(
    val date: LocalDate,
    val films: List<McuFilmTimeline>,
) {
    val inLibrary: Boolean get() = films.any { it.item != null }
}

data class McuTimelineState(
    val entries: List<McuTimelineEntry> = buildEntries(emptyMap()),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

private val yearSuffix = Regex("\\b(19|20)\\d{2}$")
private val nonAlphanumeric = Regex("[^a-z0-9 ]")

/**
 * Titles are matched loosely: a library may call the 2012 film "The Avengers", "Marvel's The
 * Avengers" or "The Avengers (2012)". Punctuation, a leading "the"/"Marvel's" and a trailing
 * year all go, then "&" reads as "and".
 */
internal fun normalizeMcuTitle(title: String): String {
    var normalized =
        title
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(nonAlphanumeric, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    for (prefix in listOf("marvel studios ", "marvels ", "marvel s ", "the ")) {
        if (normalized.startsWith(prefix)) {
            normalized = normalized.removePrefix(prefix)
        }
    }
    return normalized.replace(yearSuffix, "").trim()
}

/**
 * Pair every MCU film with the library movie that is the same film.
 *
 * An exact normalized title wins. A film whose library copy is titled differently (a subtitle
 * added, a "Part" dropped) still matches when one title contains the other and the years are
 * within a year of each other, which is what release-date labels and regional titles do.
 */
internal fun matchMcuFilms(
    films: List<McuFilm>,
    library: List<BaseItem>,
): Map<String, BaseItem> {
    val byTitle = library.groupBy { normalizeMcuTitle(it.name.orEmpty()) }
    val claimed = mutableSetOf<UUID>()
    val matches = mutableMapOf<String, BaseItem>()

    for (film in films) {
        val key = normalizeMcuTitle(film.title)
        val exact =
            byTitle[key]
                ?.filterNot { it.id in claimed }
                ?.let { candidates ->
                    candidates.firstOrNull { item ->
                        item.data.productionYear?.let { abs(it - film.releaseDate.year) <= 1 } == true
                    } ?: candidates.firstOrNull()
                }
        val match =
            exact ?: library.firstOrNull { item ->
                if (item.id in claimed || key.length < 6) {
                    false
                } else {
                    val name = normalizeMcuTitle(item.name.orEmpty())
                    val sameYear =
                        item.data.productionYear?.let { abs(it - film.releaseDate.year) <= 1 } == true
                    sameYear && name.length >= 6 && (name.contains(key) || key.contains(name))
                }
            }
        if (match != null) {
            claimed.add(match.id)
            matches[film.title] = match
        }
    }
    return matches
}

private fun buildEntries(matches: Map<String, BaseItem>): List<McuTimelineEntry> =
    McuFilms
        .groupBy { it.releaseDate }
        .toSortedMap()
        .map { (date, films) ->
            McuTimelineEntry(
                date = date,
                films = films.map { McuFilmTimeline(it, matches[it.title]) },
            )
        }

/**
 * The card that stands in for the MCU collection in a movie library's Collections tab.
 *
 * It is an ordinary `BOX_SET` with a poster of its own, so it takes the same card, the same
 * sort and the same grid slot as every real collection beside it. Only its destination differs:
 * it opens the timeline rather than a collection page.
 */
fun mcuCollectionCard(): BaseItem =
    BaseItem(
        data =
            BaseItemDto(
                id = MCU_COLLECTION_ID,
                name = "MCU",
                type = BaseItemKind.BOX_SET,
                childCount = McuFilms.size,
            ),
        // An asset URI rather than a drawable resource: the card renders images from a URL, and
        // Coil resolves android_asset URIs without a resource lookup.
        imageUrlOverride = "file:///android_asset/mcu_poster.jpg",
        destinationOverride = Destination.Mcu,
    )

@HiltViewModel
class McuTimelineViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val navigationManager: NavigationManager,
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(McuTimelineState())
        val state: StateFlow<McuTimelineState> = _state

        init {
            load()
        }

        fun load() {
            viewModelScope.launchIO {
                _state.update { it.copy(loading = true, failed = false) }
                try {
                    val request =
                        GetItemsRequest(
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            enableUserData = true,
                            enableImages = true,
                            imageTypeLimit = 1,
                            enableImageTypes = listOf(ImageType.PRIMARY),
                            limit = 5000,
                            enableTotalRecordCount = false,
                        )
                    val library =
                        api.itemsApi
                            .getItems(request)
                            .content
                            .items
                            .orEmpty()
                            .map(::BaseItem)
                    val matches = matchMcuFilms(McuFilms, library)
                    _state.update {
                        McuTimelineState(
                            entries = buildEntries(matches),
                            loading = false,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error reading the movie library for the MCU timeline")
                    _state.update { it.copy(loading = false, failed = true) }
                }
            }
        }

        /** A film on the server plays immediately; one that is missing is looked up in Discover. */
        fun onSelect(entry: McuFilmTimeline) {
            val item = entry.item
            if (item != null) {
                navigationManager.navigateTo(Destination.Playback(item.id, item.resumeMs))
            } else {
                request(entry.film)
            }
        }

        fun onShowDetails(entry: McuFilmTimeline) {
            entry.item?.let { navigationManager.navigateTo(it.destination()) }
        }

        private fun request(film: McuFilm) {
            viewModelScope.launchIO {
                if (!seerrServerRepository.active.first()) {
                    showToast(context, context.getString(R.string.mcu_request_unavailable))
                    return@launchIO
                }
                try {
                    val key = normalizeMcuTitle(film.title)
                    val movies =
                        seerrService.search(film.title).filter { it.mediaType == "movie" }
                    val match =
                        movies.firstOrNull { result ->
                            normalizeMcuTitle(result.title ?: result.name.orEmpty()) == key &&
                                result.releaseDate?.take(4)?.toIntOrNull() == film.releaseDate.year
                        } ?: movies.firstOrNull { result ->
                            normalizeMcuTitle(result.title ?: result.name.orEmpty()) == key
                        }
                    if (match == null) {
                        showToast(
                            context,
                            context.getString(R.string.mcu_request_not_found, film.title),
                        )
                    } else {
                        navigationManager.navigateTo(
                            Destination.DiscoveredItem(seerrService.createDiscoverItem(match)),
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error looking up %s in Discover", film.title)
                    showToast(context, context.getString(R.string.mcu_request_failed))
                }
            }
        }
    }

/**
 * The MCU timeline: one column per release date, films stacked above the date, the dates joined
 * by a horizontal rule.
 */
@Composable
fun McuTimelinePage(
    modifier: Modifier = Modifier,
    viewModel: McuTimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(state.entries) {
        if (state.entries.isNotEmpty()) {
            firstFocus.tryRequestFocus()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        McuTimelineHeader(state)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 4.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusGroup(),
        ) {
            items(state.entries, key = { it.date.toString() }) { entry ->
                val first = entry === state.entries.firstOrNull()
                McuTimelineColumn(
                    entry = entry,
                    focusRequester = if (first) firstFocus else null,
                    onSelect = viewModel::onSelect,
                    onShowDetails = viewModel::onShowDetails,
                )
            }
        }
    }
}

@Composable
private fun McuTimelineHeader(
    state: McuTimelineState,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlass.current
    val total = remember(state.entries) { state.entries.sumOf { it.films.size } }
    val owned =
        remember(state.entries) { state.entries.sumOf { entry -> entry.films.count { it.item != null } } }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.mcu),
                style = if (glass) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    when {
                        state.failed -> stringResource(R.string.mcu_library_unavailable)
                        state.loading -> stringResource(R.string.mcu_checking)
                        else -> stringResource(R.string.mcu_summary, total, owned)
                    },
                style = if (glass) GlassType.meta else MaterialTheme.typography.labelMedium,
                color = if (glass) Color.White.copy(alpha = 0.66f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun McuTimelineColumn(
    entry: McuTimelineEntry,
    focusRequester: FocusRequester?,
    onSelect: (McuFilmTimeline) -> Unit,
    onShowDetails: (McuFilmTimeline) -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val rule = if (glass) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val quiet = if (glass) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    // Two films on one date need the column to give its posters less height so the stack still
    // fits above the date.
    val posterWidth = if (entry.films.size > 1) 118.dp else 148.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier =
            Modifier
                .fillMaxHeight()
                .width(McuColumnWidth),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 18.dp),
        ) {
            entry.films.forEachIndexed { index, film ->
                McuFilmCard(
                    film = film,
                    width = posterWidth,
                    focusRequester = if (index == 0) focusRequester else null,
                    onSelect = { onSelect(film) },
                    onShowDetails = { onShowDetails(film) },
                )
            }
        }
        // The timeline itself: a rule through every column, with this date's dot on it. The
        // posters sit above it and the date reads below, so the row is a calendar rather than a
        // carousel of cards.
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp),
        ) {
            Box(Modifier.fillMaxSize().background(rule))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(if (entry.inLibrary) 9.dp else 7.dp)
                    .background(if (entry.inLibrary) McuRed else rule, CircleShape),
            )
        }
        Text(
            text = entry.date.format(MdateFormatter).uppercase(Locale.getDefault()),
            style = if (glass) GlassType.meta else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.inLibrary) McuRed else quiet,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = entry.date.year.toString(),
            style = if (glass) GlassType.meta else MaterialTheme.typography.labelSmall,
            color = quiet,
            maxLines = 1,
        )
    }
}

@Composable
private fun McuFilmCard(
    film: McuFilmTimeline,
    width: Dp,
    focusRequester: FocusRequester?,
    onSelect: () -> Unit,
    onShowDetails: () -> Unit,
) {
    val item = film.item
    val dto = item?.data
    val glass = LocalLiquidGlass.current
    val imageUrlService = LocalImageUrlService.current
    val imageUrl =
        remember(item) {
            item?.let {
                imageUrlService.getItemImageUrl(
                    it,
                    ImageType.PRIMARY,
                    fillWidth = 320,
                    fillHeight = 480,
                )
            }
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.width(width),
    ) {
        Card(
            onClick = onSelect,
            onLongClick = onShowDetails,
            shape = glassCardShape(),
            border = glassCardBorder(),
            glow = glassCardGlow(),
            scale = glassCardScale(),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        ) {
            ItemCardImage(
                imageUrl = imageUrl,
                name = film.film.title,
                showOverlay = true,
                favorite = dto?.userData?.isFavorite ?: false,
                watched = dto?.userData?.played ?: false,
                unwatchedCount = -1,
                watchedPercent = dto?.userData?.playedPercentage,
                numberOfVersions = 0,
                useFallbackText = false,
                contentScale = ContentScale.FillBounds,
                fallback = {
                    if (item == null) {
                        McuMissingPoster(film.film.title)
                    } else {
                        // A film the library owns but has no poster for: show the usual fallback
                        // rather than the request badge, which would be a lie.
                        ItemCardImageFallback(name = film.film.title, useFallbackText = true)
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(AspectRatios.TALL)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(
            text = film.film.title,
            // Two lines always: a one-line title would lift its poster above the rest of the row.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (glass) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The poster of a film the library does not have: the title, and where to ask for it. */
@Composable
private fun McuMissingPoster(title: String) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2C1114), Color(0xFF120A0C)),
                    ),
                ),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp, vertical = 24.dp),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .background(McuRed, RoundedCornerShape(percent = 50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = stringResource(R.string.mcu_request),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Width of one date on the timeline. */
private val McuColumnWidth: Dp = 176.dp
