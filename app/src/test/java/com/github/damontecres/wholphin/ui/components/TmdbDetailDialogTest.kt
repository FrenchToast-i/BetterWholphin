package com.github.damontecres.wholphin.ui.components

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.TmdbRequestStatus
import com.github.damontecres.wholphin.data.model.TmdbRequestUiState
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The request control is the only place discovery can turn into something watchable when the
 * library does not own the title, so it is exercised through the real dialog: the sentence, the
 * action it offers, and the state it keeps after the server answers.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
class TmdbDetailDialogTest {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    // From https://github.com/robolectric/robolectric/pull/4736#issuecomment-1831034882
    @get:Rule(order = 1)
    val addActivityToRobolectricRule =
        object : TestWatcher() {
            override fun starting(description: Description?) {
                super.starting(description)
                val appContext: Application = ApplicationProvider.getApplicationContext()
                val activityInfo =
                    ActivityInfo().apply {
                        name = TestActivity::class.java.name
                        packageName = appContext.packageName
                    }
                shadowOf(appContext.packageManager).addOrUpdateActivity(activityInfo)
            }
        }

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun item() =
        TmdbItem(
            id = 603,
            mediaType = "movie",
            title = "The Matrix",
            overview = "A hacker learns the truth about his reality.",
            releaseDate = "1999-03-31",
            voteAverage = 8.2,
        )

    private fun show(
        state: TmdbRequestUiState = TmdbRequestUiState(),
        onRequest: () -> Unit = {},
        onOpenInLibrary: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onFindInLibrary: (TmdbItem) -> Unit = {},
    ) {
        composeTestRule.setContent {
            WholphinTheme {
                TmdbDetailDialog(
                    item = item(),
                    onDismissRequest = onDismiss,
                    onFindInLibrary = onFindInLibrary,
                    requestState = state,
                    onRequest = onRequest,
                    onOpenInLibrary = onOpenInLibrary,
                )
            }
        }
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    @Test
    fun `a title the server does not have offers a request and the library search`() {
        var requested = false
        show(
            state =
                TmdbRequestUiState(
                    status = TmdbRequestStatus(SeerrAvailability.UNKNOWN, canRequest = true, jellyfinItemId = null),
                ),
            onRequest = { requested = true },
        )

        composeTestRule.onNodeWithText(string(R.string.tmdb_request_hint)).assertIsDisplayed()
        // Discovery must not dead-end: the request is an addition to the library bridge, not a
        // replacement for it.
        composeTestRule.onNodeWithText(string(R.string.tmdb_find_in_library)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tmdb_request)).performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(requested)
    }

    @Test
    fun `without seerr the dialog is exactly the library search it was before`() {
        var requested = false
        show(onRequest = { requested = true })

        composeTestRule.onNodeWithText(string(R.string.tmdb_find_in_library)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tmdb_request_hint)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.tmdb_request)).assertDoesNotExist()
        assertFalse(requested)
    }

    @Test
    fun `an approved request is downloading rather than requestable again`() {
        var requested = false
        show(
            state =
                TmdbRequestUiState(
                    status = TmdbRequestStatus(SeerrAvailability.PROCESSING, canRequest = true, jellyfinItemId = null),
                ),
            onRequest = { requested = true },
        )

        composeTestRule.onNodeWithText(string(R.string.tmdb_request_processing)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tmdb_requested)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.tmdb_requested)).performSemanticsAction(SemanticsActions.OnClick)
        assertFalse(requested)
    }

    @Test
    fun `a title already on the server offers to open rather than to request`() {
        var opened = false
        var dismissed = false
        show(
            state =
                TmdbRequestUiState(
                    status =
                        TmdbRequestStatus(
                            SeerrAvailability.AVAILABLE,
                            canRequest = true,
                            jellyfinItemId = UUID.randomUUID(),
                        ),
                ),
            onOpenInLibrary = { opened = true },
            onDismiss = { dismissed = true },
        )

        composeTestRule.onNodeWithText(string(R.string.tmdb_request_available)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tmdb_open_in_library))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(true, opened)
        assertEquals(true, dismissed)
    }

    @Test
    fun `a request in flight says so instead of pretending it is idle`() {
        show(
            state =
                TmdbRequestUiState(
                    status = TmdbRequestStatus(SeerrAvailability.UNKNOWN, canRequest = true, jellyfinItemId = null),
                    submitting = true,
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.tmdb_requesting)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tmdb_requesting)).assertIsNotEnabled()
    }

    @Test
    fun `a failed request is reported and left pressable`() {
        show(
            state =
                TmdbRequestUiState(
                    status = TmdbRequestStatus(SeerrAvailability.UNKNOWN, canRequest = true, jellyfinItemId = null),
                    failed = true,
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.tmdb_request_failed)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.tmdb_request)).assertIsDisplayed()
    }
}
