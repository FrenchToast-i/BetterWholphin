package com.github.damontecres.wholphin.services

import kotlin.random.Random

/**
 * The taste model behind "Play something".
 *
 * A shuffle button that picks uniformly is a random button: with a large library it mostly
 * suggests things the viewer has never shown any interest in, and it feels like a slot machine
 * rather than like a recommendation. So the picker leans on what the viewer actually watches —
 * the genres of their recent history, weighted by recency — but never at the cost of dead ends:
 * anything without a matching genre keeps a base weight, so a library that is all new to them
 * still answers.
 *
 * Pure and deterministic (the [Random] is injected), so the weighting can be unit tested without a
 * server. [SurpriseService] owns the queries and the "don't repeat the last few picks" memory.
 */
internal object SurprisePicker {
    /**
     * Weight of a candidate with no matching genre. Small enough that taste dominates the draw,
     * large enough that a library of unfamiliar genres is not a dead end.
     */
    const val BASE_WEIGHT = 0.25

    /**
     * Genre weights in `0..1`, most recent history counting most.
     *
     * The newest watched title is the strongest signal of what the viewer wants tonight, so the
     * weight decays linearly with position in [history] rather than counting each watched genre
     * equally — otherwise a decade of watching one genre would drown out the thing they just
     * finished.
     */
    fun genreAffinity(history: List<List<String>>): Map<String, Double> {
        if (history.isEmpty()) return emptyMap()
        val totals = mutableMapOf<String, Double>()
        history.forEachIndexed { index, genres ->
            val recency = (history.size - index).toDouble() / history.size
            genres.forEach { genre ->
                val key = genre.lowercase()
                totals[key] = (totals[key] ?: 0.0) + recency
            }
        }
        val max = totals.values.maxOrNull() ?: return emptyMap()
        if (max <= 0.0) return emptyMap()
        return totals.mapValues { it.value / max }
    }

    /** How much one candidate should be favoured, given an [affinity] from [genreAffinity]. */
    fun weight(
        genres: List<String>,
        affinity: Map<String, Double>,
    ): Double = BASE_WEIGHT + genres.sumOf { affinity[it.lowercase()] ?: 0.0 }

    /**
     * Draw one item with probability proportional to its weight.
     *
     * Returns `null` only for an empty list. Weights are clamped at zero so a negative weight can
     * never invert the draw.
     */
    fun <T> pickWeighted(
        items: List<T>,
        weightOf: (T) -> Double,
        random: Random,
    ): T? {
        if (items.isEmpty()) return null
        val weights = items.map { weightOf(it).coerceAtLeast(0.0) }
        val total = weights.sum()
        if (total <= 0.0) return items[random.nextInt(items.size)]
        var remaining = random.nextDouble() * total
        items.forEachIndexed { index, item ->
            remaining -= weights[index]
            if (remaining <= 0.0) return item
        }
        // Floating point leaves a sliver at the end; the last item is the remainder of the range.
        return items.last()
    }
}
