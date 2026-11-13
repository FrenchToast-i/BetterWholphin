package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On Air: the viewer's own library as a handful of always-on television channels.
 *
 * Every other surface answers "what should I watch?" with a decision. This one removes the
 * decision: a channel is a deterministic loop of films or episodes, already in progress, so the
 * viewer can sit down and join it the way they would a broadcast. The loop is seeded by the week
 * and anchored at Monday 00:00, which is what makes "what's on" stable while the page is open and
 * different next week.
 *
 * Channels are best-effort. A library without animation loses that channel, not the page: each is
 * one query and one failure is one quiet row rather than an error screen.
 */
@Singleton
class OnAirService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        enum class Channel(
            val key: String,
            val itemTypes: List<BaseItemKind>,
            val genres: List<String> = emptyList(),
            val favoritesOnly: Boolean = false,
            val limit: Int = 140,
        ) {
            MOVIES(
                key = "movies",
                itemTypes = listOf(BaseItemKind.MOVIE),
            ),
            BINGE(
                key = "binge",
                itemTypes = listOf(BaseItemKind.EPISODE),
                limit = 200,
            ),
            COMFORT(
                key = "comfort",
                itemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                favoritesOnly = true,
            ),
            ANIMATION(
                key = "animation",
                itemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                genres = listOf("Animation"),
            ),
            LATE_NIGHT(
                key = "late",
                itemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                genres = listOf("Thriller", "Horror", "Mystery"),
            ),
        }

        data class OnAirItem(
            val item: BaseItem,
            val entry: OnAirEntry,
        )

        data class ChannelData(
            val channel: Channel,
            val items: List<OnAirItem>,
            val playlist: List<OnAirEntry>,
            val anchorMs: Long,
        ) {
            fun programs(
                nowMs: Long,
                count: Int = 6,
            ): List<OnAirProgram> = OnAirPlanner.schedule(playlist, anchorMs, nowMs, count)

            fun now(nowMs: Long): OnAirProgram? = programs(nowMs, 1).firstOrNull()

            fun tuneIn(nowMs: Long): OnAirProgram? = OnAirPlanner.tuneIn(programs(nowMs, 2), nowMs)

            fun itemFor(id: UUID): OnAirItem? = items.firstOrNull { it.entry.id == id }
        }

        /**
         * Load every channel that the library can support, in parallel. The returned list keeps
         * the channel declaration order, so a viewer who learns the list does not find it
         * rearranged when one query is slower.
         */
        suspend fun load(
            userId: UUID,
            nowMs: Long = System.currentTimeMillis(),
        ): List<ChannelData> =
            withContext(WholphinDispatchers.IO) {
                val anchor = OnAirPlanner.weekAnchor(nowMs)
                val seed = OnAirPlanner.weekSeed(anchor)
                supervisorScope {
                    Channel.entries
                        .map { channel ->
                            async {
                                try {
                                    loadChannel(channel, userId, anchor, seed)
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    // Only the class: an SDK exception can carry a request URL.
                                    Timber.w(
                                        "On Air could not load %s: %s",
                                        channel.key,
                                        ex.javaClass.simpleName,
                                    )
                                    null
                                }
                            }
                        }.awaitAll()
                        .filterNotNull()
                }
            }

        private suspend fun loadChannel(
            channel: Channel,
            userId: UUID,
            anchorMs: Long,
            seed: Long,
        ): ChannelData? {
            val items =
                api.itemsApi
                    .getItems(
                        userId = userId,
                        recursive = true,
                        includeItemTypes = channel.itemTypes,
                        genres = channel.genres.ifEmpty { null },
                        filters = if (channel.favoritesOnly) listOf(ItemFilter.IS_FAVORITE) else null,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        fields = FIELDS,
                        limit = channel.limit,
                    ).content.items
                    .mapNotNull { it.toOnAirItem() }
                    .distinctBy { it.item.id }
                    .diversify()
            if (items.size < OnAirPlanner.MIN_ENTRIES) return null

            val playlist = OnAirPlanner.playlist(channel.key, items.map { it.entry }, seed)
            return ChannelData(
                channel = channel,
                items = items,
                playlist = playlist,
                anchorMs = anchorMs,
            )
        }

        /**
         * A binge channel made of the first two hundred episodes alphabetically is one show.
         * Two episodes per series keeps the loop recognisably varied without turning the channel
         * into a schedule for somebody's complete DVD collection.
         */
        private fun List<OnAirItem>.diversify(): List<OnAirItem> {
            val perSeries = mutableMapOf<UUID, Int>()
            val result = mutableListOf<OnAirItem>()
            for (item in this) {
                val seriesId = item.item.data.seriesId
                if (seriesId != null) {
                    val seen = perSeries.getOrDefault(seriesId, 0)
                    if (seen >= MAX_PER_SERIES) continue
                    perSeries[seriesId] = seen + 1
                }
                result += item
                if (result.size >= MAX_POOL) break
            }
            return result
        }

        private fun BaseItemDto.toOnAirItem(): OnAirItem? {
            val kind = type ?: return null
            if (kind != BaseItemKind.MOVIE && kind != BaseItemKind.EPISODE) return null
            if (kind == BaseItemKind.EPISODE && seriesId == null) return null
            val runtimeMinutes =
                runTimeTicks
                    ?.ticks
                    ?.inWholeMinutes
                    ?.toInt()
                    ?.takeIf { it > 0 }
                    ?: if (kind == BaseItemKind.EPISODE) {
                        OnAirPlanner.DEFAULT_EPISODE_MINUTES
                    } else {
                        OnAirPlanner.DEFAULT_MOVIE_MINUTES
                    }
            val entry =
                OnAirEntry(
                    id = id,
                    runtimeMinutes =
                        runtimeMinutes.coerceIn(
                            OnAirPlanner.MIN_RUNTIME_MINUTES,
                            OnAirPlanner.MAX_RUNTIME_MINUTES,
                        ),
                )
            // Series artwork for an episode: the loop draws a show, not a grey 16:9 frame.
            return OnAirItem(BaseItem(this, useSeriesForPrimary = true), entry)
        }

        private companion object {
            const val MAX_POOL = 90
            const val MAX_PER_SERIES = 2

            val FIELDS =
                listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.GENRES,
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                )
        }
    }
