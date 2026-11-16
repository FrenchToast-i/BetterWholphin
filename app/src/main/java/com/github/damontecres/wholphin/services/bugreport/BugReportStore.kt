package com.github.damontecres.wholphin.services.bugreport

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings and live state for the bug-report recorder.
 *
 * The token is a fine-grained GitHub token with `contents: write` on one repository. It is kept
 * in the app's private preferences — never in the source tree, a resource or a build constant —
 * because a token in an APK is a token everybody has. The repository defaults to this fork so a
 * report reaches the person who fixes it without any setup beyond the token.
 */
object BugReportStore {
    private const val PREFS = "bug_reports"
    private const val KEY_REPO = "repo"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTO_UPLOAD = "auto_upload"

    const val DEFAULT_REPO = "FrenchToast-i/BetterWholphin"

    private val _status = MutableStateFlow<BugReportStatus>(BugReportStatus.Idle)

    /** The recorder's state, shared by the service and the settings page. */
    val status = _status

    fun status(): BugReportStatus = _status.value

    fun updateStatus(status: BugReportStatus) {
        _status.value = status
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun repo(context: Context): String =
        prefs(context).getString(KEY_REPO, DEFAULT_REPO).orEmpty().ifBlank { DEFAULT_REPO }

    fun setRepo(
        context: Context,
        value: String,
    ) {
        prefs(context).edit().putString(KEY_REPO, value.trim().trim('/')).apply()
    }

    fun token(context: Context): String = prefs(context).getString(KEY_TOKEN, "").orEmpty()

    fun setToken(
        context: Context,
        value: String,
    ) {
        prefs(context).edit().putString(KEY_TOKEN, value.trim()).apply()
    }

    fun autoUpload(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPLOAD, true)

    fun setAutoUpload(
        context: Context,
        value: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPLOAD, value).apply()
    }

    fun reportsDir(context: Context): File =
        File(context.cacheDir, "bug_reports").apply { mkdirs() }

    fun newVideoFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(reportsDir(context), "report-$stamp.mp4")
    }

    fun latestReport(context: Context): File? =
        reportsDir(context)
            .listFiles()
            ?.filter { it.extension == "zip" }
            ?.maxByOrNull { it.lastModified() }

    /**
     * Removes the recordings a crash or a force-stop left behind, and keeps only the last few
     * finished reports so the cache cannot grow without bound.
     */
    fun prune(context: Context, keepReports: Int = 5) {
        val files = reportsDir(context).listFiles() ?: return
        files.filter { it.extension == "mp4" }.forEach { runCatching { it.delete() } }
        files
            .filter { it.extension == "zip" }
            .sortedByDescending { it.lastModified() }
            .drop(keepReports)
            .forEach { runCatching { it.delete() } }
    }
}

sealed interface BugReportStatus {
    data object Idle : BugReportStatus

    data class Recording(
        val startedAt: Long,
    ) : BugReportStatus

    data class Packed(
        val file: File,
        val uploadUrl: String? = null,
        val error: String? = null,
    ) : BugReportStatus

    data class Failed(
        val message: String,
    ) : BugReportStatus
}
