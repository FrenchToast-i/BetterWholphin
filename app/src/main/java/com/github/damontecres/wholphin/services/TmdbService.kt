package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access to TMDB, using a key the viewer supplies in Settings.
 *
 * The key is deliberately not a build constant and not part of the project: it is read from the
 * app's own preferences on demand, so a build never contains anyone's credentials. Nothing here
 * logs a request, a URL or an exception message — all three can contain the key — only the fact
 * that a call failed.
 */
@Singleton
class TmdbService
    @Inject
    constructor(
        @param:StandardOkHttpClient private val client: OkHttpClient,
        private val preferences: DataStore<AppPreferences>,
        private val accountPreferences: AccountPreferencesService,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            }

        suspend fun apiKey(): String = keyFlow.first()

        /**
         * The key as it is edited, so a page already on screen can react in place.
         *
         * The account's own key wins over the device key; a blank account override is a
         * deliberate "no discovery for this profile" and does not fall back. Preferences arrive
         * as objects rather than fields, so the two stores are combined here and
         * distinctUntilChanged keeps an unrelated switch from re-fetching the shelf.
         */
        val keyFlow: Flow<String> =
            combine(preferences.data, accountPreferences.current) { prefs, account ->
                (account.tmdbApiKey ?: prefs.interfacePreferences.tmdbApiKey).trim()
            }.distinctUntilChanged()

        /**
         * Whether a key has been configured at all.
         *
         * Discovery surfaces check this before asking for anything, so that "not configured" is
         * a layout decision (draw no shelf) rather than a request that has to fail first.
         */
        suspend fun hasKey(): Boolean = apiKey().isNotBlank()

        /**
         * What is trending across film and television this week.
         *
         * With no key this is an empty success rather than a failure: the caller has already
         * decided not to draw a shelf, and there is nothing to report.
         */
        suspend fun trending(): TmdbResult<List<TmdbItem>> {
            if (!hasKey()) return TmdbResult.Success(emptyList())
            return when (val response = get("/trending/all/week") { it.addQueryParameter("language", "en-US") }) {
                is TmdbResult.Success ->
                    decode(response.value) { paged ->
                        paged.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                    }

                TmdbResult.Rejected -> TmdbResult.Rejected
                TmdbResult.Unavailable -> TmdbResult.Unavailable
            }
        }

        /**
         * Titles TMDB considers close to [tmdbId], which is how the home rail follows whatever
         * the viewer is part-way through.
         *
         * The endpoint's results carry no `media_type` (they are all the same kind as the seed),
         * so it is stamped back on here: the dialog uses it to say film or television.
         */
        suspend fun recommendations(
            tmdbId: Int,
            isMovie: Boolean,
        ): TmdbResult<List<TmdbItem>> {
            val kind = if (isMovie) "movie" else "tv"
            return when (
                val response = get("/$kind/$tmdbId/recommendations") {
                    it.addQueryParameter("language", "en-US")
                }
            ) {
                is TmdbResult.Success ->
                    decode(response.value) { paged ->
                        paged.results.map { item ->
                            if (item.mediaType == null) item.copy(mediaType = kind) else item
                        }
                    }

                TmdbResult.Rejected -> TmdbResult.Rejected
                TmdbResult.Unavailable -> TmdbResult.Unavailable
            }
        }

        /**
         * Titles TMDB has for what the viewer typed.
         *
         * This is the bridge the other way round from [findId]: Search asks the library first,
         * and when the library owns nothing by that name the page has something to offer instead
         * of only "not in your library". `/search/multi` mixes people into its results, and a
         * person has no poster to draw, so non-title rows are dropped here rather than in the UI.
         *
         * A blank query sends nothing: the caller already has nothing to show for it.
         */
        suspend fun search(query: String): TmdbResult<List<TmdbItem>> {
            val trimmed = query.trim()
            if (trimmed.isBlank() || !hasKey()) return TmdbResult.Success(emptyList())
            val response =
                get("/search/multi") { url ->
                    url.addQueryParameter("query", trimmed)
                    url.addQueryParameter("language", "en-US")
                    url.addQueryParameter("include_adult", "false")
                }
            return when (response) {
                is TmdbResult.Success ->
                    decode(response.value) { paged ->
                        paged.results.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                    }

                TmdbResult.Rejected -> TmdbResult.Rejected
                TmdbResult.Unavailable -> TmdbResult.Unavailable
            }
        }

        /**
         * The TMDB id for a library title, for when Jellyfin did not scrape a provider id.
         *
         * Only a seed for discovery, so the first result wins; a wrong match would show a
         * differently-themed rail, not an error. A successful search that matches nothing is
         * `Success(null)`, which the shelf treats like any other reason not to appear.
         */
        suspend fun findId(
            title: String,
            year: Int?,
            isMovie: Boolean,
        ): TmdbResult<Int?> {
            if (title.isBlank()) return TmdbResult.Success(null)
            val response =
                get("/search/${if (isMovie) "movie" else "tv"}") { url ->
                    url.addQueryParameter("query", title)
                    url.addQueryParameter("language", "en-US")
                    url.addQueryParameter("include_adult", "false")
                    if (year != null) {
                        url.addQueryParameter(if (isMovie) "year" else "first_air_date_year", year.toString())
                    }
                }
            return when (response) {
                is TmdbResult.Success ->
                    decode(response.value) { paged ->
                        paged.results.firstOrNull()?.id
                    }

                TmdbResult.Rejected -> TmdbResult.Rejected
                TmdbResult.Unavailable -> TmdbResult.Unavailable
            }
        }

        /**
         * Read a paged body, treating malformed JSON as an unreachable service.
         *
         * A response we cannot parse is not something the viewer can fix by checking the key, so
         * it must not claim the key was rejected — and it must not throw out of a view-model
         * coroutine either.
         */
        private fun <T> decode(
            body: String,
            transform: (TmdbPaged) -> T,
        ): TmdbResult<T> =
            try {
                TmdbResult.Success(transform(json.decodeFromString<TmdbPaged>(body)))
            } catch (e: Exception) {
                Timber.w("TMDB response could not be read: %s", e.javaClass.simpleName)
                TmdbResult.Unavailable
            }

        /**
         * One GET, with the key attached the way the configured credential expects.
         *
         * A v4 read token is long and dot-separated and belongs in the Authorization header; a v3
         * key is short and travels in the query string. Nothing here is ever logged: with a v3 key
         * the URL *is* the credential, so only the failure class and a status code are recorded.
         *
         * 401/403 is reported as [TmdbResult.Rejected] rather than as unreachable: telling the
         * viewer "could not reach TMDB" when the key is the problem sends them looking in the
         * wrong place.
         */
        private suspend fun get(
            path: String,
            query: (HttpUrl.Builder) -> Unit,
        ): TmdbResult<String> =
            withContext(WholphinDispatchers.IO) {
                val key = apiKey()
                if (key.isBlank()) return@withContext TmdbResult.Unavailable

                val bearer = key.length > 60 && key.contains('.')
                val url =
                    "$BASE$path"
                        .toHttpUrl()
                        .newBuilder()
                        .apply {
                            query(this)
                            if (!bearer) addQueryParameter("api_key", key)
                        }.build()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .apply {
                            if (bearer) header("Authorization", "Bearer $key")
                        }.build()

                try {
                    client.newCall(request).execute().use { response ->
                        when {
                            response.code == 401 || response.code == 403 -> {
                                Timber.w("TMDB rejected the configured key")
                                TmdbResult.Rejected
                            }

                            !response.isSuccessful -> {
                                Timber.w("TMDB request failed with HTTP %s", response.code)
                                TmdbResult.Unavailable
                            }

                            else -> TmdbResult.Success(response.body.string())
                        }
                    }
                } catch (e: Exception) {
                    // Never log the exception itself: its message carries the request URL, and
                    // with a v3 key that URL carries the key.
                    Timber.w("TMDB request failed: %s", e.javaClass.simpleName)
                    TmdbResult.Unavailable
                }
            }

        companion object {
            private const val BASE = "https://api.themoviedb.org/3"

            /** Poster URL for a TMDB size bucket (`w342`, `w500`, …). */
            fun imageUrl(
                path: String?,
                size: String = "w342",
            ): String? = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }
        }
    }

/**
 * The three outcomes a discovery caller has to distinguish.
 *
 * [Rejected] exists because an invalid key and an unreachable service need different words on
 * screen: one is a settings problem, the other is a network problem.
 */
sealed interface TmdbResult<out T> {
    data class Success<T>(
        val value: T,
    ) : TmdbResult<T>

    /** Sent, and TMDB refused the credential (HTTP 401/403). */
    data object Rejected : TmdbResult<Nothing>

    /** Network failure, server error, no key, or a body that could not be read. */
    data object Unavailable : TmdbResult<Nothing>
}

@Serializable
data class TmdbPaged(
    val page: Int = 1,
    val results: List<TmdbItem> = emptyList(),
)

/**
 * One title from TMDB's discovery endpoints.
 *
 * Film and television share the shape, so exactly one of [title]/[name] is populated for a given
 * item; [mediaType] says which.
 */
@Serializable
data class TmdbItem(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
) {
    val displayTitle: String get() = title ?: name.orEmpty()

    val displayYear: String?
        get() = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.length == 4 }

    val isMovie: Boolean get() = mediaType != "tv"
}
