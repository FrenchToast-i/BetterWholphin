package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The window the hand-rolled TV menus live in: full screen, undimmed, content anchored to the
 * trailing edge.
 *
 * These menus used to move their window with `Window.setGravity(END)`, reached through
 * `LocalView.current.parent as? DialogWindowProvider`. That lookup does not resolve to a dialog
 * window on Android TV, so the gravity silently never applied and every menu came out dead centre
 * instead of at the edge it was opened from.
 *
 * The collision is what makes it worth fixing: a menu that opens a submenu (image type, aspect
 * ratio, columns) gets a second dialog, and with both centred the two panes cover each other.
 * In the classic theme the top one is opaque, so it merely looked odd. Glass is translucent, so
 * both sets of labels read through both surfaces at once. Anchoring in layout instead of in window
 * attributes puts the menu on the edge and its submenu in the middle, where neither hides the
 * other.
 */
@Composable
fun GlassMenuDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            content()
        }
    }
}
