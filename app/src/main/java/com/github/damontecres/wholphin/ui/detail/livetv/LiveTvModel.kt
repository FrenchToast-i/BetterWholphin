package com.github.damontecres.wholphin.ui.detail.livetv

import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * The one filter state shared by every Live TV surface.
 *
 * The old page had three independent chips and derived "live" from sports programmes only, so a
 * news channel could be on air and still be missing from the header count. This object travels
 * through Now, Guide, Channels and Sports so switching tabs never changes the universe of channels
 * being talked about.
 */
data class LiveTvFilters(
    val favoritesOnly: Boolean = false,
    val liveOnly: Boolean = false,
    val sportsOnly: Boolean = false,
    val hasGuideOnly: Boolean = false,
    val category: ProgramCategory? = null,
) {
    val activeCount: Int
        get() =
            listOf(
                favoritesOnly,
                liveOnly,
                sportsOnly,
                hasGuideOnly,
                category != null,
            ).count { it }

    fun clear() = LiveTvFilters()

    fun toggleFavorites() = copy(favoritesOnly = !favoritesOnly)

    fun toggleLive() = copy(liveOnly = !liveOnly)

    fun toggleSports() = copy(sportsOnly = !sportsOnly)

    fun toggleHasGuide() = copy(hasGuideOnly = !hasGuideOnly)

    fun setCategory(value: ProgramCategory?) = copy(category = value)
}

/** A channel's broadest guide-derived identity. Used for grouping only, never guessed from a name. */
fun channelCategory(
    channelId: UUID,
    currentPrograms: Map<UUID, TvProgram>,
    programsByChannel: Map<UUID, List<TvProgram>>,
): ProgramCategory? {
    currentPrograms[channelId]
        ?.category
        ?.takeUnless { it == ProgramCategory.FAKE }
        ?.let { return it }
    return programsByChannel[channelId]
        .orEmpty()
        .asSequence()
        .filterNot { it.isFake }
        .mapNotNull { it.category }
        .firstOrNull()
}

/** Every channel that has a real current programme at [now]. */
fun liveProgramsByChannel(
    currentPrograms: Map<UUID, TvProgram>,
    fallbackProgramsByChannel: Map<UUID, List<TvProgram>>,
    now: LocalDateTime,
): Map<UUID, TvProgram> {
    val result = LinkedHashMap<UUID, TvProgram>()
    val channels = currentPrograms.keys + fallbackProgramsByChannel.keys
    channels.forEach { channelId ->
        val current =
            currentPrograms[channelId]
                ?.takeIf { !it.isFake && it.isLiveAt(now) }
                ?: fallbackProgramsByChannel[channelId]
                    .orEmpty()
                    .firstOrNull { !it.isFake && it.isLiveAt(now) }
        if (current != null) result[channelId] = current
    }
    return result
}

/** The next real programme on a channel after [now]. */
fun nextProgramForChannel(
    programsByChannel: Map<UUID, List<TvProgram>>,
    channelId: UUID,
    now: LocalDateTime,
): TvProgram? =
    programsByChannel[channelId]
        .orEmpty()
        .asSequence()
        .filterNot { it.isFake }
        .filter { it.start.isAfter(now) }
        .minByOrNull { it.start }

fun TvProgram.isLiveAt(now: LocalDateTime): Boolean = !start.isAfter(now) && now.isBefore(end)

fun TvProgram.progressAt(now: LocalDateTime): Float {
    val total = Duration.between(start, end).toMillis().coerceAtLeast(1L)
    val elapsed = Duration.between(start, now).toMillis().coerceIn(0L, total)
    return elapsed.toFloat() / total.toFloat()
}

fun TvProgram.minutesLeftAt(now: LocalDateTime): Long =
    Duration.between(now, end).toMinutes().coerceAtLeast(0L)

/** Programmes beginning within [minutes] of [now], earliest first, one per channel. */
fun startingSoon(
    programs: List<TvProgram>,
    now: LocalDateTime,
    minutes: Long = 180,
    limit: Int = 40,
): List<TvProgram> {
    val until = now.plusMinutes(minutes)
    return programs
        .asSequence()
        .filterNot { it.isFake }
        .filter { it.start.isAfter(now) && !it.start.isAfter(until) }
        .sortedBy { it.start }
        .distinctBy { it.channelId }
        .take(limit)
        .toList()
}

/**
 * Apply the global filters. [allProgramsByChannel] is only used to answer "does this channel ever
 * show sport" and "does it have guide data"; it is not a second schedule.
 */
fun filterChannels(
    channels: List<TvChannel>,
    filters: LiveTvFilters,
    currentPrograms: Map<UUID, TvProgram>,
    allProgramsByChannel: Map<UUID, List<TvProgram>>,
    sportsChannelIds: Set<UUID>,
    now: LocalDateTime = LocalDateTime.now(),
): List<TvChannel> =
    channels.filter { channel ->
        (!filters.favoritesOnly || channel.favorite) &&
            (!filters.liveOnly || currentPrograms[channel.id]?.isLiveAt(now) == true) &&
            (!filters.sportsOnly || channel.id in sportsChannelIds) &&
            (!filters.hasGuideOnly || allProgramsByChannel[channel.id].orEmpty().any { !it.isFake }) &&
            (
                filters.category == null ||
                    channelCategory(channel.id, currentPrograms, allProgramsByChannel) == filters.category
            )
    }

/** Does a recording/airing overlap another one? Both ends are half-open. */
fun overlaps(
    startA: LocalDateTime,
    endA: LocalDateTime,
    startB: LocalDateTime,
    endB: LocalDateTime,
): Boolean = startA.isBefore(endB) && startB.isBefore(endA)

/**
 * IDs of scheduled programmes that overlap another scheduled programme. Keeping the whole list
 * (rather than only the later recording) lets the DVR show both sides of a conflict, which is what
 * the viewer needs to make a choice.
 */
fun conflictedProgramIds(
    scheduled: List<Pair<UUID, Pair<LocalDateTime, LocalDateTime>>>,
): Set<UUID> =
    scheduled
        .mapIndexedNotNull { index, (id, window) ->
            val conflict =
                scheduled.any { (otherId, otherWindow) ->
                    otherId != id &&
                        overlaps(window.first, window.second, otherWindow.first, otherWindow.second)
                }
            id.takeIf { conflict }
        }.toSet()

/** Numeric channel lookup. Supports "5", "5.2", and padded numbers like "005". */
fun channelNumberMatches(
    channel: TvChannel,
    query: String,
): Boolean {
    val number = channel.number?.trim()?.lowercase() ?: return false
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return false
    return number == needle ||
        number.startsWith(needle) ||
        number.removePrefix("0") == needle.removePrefix("0")
}

/** Best match for the digits the viewer has entered: exact number first, then prefix. */
fun findChannelByNumber(
    channels: List<TvChannel>,
    query: String,
): TvChannel? {
    if (query.isBlank()) return null
    return channels.firstOrNull { it.number?.trim() == query.trim() }
        ?: channels.firstOrNull { channelNumberMatches(it, query) }
}
