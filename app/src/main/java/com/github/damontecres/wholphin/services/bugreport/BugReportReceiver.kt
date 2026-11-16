package com.github.damontecres.wholphin.services.bugreport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.damontecres.wholphin.MainActivity
import timber.log.Timber

/**
 * Lets a remote key toggle the bug-report recorder.
 *
 * A television remote has no room for a debug menu, but it does have spare buttons: a key
 * remapper can map one to `am broadcast -a com.github.damontecres.wholphin.BUG_REPORT_TOGGLE`
 * and the viewer gets a one-press recorder. Starting a capture still needs the system's consent
 * dialog, so the receiver opens [MainActivity] on the first press and the service stops directly
 * on the second.
 *
 * The receiver is exported because the remapper is a different app. It cannot start a capture on
 * its own: Android requires the projection grant, which is a system dialog in front of the user.
 */
class BugReportReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_TOGGLE) return
        Timber.i("Bug report toggle received")
        if (BugReportStore.status() is BugReportStatus.Recording) {
            BugReportService.stop(context)
        } else {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_TOGGLE)
                    .putExtra(MainActivity.EXTRA_BUG_REPORT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.github.damontecres.wholphin.BUG_REPORT_TOGGLE"
    }
}
