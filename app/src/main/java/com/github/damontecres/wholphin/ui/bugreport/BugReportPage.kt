package com.github.damontecres.wholphin.ui.bugreport

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import android.media.projection.MediaProjectionManager
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.bugreport.BugReportService
import com.github.damontecres.wholphin.services.bugreport.BugReportStatus
import com.github.damontecres.wholphin.services.bugreport.BugReportStore
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.glassSurface
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * The bug-report recorder.
 *
 * One button toggles recording, so a remote key can be mapped straight to it through a key
 * remapper and a broadcast. The rest of the page is the one-time setup: which repository
 * receives the report, the token that may write to it, and whether to upload automatically.
 */
@Composable
fun BugReportPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val glass = LocalLiquidGlass.current
    val status by BugReportStore.status.collectAsState()
    val recording = status as? BugReportStatus.Recording
    var repo by rememberSaveable { mutableStateOf(BugReportStore.repo(context)) }
    var token by rememberSaveable { mutableStateOf(BugReportStore.token(context)) }
    var autoUpload by rememberSaveable { mutableStateOf(BugReportStore.autoUpload(context)) }
    val toggleFocus = remember { FocusRequester() }
    val lastReport = remember(status) { BugReportStore.latestReport(context) }

    val projectionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                BugReportService.start(context, result.resultCode, data)
            }
        }

    // The page opens for a viewer who wants to press record, not to type: focus the toggle and
    // retry until it is laid out, otherwise the first focusable (the token field) wins and the
    // keyboard covers the page.
    LaunchedEffect(Unit) {
        repeat(20) {
            if (toggleFocus.tryRequestFocus()) return@LaunchedEffect
            delay(50)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        // The page floats on the backdrop rather than inside the settings pane, so the title lives
        // in the same sheet as the controls: white type on bright artwork is not readable.
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .glassSurface(scrim = 1f)
                    .padding(GlassTokens.rowInset + 8.dp),
        ) {
            Text(
                text = stringResource(R.string.bug_reports),
                style = if (glass) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.bug_report_hint),
                style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                color =
                    if (glass) {
                        Color.White.copy(alpha = 0.68f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            val statusText =
                when (val s = status) {
                    BugReportStatus.Idle -> stringResource(R.string.bug_report_idle)
                    is BugReportStatus.Recording -> recordingLine(s.startedAt)
                    is BugReportStatus.Packed -> {
                        when {
                            s.uploadUrl != null -> stringResource(R.string.bug_report_uploaded)
                            s.error != null -> stringResource(R.string.bug_report_failed) + ": " + s.error
                            else -> stringResource(R.string.bug_report_saved)
                        }
                    }

                    is BugReportStatus.Failed -> stringResource(R.string.bug_report_failed) + ": " + s.message
                }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (glass) {
                        Color.White.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (recording != null) {
                            BugReportService.stop(context)
                        } else {
                            val manager = context.getSystemService(MediaProjectionManager::class.java)
                            projectionLauncher.launch(manager.createScreenCaptureIntent())
                        }
                    },
                    modifier = Modifier.focusRequester(toggleFocus),
                    // Keep the same white capsule in both states: switching the colour role while
                    // the button is focused leaves its label white on the new white fill, so the
                    // stop button looked like a blank pill exactly when the viewer needed it.
                    glassProminent = true,
                ) {
                    Text(
                        stringResource(
                            if (recording != null) R.string.bug_report_stop else R.string.bug_report_record,
                        ),
                    )
                }
                Button(
                    onClick = {
                        autoUpload = !autoUpload
                        BugReportStore.setAutoUpload(context, autoUpload)
                    },
                    glassFilled = true,
                ) {
                    Text(
                        stringResource(
                            if (autoUpload) R.string.bug_report_upload_on else R.string.bug_report_upload_off,
                        ),
                    )
                }
            }

            Text(
                text = stringResource(R.string.bug_report_repo),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (glass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditTextBox(
                value = repo,
                onValueChange = {
                    repo = it
                    BugReportStore.setRepo(context, it)
                },
                modifier = Modifier.fillMaxWidth(),
                glass = glass,
            )
            Text(
                text = stringResource(R.string.bug_report_token),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (glass) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EditTextBox(
                value = token,
                onValueChange = {
                    token = it
                    BugReportStore.setToken(context, it)
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                glass = glass,
            )
            Text(
                text = stringResource(R.string.bug_report_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (glass) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastReport != null) {
                Button(
                    onClick = {
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                lastReport,
                            )
                        val share =
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(
                            android.content.Intent.createChooser(share, null)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    glassFilled = true,
                ) {
                    Text(stringResource(R.string.bug_report_share_last))
                }
            }
        }
    }
}

@Composable
private fun recordingLine(startedAt: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = ((now - startedAt) / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    return stringResource(
        R.string.bug_report_recording_for,
        "%d:%02d".format(minutes, seconds % 60),
    )
}
