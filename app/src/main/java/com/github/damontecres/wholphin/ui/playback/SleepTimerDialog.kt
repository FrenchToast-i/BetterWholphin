package com.github.damontecres.wholphin.ui.playback

import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.SleepTimerPlan
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.playback.overlay.BottomDialog
import com.github.damontecres.wholphin.ui.playback.overlay.BottomDialogItem
import kotlin.time.Duration.Companion.minutes

/**
 * A running sleep timer.
 *
 * @param minutes the length the viewer chose, which is what the dialog highlights; a timer with a
 *   countdown is still "the 30-minute timer", not "the 7-minute timer"
 * @param remainingSeconds wall-clock seconds left
 */
data class SleepTimerState(
    val minutes: Int,
    val remainingSeconds: Int,
)

/**
 * The picker for the sleep timer: off, a few lengths, and — when it is near enough — the viewer's
 * own bedtime.
 *
 * The bedtime entry is the bridge between this and [com.github.damontecres.wholphin.ui.main.TonightPage]:
 * the page plans an evening that finishes before bed, and this makes the television keep that
 * promise when the viewer starts something with eleven minutes to spare. It is listed with its
 * length ("At bedtime · 11m") because a clock time is a worse answer than the number of minutes the
 * viewer actually has.
 */
@Composable
fun SleepTimerDialog(
    currentMinutes: Int?,
    bedtimeMinutes: Int?,
    onDismissRequest: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val context = LocalContext.current
    val resources = context.resources
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val gravity = if (isLtr) Gravity.END else Gravity.START
    val off = stringResource(R.string.sleep_timer_off)
    val inLabel = stringResource(R.string.sleep_timer_in)
    val bedtimeLabel = stringResource(R.string.sleep_timer_at_bedtime)
    val choices =
        buildList<BottomDialogItem<Int?>> {
            add(BottomDialogItem<Int?>(data = null, headline = off, supporting = null))
            SleepTimerPlan.OPTIONS.forEach { minutes ->
                add(
                    BottomDialogItem<Int?>(
                        data = minutes,
                        headline = inLabel.format(resources.formatDuration(minutes.minutes)),
                        supporting = null,
                    ),
                )
            }
            // When the bedtime happens to be exactly one of the fixed lengths, the two rows
            // would start the same timer with two different labels.
            bedtimeMinutes?.takeIf { it !in SleepTimerPlan.OPTIONS }?.let { minutes ->
                add(
                    BottomDialogItem<Int?>(
                        data = minutes,
                        // A bedtime eleven minutes away is a different thing from a bedtime five
                        // hours away; the countdown is the useful half of the label.
                        headline = bedtimeLabel.format(resources.formatDuration(minutes.minutes)),
                        supporting = null,
                    ),
                )
            }
        }
    BottomDialog(
        choices = choices,
        onDismissRequest = onDismissRequest,
        onSelectChoice = { _, item -> onSelect(item.data) },
        gravity = gravity,
        currentChoice = choices.firstOrNull { it.data == currentMinutes },
        header = stringResource(R.string.sleep_timer),
    )
}
