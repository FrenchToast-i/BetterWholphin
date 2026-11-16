package com.github.damontecres.wholphin.services.bugreport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import android.content.pm.ServiceInfo
import android.widget.Toast

/**
 * Records the screen, then packs the video, the app log and the device details into one zip for
 * a bug report.
 *
 * A report is only useful if it captures the moment something went wrong, so recording starts
 * from the same press that stops it: one toggle, mappable to a remote key through a key remapper.
 * The service is a foreground service with the `mediaProjection` type because Android 14 refuses
 * `getMediaProjection` from anything else.
 */
@AndroidEntryPoint
class BugReportService : Service() {
    @Inject
    lateinit var uploader: GitHubReportUploader

    private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)
    private var recorder: ScreenRecorder? = null
    private var videoFile: File? = null
    private var startedAt = 0L
    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (recorder != null) finish() else stopSelf()
            }

            ACTION_START -> startRecording(intent)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (recorder != null) {
            return
        }
        BugReportStore.prune(this)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (data == null) {
            BugReportStore.updateStatus(BugReportStatus.Failed("Screen capture was not granted"))
            stopSelf()
            return
        }
        // The notification has to exist before the projection is taken on Android 14.
        goForeground(getString(R.string.bug_report_recording))
        try {
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            val projection =
                projectionManager.getMediaProjection(resultCode, data)
                    ?: throw IllegalStateException("Screen capture was not granted")
            val metrics = resources.displayMetrics
            val width = 1280
            val height = (width * metrics.heightPixels / metrics.widthPixels).let { it - (it % 2) }
            val file = BugReportStore.newVideoFile(this)
            val screenRecorder =
                ScreenRecorder(
                    projection = projection,
                    outputFile = file,
                    width = width,
                    height = height,
                    densityDpi = metrics.densityDpi,
                )
            screenRecorder.start()
            recorder = screenRecorder
            videoFile = file
            startedAt = System.currentTimeMillis()
            BugReportStore.updateStatus(BugReportStatus.Recording(startedAt))
            toast(getString(R.string.bug_report_started))
        } catch (ex: Exception) {
            Timber.e(ex, "Could not start bug report recording")
            BugReportStore.updateStatus(BugReportStatus.Failed(ex.message ?: "Could not start recording"))
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        val file = videoFile
        val start = startedAt
        runCatching { recorder?.stop() }
        recorder = null
        videoFile = null
        if (file == null || !file.exists()) {
            BugReportStore.updateStatus(BugReportStatus.Failed("The recording produced no video"))
            stopForegroundCompat()
            stopSelf()
            return
        }
        goForeground(getString(R.string.bug_report_packing))
        scope.launch {
            val zip = pack(file, start)
            val result = uploadIfConfigured(zip)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                BugReportStore.updateStatus(result)
                val message =
                    when {
                        result is BugReportStatus.Packed && result.uploadUrl != null ->
                            getString(R.string.bug_report_uploaded)
                        result is BugReportStatus.Packed ->
                            getString(R.string.bug_report_saved)
                        else -> getString(R.string.bug_report_failed)
                    }
                Toast.makeText(this@BugReportService, message, Toast.LENGTH_LONG).show()
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private suspend fun uploadIfConfigured(zip: File): BugReportStatus {
        val config = BugReportConfig(repo(this), token(this), autoUpload(this))
        if (!config.autoUpload || config.token.isBlank()) {
            return BugReportStatus.Packed(zip)
        }
        return uploader
            .upload(config.repo, config.token, zip, getString(R.string.bug_report_label))
            .fold(
                onSuccess = { url -> BugReportStatus.Packed(zip, uploadUrl = url) },
                onFailure = { ex ->
                    // A failed upload must not lose the report: the zip stays on disk and the
                    // settings page can share it.
                    Timber.w(ex, "Bug report kept on device after upload failure")
                    BugReportStatus.Packed(zip, error = ex.message)
                },
            )
    }

    private fun repo(context: Context) = BugReportStore.repo(context)

    private fun token(context: Context) = BugReportStore.token(context)

    private fun autoUpload(context: Context) = BugReportStore.autoUpload(context)

    private fun pack(
        video: File,
        startedAt: Long,
    ): File {
        val dir = BugReportStore.reportsDir(this)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(startedAt))
        val zip = File(dir, "bug-report-$stamp.zip")
        val log = File(dir, "logcat-$stamp.txt")
        val device = File(dir, "device-$stamp.txt")
        runCatching { captureLogcat(log) }
        runCatching { device.writeText(deviceDetails(startedAt)) }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { zos ->
            listOf(video, log, device).filter { it.exists() && it.length() > 0 }.forEach { entry ->
                zos.putNextEntry(ZipEntry(entry.name))
                entry.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        // The zip is the report; the parts are reproducible and would double the cache use.
        runCatching { video.delete() }
        runCatching { log.delete() }
        runCatching { device.delete() }
        BugReportStore.prune(this)
        Timber.i("Packed bug report %s (%d bytes)", zip.name, zip.length())
        return zip
    }

    private fun captureLogcat(output: File) {
        // Since Android 4.1 an app can only read its own log records, which is exactly what a
        // report needs: the app's own Timber lines around the moment the viewer reported.
        val process =
            ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "8000")
                .redirectErrorStream(true)
                .start()
        process.inputStream.bufferedReader().use { reader ->
            output.outputStream().bufferedWriter().use { writer ->
                writer.write(reader.readText())
            }
        }
        process.waitFor()
    }

    private fun deviceDetails(startedAt: Long): String =
        buildString {
            appendLine("Wholphin bug report")
            appendLine("Recorded: ${Date(startedAt)}")
            appendLine("Duration: ${(System.currentTimeMillis() - startedAt) / 1000}s")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: $packageName")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Display: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels} @ ${resources.displayMetrics.densityDpi}dpi")
            appendLine("Locale: ${Locale.getDefault()}")
        }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.bug_reports),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                },
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, BugReportService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.bug_reports))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.bug_report_stop), stop)
            .build()
    }

    private fun goForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        stopForeground(true)
    }

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        runCatching { recorder?.stop() }
        recorder = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.github.damontecres.wholphin.bugreport.START"
        const val ACTION_STOP = "com.github.damontecres.wholphin.bugreport.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "bug_reports"
        private const val NOTIFICATION_ID = 0x5EED

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
        ) {
            val intent =
                Intent(context, BugReportService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BugReportService::class.java).setAction(ACTION_STOP))
        }
    }
}

private data class BugReportConfig(
    val repo: String,
    val token: String,
    val autoUpload: Boolean,
)
