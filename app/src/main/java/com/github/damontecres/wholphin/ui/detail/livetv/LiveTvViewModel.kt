package com.github.damontecres.wholphin.ui.detail.livetv

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.LiveTvCache
import com.github.damontecres.wholphin.services.LiveTvCachePolicy
import com.github.damontecres.wholphin.services.LiveTvReminderWorker
import com.github.damontecres.wholphin.services.LiveTvService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.formatSeasonEpisode
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

const val MAX_HOURS = 48L
private const val STARTING_SOON_HOURS = 3L
private const val STARTING_SOON_LIMIT = 240

/** How far ahead the Sports page looks. A week of fixtures is a calendar; the guide is 48 hours. */
private const val SPORT_DAYS = 7L
private const val SPORT_FIXTURE_LIMIT = 300
private const val MAX_RECENT_CHANNELS = 8

@OptIn(FlowPreview::class)
@HiltViewModel
class LiveTvViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val preferences: DataStore<AppPreferences>,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val liveTvService: LiveTvService,
        private val liveTvCache: LiveTvCache,
    ) : ViewModel() {
        private lateinit var channelsIdToIndex: Map<UUID, Int>
        private val mutex = Mutex()

        private val _state = MutableStateFlow(LiveTvState())
        val state: StateFlow<LiveTvState> = _state

        private val _programDialogState = MutableStateFlow(ProgramDialogState())
        val programDialogState: StateFlow<ProgramDialogState> = _programDialogState

        // "Watch last" and "Recent channels" are per device and per profile. They are deliberately
        // small and local: a tuner should remember where the viewer was without making another
        // server round trip on every launch.
        private val channelPrefs = context.getSharedPreferences("live_tv_channels", Context.MODE_PRIVATE)
        private val channelPrefsKey: String
            get() = "user_${serverRepository.currentUser?.id ?: "default"}"

        private val _lastChannelId = MutableStateFlow(readLastChannelId())
        val lastChannelId: StateFlow<UUID?> = _lastChannelId

        private val _recentChannelIds = MutableStateFlow(readRecentChannelIds())
        val recentChannelIds: StateFlow<List<UUID>> = _recentChannelIds

        private val _remindedProgramIds = MutableStateFlow(readRemindedProgramIds())
        val remindedProgramIds: StateFlow<Set<UUID>> = _remindedProgramIds

        private val range = 100

        init {
            viewModelScope.launchDefault {
                preferences.data
                    .map {
                        it.interfacePreferences.liveTvPreferences.let {
                            Pair(it.sortByRecentlyWatched, it.favoriteChannelsAtBeginning)
                        }
                    }.distinctUntilChanged()
                    // The debounce exists for a preference flip arriving while the viewer is in
                    // Settings. It was also delaying the *first* emission, so every visit to Live
                    // TV or Sports sat on a spinner for half a second before the first request
                    // even left the app. Index 0 is the page opening: load it immediately.
                    .withIndex()
                    .debounce { indexed ->
                        if (indexed.index == 0) Duration.ZERO else 500.milliseconds
                    }.collectLatest { indexed ->
                        val settings = indexed.value
                        Timber.v("Init due to pref change")
                        // Live TV and Sports are separate entries with a ViewModel each, so the
                        // second page to open used to ask for the same 48 hours of guide again.
                        // A recent snapshot is painted at once; a fresh, whole one is used
                        // instead of the request entirely. A preference flip is a new sort order,
                        // so it always fetches.
                        val cached = liveTvCache.get(serverRepository.currentUser?.id)
                        if (cached != null) {
                            _state.value = cached.state.copy(refreshing = false)
                            val age = cached.ageMs(System.currentTimeMillis())
                            if (indexed.index == 0 &&
                                cached.complete &&
                                LiveTvCachePolicy.isFresh(age)
                            ) {
                                Timber.v("Live TV guide reused from cache, age=%sms", age)
                                return@collectLatest
                            }
                        } else {
                            _state.update { LiveTvState() }
                        }
                        init(settings.first, settings.second)
                    }
            }
        }

        private fun init(
            sortByRecentlyWatched: Boolean,
            favoriteChannelsAtBeginning: Boolean,
        ) {
            viewModelScope.launchDefault {
                try {
                    val guideTimes = buildGuideTimes()
                    _state.update { it.copy(guideTimes = guideTimes) }
                    val guideStart = guideTimes.first()
                    val channelData by api.liveTvApi.getLiveTvChannels(
                        GetLiveTvChannelsRequest(
                            startIndex = 0,
                            userId = serverRepository.currentUser?.id,
                            enableFavoriteSorting = favoriteChannelsAtBeginning,
                            sortBy =
                                if (sortByRecentlyWatched) {
                                    listOf(ItemSortBy.DATE_PLAYED)
                                } else {
                                    null
                                },
                            sortOrder =
                                if (sortByRecentlyWatched) {
                                    SortOrder.DESCENDING
                                } else {
                                    null
                                },
                            // The current programme is included on the channel itself. That is what
                            // makes the header count and the Now board complete for a tuner with
                            // hundreds of channels without loading 48 hours of guide data for all of
                            // them before the first frame.
                            addCurrentProgram = true,
                        ),
                    )
                    val channels =
                        channelData.items
                            .map {
                                TvChannel(
                                    id = it.id,
                                    number = it.channelNumber,
                                    name = it.channelName ?: it.name,
                                    imageUrl =
                                        imageUrlService.getItemImageUrl(
                                            itemId = it.id,
                                            imageType = ImageType.PRIMARY,
                                            tag = it.imageTags?.get(ImageType.PRIMARY),
                                        ),
                                    favorite = it.userData?.isFavorite == true,
                                )
                            }
                    val currentPrograms =
                        channelData.items
                            .mapNotNull { dto ->
                                dto.currentProgram?.toTvProgram(guideStart)?.let { program ->
                                    dto.id to program
                                }
                            }.toMap()
                    Timber.d("Got ${channels.size} channels")
                    channelsIdToIndex =
                        channels.withIndex().associateBy({ it.value.id }, { it.index })
                    // Initially, quickly load the first 10 channels (only some are visible immediately), then below will load more
                    // This makes the guide appear faster, and load more usable data in the background
                    val initial = 10
                    fetchPrograms(guideStart, channels, 0..<initial.coerceAtMost(channels.size))

                    _state.update {
                        it.copy(
                            channels = channels,
                            currentPrograms = currentPrograms,
                            lastUpdated = Instant.now(),
                            loading = LoadingState.Success,
                        )
                    }
                    // Drawable, but not yet whole: the full guide is still loading below.
                    liveTvCache.put(serverRepository.currentUser?.id, state.value)
                    // The Sports page asks for the games, not the schedule. This runs alongside
                    // the guide so the tab has fixtures while the grid is still arriving.
                    viewModelScope.launchDefault { fetchSportFixtures(guideStart) }
                    fetchStartingSoon(guideStart)
                    // Now load the full range
                    if (channels.size > initial) {
                        fetchPrograms(guideStart, channels, 0..<range.coerceAtMost(channels.size))
                    }
                    // A tuner can carry more than one page of channels. The guide used to stop at
                    // the first hundred, leaving the rest as channel names with no listings. Load
                    // the remaining pages in the background, one page at a time, and merge them as
                    // they arrive.
                    if (channels.size > range) {
                        for (start in range..<channels.size step range) {
                            val end = (start + range).coerceAtMost(channels.size)
                            fetchPrograms(guideStart, channels, start..<end)
                        }
                    }
                    // Only now is the guide whole: earlier it had listings for the first ten
                    // channels and filler for the rest, which must never skip a fetch.
                    liveTvCache.put(serverRepository.currentUser?.id, state.value, complete = true)
                } catch (ex: Exception) {
                    Timber.e(ex, "Erroring during init")
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        /**
         * Creates a list of [LocalDateTime] for each half hour from now.
         *
         * A guide ruler is a time axis. Starting with one half-hour cell and then switching to
         * whole hours made 6:30 disappear from a 5:30 schedule: the labels were right, but the
         * ruler jumped from a 30-minute column to 60-minute columns and the half-hour tick marks
         * no longer lined up with the programme blocks. Half-hour columns throughout are what
         * the reference EPG draws, and what two-hour sports fixtures need in order to read
         * correctly.
         */
        private fun buildGuideTimes() =
            buildList {
                val start = LocalDateTime.now().roundDownToHalfHour()
                repeat(MAX_HOURS.toInt() * 2) {
                    add(start.plusMinutes(it * 30L))
                }
            }

        /**
         * This week's games, from the server's own sport filter.
         *
         * Deliberately not `fetchPrograms`: that asks for every programme on up to a hundred
         * channels over 48 hours to find a dozen matches. Everything that is still airing or has
         * not started is sport, and the page shows a week of it.
         */
        private suspend fun fetchSportFixtures(guideStart: LocalDateTime) {
            try {
                val now = LocalDateTime.now()
                val result =
                    api.liveTvApi.getPrograms(
                        GetProgramsDto(
                            userId = serverRepository.currentUser?.id,
                            isSports = true,
                            minEndDate = now.plusMinutes(1),
                            maxStartDate = now.plusDays(SPORT_DAYS),
                            sortBy = listOf(ItemSortBy.START_DATE),
                            fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                            enableImages = true,
                            imageTypeLimit = 1,
                            limit = SPORT_FIXTURE_LIMIT,
                        ),
                    ).content.items
                val fixtures =
                    result
                        .mapNotNull { dto -> dto.toTvProgram(guideStart) }
                        .filter { it.category == ProgramCategory.SPORTS && !it.isFake }
                        .sortedBy { it.start }
                Timber.d("Got %s sport fixtures", fixtures.size)
                _state.update { it.copy(sportFixtures = fixtures) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // A failed sport query costs the faster list, not the page: the guide fallback is
                // already loaded by the time the viewer can be looking at it.
                Timber.w(ex, "Could not load sport fixtures")
            }
        }

        /**
         * The next few hours across every channel. This is separate from the 48-hour guide: the
         * Now board needs one cheap answer for all channels, not a full schedule for each one.
         */
        private suspend fun fetchStartingSoon(guideStart: LocalDateTime) {
            try {
                val now = LocalDateTime.now()
                val result =
                    api.liveTvApi.getPrograms(
                        GetProgramsDto(
                            userId = serverRepository.currentUser?.id,
                            minStartDate = now,
                            maxStartDate = now.plusHours(STARTING_SOON_HOURS),
                            minEndDate = now.plusMinutes(1),
                            sortBy = listOf(ItemSortBy.START_DATE),
                            fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                            enableImages = true,
                            imageTypeLimit = 1,
                            limit = STARTING_SOON_LIMIT,
                        ),
                    ).content.items
                val programs =
                    result.mapNotNull { dto -> dto.toTvProgram(guideStart) }
                _state.update { it.copy(startingSoon = programs) }
            } catch (ex: Exception) {
                // The board is still useful from the guide already loaded. A failed secondary
                // query must not turn Live TV into an error page.
                Timber.w(ex, "Could not load starting soon programmes")
            }
        }

        /**
         * Re-fetch the guide the viewer is looking at, keeping the current page on screen while
         * the request runs. This is the sheet's "Refresh guide" action, so it must actually fetch
         * programmes rather than only the current-programme strip.
         */
        fun refreshGuide() {
            viewModelScope.launchDefault {
                val snapshot = state.value
                if (snapshot.channels.isEmpty() || snapshot.guideTimes.isEmpty()) return@launchDefault
                _state.update { it.copy(refreshing = true) }
                try {
                    val guideStart = snapshot.guideTimes.first()
                    fetchPrograms(guideStart, snapshot.channels, 0..<snapshot.channels.size)
                    fetchStartingSoon(guideStart)
                    _state.update {
                        it.copy(refreshing = false, lastUpdated = Instant.now())
                    }
                    liveTvCache.put(serverRepository.currentUser?.id, state.value, complete = true)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Could not refresh the guide")
                    _state.update { it.copy(refreshing = false) }
                    showToast(context, context.getString(R.string.live_tv_channels_error))
                }
            }
        }

        /** Refresh the live strip without throwing away the guide the viewer is reading. */
        fun refreshLiveData() {
            viewModelScope.launchDefault {
                val guideStart = state.value.guideTimes.firstOrNull() ?: return@launchDefault
                try {
                    val channelData by api.liveTvApi.getLiveTvChannels(
                        GetLiveTvChannelsRequest(
                            userId = serverRepository.currentUser?.id,
                            addCurrentProgram = true,
                            enableFavoriteSorting = true,
                        ),
                    )
                    val currentPrograms =
                        channelData.items
                            .mapNotNull { dto ->
                                dto.currentProgram?.toTvProgram(guideStart)?.let { program ->
                                    dto.id to program
                                }
                            }.toMap()
                    _state.update { it.copy(currentPrograms = currentPrograms) }
                    fetchStartingSoon(guideStart)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not refresh current programmes")
                }
            }
        }

        private fun readLastChannelId(): UUID? =
            channelPrefs.getString("${channelPrefsKey}_last", null)?.let { raw ->
                runCatching { UUID.fromString(raw) }.getOrNull()
            }

        private fun readRecentChannelIds(): List<UUID> =
            channelPrefs
                .getString("${channelPrefsKey}_recent", null)
                ?.split(',')
                ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                .orEmpty()

        private fun readRemindedProgramIds(): Set<UUID> =
            channelPrefs
                .getString("${channelPrefsKey}_reminders", null)
                ?.split(',')
                ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                ?.toSet()
                .orEmpty()

        private fun rememberChannel(channel: TvChannel) {
            _lastChannelId.value = channel.id
            val recent = (_recentChannelIds.value - channel.id).toMutableList()
            recent.add(0, channel.id)
            _recentChannelIds.value = recent.take(MAX_RECENT_CHANNELS)
            channelPrefs
                .edit()
                .putString("${channelPrefsKey}_last", channel.id.toString())
                .putString(
                    "${channelPrefsKey}_recent",
                    _recentChannelIds.value.joinToString(","),
                ).apply()
        }

        /** The one path from any Live TV surface to a channel. */
        fun tuneChannel(channel: TvChannel) {
            rememberChannel(channel)
            navigationManager.navigateTo(
                Destination.Playback(itemId = channel.id, positionMs = 0L),
            )
        }

        /**
         * Toggle a reminder and make it real in both directions: the set is persisted for this
         * profile, and WorkManager holds one notification job per programme. Without the job the
         * button would only change its own label; without the store it would forget the reminder
         * as soon as the app was closed.
         */
        fun toggleReminded(
            program: TvProgram,
            channelName: String?,
        ): Boolean {
            val currently = program.id in _remindedProgramIds.value
            _remindedProgramIds.value =
                if (currently) {
                    _remindedProgramIds.value - program.id
                } else {
                    _remindedProgramIds.value + program.id
                }
            channelPrefs
                .edit()
                .putString(
                    "${channelPrefsKey}_reminders",
                    _remindedProgramIds.value.joinToString(","),
                ).apply()

            val workName = "live_tv_reminder_${program.id}"
            val workManager = WorkManager.getInstance(context)
            if (currently) {
                workManager.cancelUniqueWork(workName)
            } else {
                val delay =
                    java.time.Duration
                        .between(LocalDateTime.now(), program.start)
                        .toMillis()
                        .coerceAtLeast(0L)
                val request =
                    OneTimeWorkRequestBuilder<LiveTvReminderWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(
                            workDataOf(
                                LiveTvReminderWorker.KEY_TITLE to program.name.orEmpty(),
                                LiveTvReminderWorker.KEY_CHANNEL to channelName.orEmpty(),
                            ),
                        ).build()
                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
            }
            return !currently
        }

        private suspend fun refreshPrograms(
            channels: List<TvChannel>,
            range: IntRange,
        ) {
            _state.update { it.copy(refreshing = true) }
            val guideStart = _state.value.guideTimes.first()
            try {
                fetchPrograms(guideStart, channels, range)
                _state.update { it.copy(refreshing = false) }
            } catch (_: CancellationException) {
                // no-op
            } catch (ex: Exception) {
                Timber.e(ex, "Error fetching programs")
                _state.update { it.copy(loading = LoadingState.Error(ex)) }
            }
        }

        /**
         * Get live TV programs for a subset of channels
         *
         * @param guideStart The start timestamp to fetch from
         * @param channels The full list of channels
         * @param channelIndices The indices of which channels to fetch programs for
         */
        private suspend fun fetchPrograms(
            guideStart: LocalDateTime,
            channels: List<TvChannel>,
            channelIndices: IntRange,
        ) = mutex.withLock {
            val maxStartDate = guideStart.plusHours(MAX_HOURS).minusMinutes(1)
            val minEndDate = guideStart.plusMinutes(1L)
            val channelsToFetch = channels.subList(channelIndices.first, channelIndices.last + 1)
            Timber.v("Fetching programs for $channelIndices channels ${channelsToFetch.size}")
            val request =
                GetProgramsDto(
                    maxStartDate = maxStartDate,
                    minEndDate = minEndDate,
                    channelIds = channelsToFetch.map { it.id },
                    sortBy = listOf(ItemSortBy.START_DATE),
                    userId = serverRepository.currentUser?.id,
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                )
            val fetchedPrograms =
                api.liveTvApi
                    .getPrograms(request)
                    .content.items
                    .filter { it.endDate?.isAfter(guideStart) == true }
            val programsByChannel = mutableMapOf<UUID, List<TvProgram>>()
            val fetchedGroupedBy = fetchedPrograms.groupBy { it.channelId }
            fetchedGroupedBy.forEach { (channelId, programs) ->
                if (channelId != null) {
                    val listing = mutableListOf<TvProgram>()
                    programs as MutableList<BaseItemDto>
                    programs.forEachIndexed { index, dto ->
                        val category =
                            if (dto.isKids ?: false) {
                                ProgramCategory.KIDS
                            } else if (dto.isMovie ?: false) {
                                ProgramCategory.MOVIE
                            } else if (dto.isNews ?: false) {
                                ProgramCategory.NEWS
                            } else if (isSportsProgram(dto)) {
                                ProgramCategory.SPORTS
                            } else {
                                null
                            }
                        // Clean up name/subtitles by collapsing whitespace (including newlines) into single spaces
                        val name = (dto.seriesName ?: dto.name)?.replace(Regex("\\s+"), " ")
                        val subtitle =
                            dto.episodeTitle
                                .takeIf { dto.isSeries ?: false }
                                ?.replace(Regex("\\s+"), " ")
                        val p =
                            TvProgram(
                                id = dto.id,
                                channelId = dto.channelId!!,
                                start = dto.startDate!!,
                                end = dto.endDate!!,
                                startHours =
                                    hoursBetween(
                                        guideStart,
                                        dto.startDate!!,
                                    ).coerceAtLeast(0f),
                                endHours = hoursBetween(guideStart, dto.endDate!!),
                                duration = dto.runTimeTicks!!.ticks,
                                name = name,
                                subtitle = subtitle,
                                overview = dto.overview,
                                officialRating = dto.officialRating,
                                genres = dto.genres.orEmpty(),
                                productionYear = dto.productionYear,
                                seasonEpisode =
                                    if (dto.indexNumber != null && dto.parentIndexNumber != null) {
                                        SeasonEpisode(
                                            dto.parentIndexNumber!!,
                                            dto.indexNumber!!,
                                        )
                                    } else {
                                        null
                                    },
                                isRecording = dto.timerId.isNotNullOrBlank(),
                                isSeriesRecording = dto.seriesTimerId.isNotNullOrBlank(),
                                isRepeat = dto.isRepeat ?: false,
                                category = category,
                                imageUrl =
                                    imageUrlService.getItemImageUrl(
                                        dto.id,
                                        ImageType.PRIMARY,
                                        tag = dto.imageTags?.get(ImageType.PRIMARY),
                                    ),
                            )
                        if (index == 0) {
                            if (p.startHours > 0) {
                                // Fill out before the first program
                                var start = 0f
                                var end = min(start + 1f, p.startHours)
                                while (start < p.startHours) {
                                    val fake =
                                        TvProgram.fake(
                                            guideStart,
                                            channelId,
                                            start,
                                            end,
                                        )
                                    start = end
                                    end = min(start + 1f, p.startHours)
                                    listing.add(fake)
                                }
                            }
                            listing.add(p)
                        } else if (index > 0 && listing.isNotEmpty()) {
                            var previous = listing.last()
                            while (previous.endHours < p.startHours) {
                                // Fill gaps between programs
                                val start = previous.endHours
                                val duration = (p.startHours - start).coerceAtMost(1f)
//                                Timber.v("Adding fake for $channelId: $start=>${start + duration}")
                                previous =
                                    TvProgram(
                                        id = UUID.randomUUID(),
                                        channelId = channelId,
                                        start = previous.end,
                                        end = previous.end.plusMinutes((duration * 60).toLong()),
                                        startHours = start,
                                        endHours = start + duration,
                                        duration = (duration * 60).toInt().minutes,
                                        name = context.getString(R.string.no_data),
                                        subtitle = null,
                                        seasonEpisode = null,
                                        isRecording = false,
                                        isSeriesRecording = false,
                                        isRepeat = false,
                                        category = ProgramCategory.FAKE,
                                    )
                                listing.add(previous)
                            }
                            listing.add(p)
                        }
                        if (index == (programs.size - 1)) {
                            if (p.endHours < MAX_HOURS) {
                                // Fill after the last program
                                val end = (p.endHours + 1).toInt()
                                listing.add(
                                    TvProgram.fake(
                                        guideStart,
                                        channelId,
                                        p.endHours,
                                        end.toFloat(),
                                    ),
                                )
                                (end..<MAX_HOURS).forEach {
                                    listing.add(
                                        TvProgram.fake(
                                            guideStart,
                                            channelId,
                                            it.toFloat(),
                                            it + 1f,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    programsByChannel[channelId] = listing
                }
            }
            val emptyChannels =
                channelsToFetch.filter { programsByChannel[it.id].orEmpty().isEmpty() }
            val fake = mutableListOf<TvProgram>()

            emptyChannels.forEach { channel ->
                val fakePrograms =
                    (0..<MAX_HOURS).map {
                        TvProgram(
                            id = UUID.randomUUID(),
                            channelId = channel.id,
                            start = guideStart.plusHours(it),
                            end = guideStart.plusHours(it + 1),
                            startHours = it.toFloat(),
                            endHours = (it + 1).toFloat(),
                            duration = 60.seconds,
                            name = context.getString(R.string.no_data),
                            subtitle = null,
                            seasonEpisode = null,
                            isRecording = false,
                            isSeriesRecording = false,
                            isRepeat = false,
                            category = ProgramCategory.FAKE,
                        )
                    }
                programsByChannel[channel.id] = fakePrograms
                fake.addAll(fakePrograms)
            }

            val channelProgramCount = programsByChannel.map { it.key to it.value.size }.toMap()

            // Merge this page with what is already loaded. The first ten channels are fetched for
            // the first frame, then the rest of the tuner in pages; replacing the map on every
            // page made the guide lose every channel that was not in the latest request.
            val previous = _state.value.programs
            val mergedByChannel = LinkedHashMap(previous.programsByChannel).apply { putAll(programsByChannel) }
            val finalProgramList =
                mergedByChannel
                    .values
                    .flatten()
                    .sortedWith(
                        compareBy(
                            { channelsIdToIndex[it.channelId] ?: Int.MAX_VALUE },
                            { it.start },
                        ),
                    )
            val mergedRange =
                minOf(previous.range.first, channelIndices.first)..
                    maxOf(previous.range.last, channelIndices.last)
            Timber.d("Got ${fetchedPrograms.size} programs & ${finalProgramList.size} total programs")
            _state.update {
                it.copy(
                    channelProgramCount = it.channelProgramCount + channelProgramCount,
                    programs =
                        FetchedPrograms(
                            mergedRange,
                            finalProgramList,
                            mergedByChannel,
                        ),
                    lastUpdated = Instant.now(),
                )
            }
        }

        /**
         * Whether a programme is sport.
         *
         * Jellyfin fills `IsSports` for most providers, but an XMLTV tuner can carry the fact
         * only as a category (`<category>sports</category>`, which Jellysports writes on every
         * fixture). Reading both is what lets the Sports tab find the games without a provider
         * specific service or a second request.
         */
        private fun isSportsProgram(dto: BaseItemDto): Boolean =
            dto.isSports == true ||
                dto.genres.orEmpty().any { it.trim().equals("sports", ignoreCase = true) } ||
                dto.tags.orEmpty().any { it.trim().equals("sports", ignoreCase = true) }

        /**
         * Convert a programme DTO from the channel/current-programme or starting-soon endpoints
         * into the same shape the guide uses. Keeping one mapping is what lets the Now board and
         * the guide agree about titles, categories and progress.
         */
        private fun BaseItemDto.toTvProgram(guideStart: LocalDateTime): TvProgram? {
            val channel = channelId ?: return null
            val start = startDate ?: return null
            val end = endDate ?: return null
            val category =
                when {
                    isKids == true -> ProgramCategory.KIDS
                    isMovie == true -> ProgramCategory.MOVIE
                    isNews == true -> ProgramCategory.NEWS
                    isSportsProgram(this) -> ProgramCategory.SPORTS
                    else -> null
                }
            val cleanedName = (seriesName ?: name)?.replace(Regex("\\s+"), " ")
            val cleanedSubtitle =
                episodeTitle
                    .takeIf { isSeries == true }
                    ?.replace(Regex("\\s+"), " ")
            return TvProgram(
                id = id,
                channelId = channel,
                start = start,
                end = end,
                startHours = hoursBetween(guideStart, start).coerceAtLeast(0f),
                endHours = hoursBetween(guideStart, end).coerceAtLeast(0f),
                duration =
                    runTimeTicks?.ticks
                        ?: java.time.Duration.between(start, end).toKotlinDuration(),
                name = cleanedName,
                subtitle = cleanedSubtitle,
                overview = overview,
                officialRating = officialRating,
                genres = genres.orEmpty(),
                productionYear = productionYear,
                seasonEpisode =
                    if (indexNumber != null && parentIndexNumber != null) {
                        SeasonEpisode(parentIndexNumber!!, indexNumber!!)
                    } else {
                        null
                    },
                isRecording = timerId.isNotNullOrBlank(),
                isSeriesRecording = seriesTimerId.isNotNullOrBlank(),
                isRepeat = isRepeat ?: false,
                category = category,
                imageUrl =
                    imageUrlService.getItemImageUrl(
                        id,
                        ImageType.PRIMARY,
                        tag = imageTags?.get(ImageType.PRIMARY),
                    ),
            )
        }

        fun fetchProgramForDialog(programId: UUID) {
            _programDialogState.update { it.copy(loading = DataLoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val result = liveTvService.fetchProgramForDialog(programId)
                    _programDialogState.update { it.copy(loading = DataLoadingState.Success(result)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching program $programId")
                    _programDialogState.update { it.copy(loading = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                try {
                    val result = liveTvService.cancelRecording(series, timerId)
                    if (result) {
                        state.value.let {
                            refreshPrograms(it.channels, it.programs.range)
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error canceling timer %s, series=%s", timerId, series)
                    showToast(context, "Error: ${ex.localizedMessage}")
                }
            }
        }

        fun record(
            programId: UUID,
            series: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    liveTvService.record(programId, series)
                    state.value.let {
                        refreshPrograms(it.channels, it.programs.range)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error recording %s, series=%s", programId, series)
                    showToast(context, "Error: ${ex.localizedMessage}")
                }
            }
        }

        private var focusLoadingJob: Job? = null

        /**
         * Callback when focusing on the EPG grid
         *
         * This determines if more programs/channels should be fetched based on the current position
         */
        fun onFocusChannel(position: RowColumn) {
            val state = state.value
            val channels = state.channels
            val fetchedRange = state.programs.range
            val quarter = range / 4
            var rangeStart = fetchedRange.start + quarter
            var rangeEnd = fetchedRange.last - quarter

            if (rangeEnd - rangeStart < range) {
                if (position.row < range / 2) {
                    // Close to beginning
                    rangeStart = 0
                } else if (position.row > (channels.size - range / 2)) {
                    // Close to the end
                    rangeEnd = channels.size
                }
            }
            val testRange = rangeStart..<rangeEnd

            Timber.v(
                "onFocusChannel: position=%s, fetchedRange=%s, testRange=%s",
                position,
                fetchedRange,
                testRange,
            )

            val fetchStart = (position.row - range).coerceAtLeast(0)
            val fetchEnd = (position.row + range).coerceAtMost(channels.size)
            val newFetchRange = fetchStart..<fetchEnd
            // If current channel  is not within +/- range
            // And the potential new fetch range is not wholly within the current (eg not near the top or bottom)
            // Fetch new data
            if (position.row !in testRange && !newFetchRange.within(fetchedRange)) {
                Timber.v("Loading more programs for channels $newFetchRange")
                focusLoadingJob?.cancel()
                focusLoadingJob =
                    viewModelScope.launchIO {
                        refreshPrograms(channels, newFetchRange)
                    }
            }
        }

        fun toggleFavorite(
            index: Int,
            channel: TvChannel,
        ) {
            viewModelScope.launchIO {
                favoriteWatchManager.setFavorite(channel.id, !channel.favorite)
                _state.update {
                    it.copy(
                        channels =
                            it.channels.toMutableList().apply {
                                set(index, channel.copy(favorite = !channel.favorite))
                            },
                    )
                }
            }
        }
    }

fun IntRange.within(other: IntRange): Boolean = this.first >= other.first && this.last <= other.last

/**
 * Returns the number of hours between two [LocalDateTime]
 */
fun hoursBetween(
    start: LocalDateTime,
    target: LocalDateTime,
): Float =
    java.time.Duration
        .between(start, target)
        .seconds / (60f * 60f)

data class LiveTvState(
    val loading: LoadingState = LoadingState.Pending,
    val refreshing: Boolean = false,
    val guideTimes: List<LocalDateTime> = emptyList(),
    val channels: List<TvChannel> = emptyList(),
    val channelProgramCount: Map<UUID, Int> = emptyMap(),
    val programs: FetchedPrograms = FetchedPrograms(),
    /** Current programme for every channel, populated by the channels endpoint. */
    val currentPrograms: Map<UUID, TvProgram> = emptyMap(),
    /** The next three hours across every channel, for the Starting Soon rail. */
    val startingSoon: List<TvProgram> = emptyList(),
    /**
     * The games themselves, from the targeted sport query.
     *
     * The Sports page used to wait for the whole 48-hour guide — thousands of programmes for every
     * channel — just to filter sport out of it. A week of fixtures is a few hundred rows, so it
     * lands long before the guide does and the page draws from it.
     */
    val sportFixtures: List<TvProgram> = emptyList(),
    val lastUpdated: Instant? = null,
)

/**
 * The sport on this guide.
 *
 * The targeted query wins when it has an answer; the guide stays the fallback for a tuner whose
 * provider marks sport only in a way the server's filter does not match.
 */
val LiveTvState.sportPrograms: List<TvProgram>
    get() =
        (sportFixtures.ifEmpty { programs.programs })
            .filter { it.category == ProgramCategory.SPORTS && !it.isFake }
            .sortedBy { it.start }

data class ProgramDialogState(
    val loading: DataLoadingState<BaseItem> = DataLoadingState.Pending,
)

data class TvChannel(
    val id: UUID,
    val number: String?,
    val name: String?,
    val imageUrl: String?,
    val favorite: Boolean,
)

@Stable
data class TvProgram(
    val id: UUID,
    val channelId: UUID,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val startHours: Float,
    val endHours: Float,
    val duration: Duration,
    val name: String?,
    val subtitle: String?,
    val overview: String? = null,
    val seasonEpisode: SeasonEpisode?,
    val isRecording: Boolean,
    val isSeriesRecording: Boolean,
    val isRepeat: Boolean,
    val category: ProgramCategory?,
    val officialRating: String? = null,
    /** The tuner's genre, used as the masthead's eyebrow when it supplies one. */
    val genres: List<String> = emptyList(),
    /** The tuner's year, used by the guide's programme titles like a printed EPG. */
    val productionYear: Int? = null,
    val imageUrl: String? = null,
) {
    val isFake = category == ProgramCategory.FAKE

    val quickDetails by lazy {
        val now = LocalDateTime.now()
        buildAnnotatedString {
            val differentDay = start.toLocalDate() != now.toLocalDate()
            val time =
                DateUtils.formatDateRange(
                    WholphinApplication.instance,
                    start
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .epochSecond * 1000,
                    end
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .epochSecond * 1000,
                    DateUtils.FORMAT_SHOW_TIME or if (differentDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0,
                )
            append(time)

            if (!isFake) {
                val resources = WholphinApplication.instance.resources
                dot()
                append(resources.formatDuration(duration.roundMinutes))
                if (now.isAfter(start) && now.isBefore(end)) {
                    dot()
                    java.time.Duration
                        .between(now, end)
                        .toKotlinDuration()
                        .roundMinutes
                        .let { append(resources.getString(R.string.time_left, resources.formatDuration(it))) }
                }
                formatSeasonEpisode(seasonEpisode?.season, seasonEpisode?.episode)?.let {
                    dot()
                    append(it)
                }
                officialRating?.let {
                    dot()
                    append(it)
                }
            }
        }
    }

    companion object {
        // Resolved on demand: a class-level read made constructing a TvProgram require a running
        // Application, which put the whole guide model out of reach of plain unit tests.
        private val NO_DATA: String
            get() = WholphinApplication.instance.getString(R.string.no_data)

        fun fake(
            zeroHourStart: LocalDateTime,
            channelId: UUID,
            startHours: Float,
            endHours: Float,
        ) = TvProgram(
            id = UUID.randomUUID(),
            channelId = channelId,
            start = zeroHourStart.plusMinutes((startHours * 60).toLong()),
            end = zeroHourStart.plusMinutes((endHours * 60).toLong()),
            startHours = startHours,
            endHours = endHours,
            duration = ((endHours - startHours) * 60).toInt().minutes,
            name = NO_DATA,
            subtitle = null,
            overview = null,
            seasonEpisode = null,
            isRecording = false,
            isSeriesRecording = false,
            isRepeat = false,
            category = ProgramCategory.FAKE,
        )
    }
}

enum class ProgramCategory(
    val color: Color?,
) {
    KIDS(AppColors.DarkCyan),
    NEWS(AppColors.DarkGreen),
    MOVIE(AppColors.DarkPurple),
    SPORTS(AppColors.DarkRed),
    FAKE(null),
}

data class FetchedPrograms(
    val range: IntRange = 0..0,
    val programs: List<TvProgram> = emptyList(),
    val programsByChannel: Map<UUID, List<TvProgram>> = emptyMap(),
)

fun LocalDateTime.roundDownToHalfHour(): LocalDateTime {
    val min = minute % 30L
    return minusMinutes(min).truncatedTo(ChronoUnit.MINUTES)
}
