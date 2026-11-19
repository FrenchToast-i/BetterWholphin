package com.github.damontecres.wholphin.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.R
import timber.log.Timber

/**
 * The other half of "Remind me": a notification at the moment the programme starts.
 *
 * The reminder is scheduled as one unique piece of work per programme, so cancelling the reminder
 * can cancel the notification even if the app was closed in between. WorkManager persists the
 * request, which is what makes a reminder survive the television being switched off.
 */
class LiveTvReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val channel = inputData.getString(KEY_CHANNEL)?.takeIf { it.isNotBlank() }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return Result.success()
        ensureChannel(manager)
        val launchIntent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                title.hashCode(),
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val body = listOfNotNull(title, channel).joinToString("  ·  ")
        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(applicationContext.getString(R.string.live_tv_reminder_notification))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        return try {
            manager.notify(title.hashCode(), notification)
            Result.success()
        } catch (ex: SecurityException) {
            // Android 13+ can refuse notifications without the runtime grant. The reminder stays
            // scheduled; failing the work would make WorkManager retry a permission the viewer
            // has already declined.
            Timber.w(ex, "Could not post Live TV reminder notification")
            Result.success()
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.live_tv_reminders_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = applicationContext.getString(R.string.live_tv_reminders_channel_description)
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "live_tv_reminders"
        const val KEY_TITLE = "title"
        const val KEY_CHANNEL = "channel"
    }
}
