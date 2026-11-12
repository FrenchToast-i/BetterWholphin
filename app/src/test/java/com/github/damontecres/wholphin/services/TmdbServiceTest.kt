package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The credential is the one part of [TmdbService] that must never regress: a v3 key has to stay in
 * the query string, a v4 token has to stay in a header, and a blank key must produce no request at
 * all. The outcome tests pin the difference between a refused key and an unreachable service.
 * Nothing here ever uses a real key.
 */
class TmdbServiceTest {
    private val testDispatcher = StandardTestDispatcher()
    private val client = mockk<OkHttpClient>()
    private val dataStore = mockk<DataStore<AppPreferences>>()
    private val accountPreferences =
        mockk<AccountPreferencesService> {
            // The device key is the fallback whenever the account has no override.
            every { current } returns flowOf(AccountPreferences.Default)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        WholphinDispatchers.configure(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        WholphinDispatchers.reset()
    }

    private fun appPreferences(
        key: String,
        showClock: Boolean = false,
    ): AppPreferences =
        AppPreferences
            .newBuilder()
            .setInterfacePreferences(
                InterfacePreferences
                    .newBuilder()
                    .setTmdbApiKey(key)
                    .setShowClock(showClock),
            ).build()

    private fun service(key: String): TmdbService {
        every { dataStore.data } returns flowOf(appPreferences(key))
        return TmdbService(client, dataStore, accountPreferences)
    }

    /** Answers the next call with [body] and [code], recording the request that was sent. */
    private fun stubCall(
        body: String,
        code: Int = 200,
        onRequest: (Request) -> Unit,
    ) {
        val response =
            Response
                .Builder()
                .request(Request.Builder().url("https://api.themoviedb.org/3/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        val call =
            mockk<Call> {
                every { execute() } returns response
            }
        every { client.newCall(any()) } answers {
            onRequest(firstArg())
            call
        }
    }

    private fun <T> TmdbResult<T>.value(): T? = (this as? TmdbResult.Success)?.value

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun v3KeyTravelsInTheQueryString() =
        runTest {
            var sent: Request? = null
            stubCall("""{"results":[{"id":1,"media_type":"movie","title":"A"}]}""") { sent = it }

            val items = service("abc123").trending().value()

            assertEquals(1, items?.size)
            assertEquals("abc123", sent?.url?.queryParameter("api_key"))
            assertNull(sent?.header("Authorization"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun v4TokenTravelsInTheHeader() =
        runTest {
            var sent: Request? = null
            stubCall("""{"results":[{"id":1,"media_type":"tv","name":"A"}]}""") { sent = it }
            val token = "eyJhbGciOiJIUzI1NiJ9." + "a".repeat(70) + ".signature"

            val items = service(token).trending().value()

            assertEquals(1, items?.size)
            assertEquals("Bearer $token", sent?.header("Authorization"))
            assertNull(sent?.url?.queryParameter("api_key"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun keyFlowEmitsOnlyWhenTheKeyItselfChanges() =
        runTest {
            // Search's ViewModel follows this flow so a key edited in Settings takes effect in
            // place; every other preference in the same object must not re-fetch the shelf.
            val stored = MutableStateFlow(appPreferences("first"))
            every { dataStore.data } returns stored
            val service = TmdbService(client, dataStore, accountPreferences)
            val seen = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                service.keyFlow.toList(seen)
            }

            stored.value = appPreferences("first", showClock = true)
            runCurrent()
            stored.value = appPreferences("second")
            runCurrent()

            assertEquals(listOf("first", "second"), seen)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun blankKeySendsNoRequest() =
        runTest {
            val items = service("   ").trending().value()

            assertTrue(items?.isEmpty() == true)
            verify(exactly = 0) { client.newCall(any()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rejectedKeyIsNotReportedAsUnreachable() =
        runTest {
            stubCall("""{"status_code":7,"status_message":"Invalid API key"}""", code = 401) {}

            assertEquals(TmdbResult.Rejected, service("wrong-key").trending())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun serverErrorIsUnavailable() =
        runTest {
            stubCall("""{"status_code":11,"status_message":"Internal error"}""", code = 500) {}

            assertEquals(TmdbResult.Unavailable, service("abc123").trending())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun findIdSearchesTheRightKindAndYear() =
        runTest {
            var sent: Request? = null
            stubCall("""{"results":[{"id":42,"name":"X","first_air_date":"2022-01-01"}]}""") { sent = it }

            val id = service("abc123").findId("Severance", 2022, isMovie = false).value()

            assertEquals(42, id)
            assertTrue(sent?.url?.encodedPath?.endsWith("/search/tv") == true)
            assertEquals("Severance", sent?.url?.queryParameter("query"))
            assertEquals("2022", sent?.url?.queryParameter("first_air_date_year"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun searchKeepsTitlesAndDropsPeople() =
        runTest {
            // Search asks the library first, so its TMDB fallback is a title search. The multi
            // endpoint also returns people, and a person has no poster and nothing to
            // recommend, so they are dropped before the shelf sees them.
            var sent: Request? = null
            stubCall(
                """{"results":[
                    {"id":1,"media_type":"movie","title":"A"},
                    {"id":2,"media_type":"tv","name":"B"},
                    {"id":3,"media_type":"person","name":"C"}
                ]}""",
            ) { sent = it }

            val items = service("abc123").search(" Severance ").value()

            assertEquals(listOf("A", "B"), items?.map { it.displayTitle })
            assertTrue(sent?.url?.encodedPath?.endsWith("/search/multi") == true)
            // Trimmed, so a trailing space from a TV keyboard does not change the results.
            assertEquals("Severance", sent?.url?.queryParameter("query"))
            assertEquals("false", sent?.url?.queryParameter("include_adult"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun blankSearchSendsNoRequest() =
        runTest {
            val items = service("abc123").search("   ").value()

            assertTrue(items?.isEmpty() == true)
            verify(exactly = 0) { client.newCall(any()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun recommendationsStampTheSeedKindOnResults() =
        runTest {
            // The recommendations endpoint omits media_type; the dialog needs it to say film or
            // television, so the service fills it in from the seed.
            stubCall("""{"results":[{"id":7,"title":"X","vote_average":7.5}]}""") {}

            val items = service("abc123").recommendations(tmdbId = 7, isMovie = true).value()

            assertEquals(1, items?.size)
            assertEquals("movie", items?.first()?.mediaType)
            assertEquals("X", items?.first()?.displayTitle)
        }
}
