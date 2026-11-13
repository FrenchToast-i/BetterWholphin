package com.github.damontecres.wholphin.ui.setup

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.JellyfinUser
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
import java.util.UUID

/**
 * Renders the glass profile picker at television size and writes a PNG.
 *
 * Not an assertion-based test: the layout is the artifact. It exists because the setup screen is
 * the one page that cannot be reached on the emulator without a server, and the picker's whole
 * job is to fit a household's profiles in a pane without clipping the last one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ProfilePickerShotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun user(
        name: String,
        pin: String? = null,
    ) = JellyfinUser(
        id = UUID.randomUUID(),
        name = name,
        serverId = UUID.randomUUID(),
        accessToken = "token",
        pin = pin,
    )

    @Test
    fun render() {
        val serverId = UUID.randomUUID()
        val users =
            listOf(
                user("John"),
                user("Ada", pin = "1234"),
                user("Theo"),
                user("Mara"),
            ).map { JellyfinUserAndImage(it.copy(serverId = serverId), null, known = true) }

        compose.setContent {
            WholphinTheme(liquidGlass = true) {
                CompositionLocalProvider(LocalLiquidGlass provides true) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF10141A)),
                    ) {
                        UserList(
                            users = users,
                            currentUser = users.first().user,
                            onSwitchUser = {},
                            onAddUser = {},
                            onRemoveUser = {},
                            onSwitchServer = {},
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 92.dp, end = 60.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        Assert.assertTrue(bitmap.width >= 900)
        Assert.assertTrue(bitmap.height >= 500)
        File("/tmp/profile_picker.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
