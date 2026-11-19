package com.github.damontecres.wholphin.ui.detail.livetv

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.preferences.LiveTvPreferences
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Renders the new Live TV guide at television size and writes a PNG.
 *
 * The tuner is empty while its guide is being repaired, so the visual layout cannot be judged on
 * the device. This fixture uses illustrative guide data only (never production data), which is the
 * same approach as the On Air shot test. It is not an assertion about content: the PNG is the
 * artifact, and the size check catches a collapsed composition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LiveTvGuideShotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun render() {
        val start = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        val channels =
            listOf("HBO", "NBC", "CBS", "ABC", "FOX", "PBS", "CW")
                .mapIndexed { index, name ->
                    TvChannel(
                        id = UUID.randomUUID(),
                        number = (902 + index).toString(),
                        name = name,
                        imageUrl = null,
                        favorite = index == 1,
                    )
                }
        val titles =
            mapOf(
                0 to listOf("Miracle at St. Anna (2008)", "The Astronaut Farmer (2007)", "Ray (2004)", "Space Buddies (2009)"),
                1 to listOf("My Big Fat Greek Wedding (2002)", "Lying and Stealing (2019)", "The Cider House Rules (1999)", "A Little Curious"),
                2 to listOf("Ray (2004)", "The Cider House Rules (1999)", "Life Is Beautiful (1997)", "Sesame Street"),
                3 to listOf("Life Is Beautiful (1997)", "Beverly Hills Chihuahua 3 (2012)", "Miracle at St. Anna (2008)", "The Astronaut Farmer (2007)"),
                4 to listOf("Space Buddies (2009)", "A Little Curious", "Pinky Dinky Doo", "Sesame Street"),
                5 to listOf("Pinky Dinky Doo", "Sesame Street", "The Supermarket", "Watch Now on Demand"),
                6 to listOf("The Supermarket", "Watch Now on Demand", "Ray (2004)", "Lying and Stealing (2019)"),
            )
        val programsByChannel = LinkedHashMap<UUID, List<TvProgram>>()
        val flat = mutableListOf<TvProgram>()
        val durations = listOf(90, 30, 60, 60)
        channels.forEachIndexed { channelIndex, channel ->
            var cursor = start
            val channelPrograms =
                titles.getValue(channelIndex).mapIndexed { programIndex, title ->
                    val programStart = cursor
                    val programEnd = programStart.plusMinutes(durations[programIndex].toLong())
                    cursor = programEnd
                    TvProgram(
                        id = UUID.randomUUID(),
                        channelId = channel.id,
                        start = programStart,
                        end = programEnd,
                        startHours = (programStart.hour - start.hour).toFloat() + programStart.minute / 60f,
                        endHours = (programEnd.hour - start.hour).toFloat() + programEnd.minute / 60f,
                        duration = durations[programIndex].minutes,
                        name = title,
                        subtitle = null,
                        seasonEpisode = null,
                        isRecording = false,
                        isSeriesRecording = false,
                        isRepeat = false,
                        category = if (channelIndex % 3 == 0) ProgramCategory.MOVIE else null,
                        officialRating = if (channelIndex % 2 == 0) "PG" else null,
                        genres =
                            when (channelIndex) {
                                0 -> listOf("Comedy")
                                1 -> listOf("Drama")
                                else -> emptyList()
                            },
                        productionYear = Regex("\\((\\d{4})\\)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                    )
                }
            programsByChannel[channel.id] = channelPrograms
            flat += channelPrograms
        }
        val programs =
            FetchedPrograms(
                range = 0..channels.lastIndex,
                programs =
                    flat.sortedWith(
                        compareBy(
                            { channels.indexOfFirst { channel -> channel.id == it.channelId } },
                            { it.start },
                        ),
                    ),
                programsByChannel = programsByChannel,
            )
        val guideTimes = (0..6).map { start.plusMinutes(it * 30L) }

        compose.setContent {
            WholphinTheme(liquidGlass = true) {
                CompositionLocalProvider(LocalLiquidGlass provides true) {
                    Box(Modifier.background(GuideEpg.background).size(960.dp, 540.dp)) {
                        Column(Modifier.fillMaxSize()) {
                            // The app's compact top bar is chrome and cannot be rendered without a
                            // signed-in user; reserve its measured height so the rest is judged in
                            // the exact room the real page has.
                            Box(Modifier.fillMaxWidth().height(56.dp))
                            GuideInfoHeader(
                                program = programs.programs.firstOrNull(),
                                channel = channels.firstOrNull(),
                                filters = LiveTvFilters(),
                                now = start.plusMinutes(48),
                                onOpenFilters = {},
                            )
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                TvGuideGridContent(
                                    preferences = LiveTvPreferences.getDefaultInstance(),
                                    refreshing = false,
                                    channels = channels,
                                    programs = programs,
                                    channelProgramCount = programsByChannel.mapValues { it.value.size },
                                    guideTimes = guideTimes,
                                    onClickChannel = { _, _ -> },
                                    onClickProgram = { _, _ -> },
                                    onFocus = {},
                                    onFocusProgram = {},
                                    focusRequester = FocusRequester(),
                                    dense = true,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        Assert.assertTrue(bitmap.width >= 900)
        Assert.assertTrue(bitmap.height >= 500)
        File("/tmp/livetv_guide_shot.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
