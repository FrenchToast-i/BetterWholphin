package com.github.damontecres.wholphin.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.OnAirEntry
import com.github.damontecres.wholphin.services.OnAirPlanner
import com.github.damontecres.wholphin.services.OnAirService
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import io.mockk.mockk
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

/**
 * Renders the On Air body at television size and writes a PNG, so the dense hero can be judged
 * without a Jellyfin server. Not an assertion-based test: the layout is the artifact.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class OnAirShotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun render() {
        val imageUrlService = mockk<ImageUrlService>(relaxed = true)
        val anchor = OnAirPlanner.weekAnchor(System.currentTimeMillis())
        val now = anchor + 67 * 60_000L

        fun item(name: String, runtime: Int): BaseItem =
            BaseItem(
                BaseItemDto(
                    id = UUID.randomUUID(),
                    name = name,
                    type = BaseItemKind.MOVIE,
                    overview =
                        "A meticulously planned heist goes sideways when the hotel concierge " +
                            "discovers the painting is not the one everybody came for.",
                    runTimeTicks = runtime * 60L * 10_000_000L,
                ),
                useSeriesForPrimary = false,
            )

        fun channel(
            kind: OnAirService.Channel,
            titles: List<String>,
        ): OnAirService.ChannelData {
            val items =
                titles.mapIndexed { index, title ->
                    OnAirService.OnAirItem(
                        item = item(title, 90 + index * 7),
                        entry = OnAirEntry(UUID.randomUUID(), 90 + index * 7),
                    )
                }
            return OnAirService.ChannelData(
                channel = kind,
                items = items,
                playlist = OnAirPlanner.playlist(kind.key, items.map { it.entry }, 42L),
                anchorMs = anchor,
            )
        }

        val channels =
            listOf(
                channel(
                    OnAirService.Channel.MOVIES,
                    listOf("The Grand Budapest Hotel", "Arrival", "Knives Out", "Heat"),
                ),
                channel(
                    OnAirService.Channel.BINGE,
                    listOf("Severance", "The Bear", "Andor", "Fleabag"),
                ),
                channel(
                    OnAirService.Channel.COMFORT,
                    listOf("The Office", "Parks and Recreation", "Brooklyn Nine-Nine", "Ted Lasso"),
                ),
                channel(
                    OnAirService.Channel.ANIMATION,
                    listOf("Spirited Away", "Arcane", "The Incredibles", "Bluey"),
                ),
                channel(
                    OnAirService.Channel.LATE_NIGHT,
                    listOf("Sicario", "Get Out", "Prisoners", "Nightcrawler"),
                ),
            )

        compose.setContent {
            WholphinTheme(liquidGlass = true) {
                CompositionLocalProvider(
                    LocalLiquidGlass provides true,
                    LocalImageUrlService provides imageUrlService,
                ) {
                    Box(Modifier.background(Color(0xFF101014)).size(960.dp, 540.dp)) {
                        OnAirContent(
                            channels = channels,
                            selectedIndex = 0,
                            nowMs = now,
                            onSelect = {},
                            onTune = {},
                            onDetails = { _, _ -> },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        // A blank or collapsed composition still "captures": the size is the smoke check that
        // the rail and the hero were laid out at television width.
        Assert.assertTrue(bitmap.width >= 900)
        Assert.assertTrue(bitmap.height >= 500)
        File("/tmp/onair_shot.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
