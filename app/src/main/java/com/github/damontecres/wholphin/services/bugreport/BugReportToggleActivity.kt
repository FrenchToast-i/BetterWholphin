package com.github.damontecres.wholphin.services.bugreport

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.github.damontecres.wholphin.MainActivity

/**
 * The toggle as an activity, for remappers that map a remote key to "launch an app" rather than
 * to a broadcast. It does the same thing as [BugReportReceiver] and closes immediately.
 */
class BugReportToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toggle()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        toggle()
    }

    private fun toggle() {
        if (BugReportStore.status() is BugReportStatus.Recording) {
            BugReportService.stop(this)
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(intent.action ?: BugReportReceiver.ACTION_TOGGLE)
                    .putExtra(MainActivity.EXTRA_BUG_REPORT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finishAndRemoveTask()
    }
}
