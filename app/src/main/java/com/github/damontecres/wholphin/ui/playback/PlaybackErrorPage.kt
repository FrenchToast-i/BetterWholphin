package com.github.damontecres.wholphin.ui.playback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.ErrorViewModel
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

/**
 * The screen a playback failure lands on.
 *
 * The old page was a diagnostic banner in the corner: a tiny title, a log-upload button, and a
 * sentence naming two settings the viewer had to go and find. On a television that is a dead end.
 * This puts the message on a card, makes the useful escape — transcode this one title — the focused
 * primary action, and leaves the log upload as a secondary control.
 */
@Composable
fun PlaybackErrorPage(
    error: LoadingState.Error,
    onPlayWithTranscoding: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    errorViewModel: ErrorViewModel = hiltViewModel(),
) {
    val transcodingHint = stringResource(R.string.transcoding_disabled_error)
    val canTranscode = onPlayWithTranscoding != null && error.message == transcodingHint
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(canTranscode) {
        focusRequester.tryRequestFocus()
    }
    BackHandler(onBack = onBack)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier =
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(0.74f)
                    .padding(horizontal = 32.dp, vertical = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.playback_error_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            error.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canTranscode) {
                    Button(
                        onClick = { onPlayWithTranscoding?.invoke() },
                        glassProminent = true,
                        modifier = Modifier.focusRequester(focusRequester),
                    ) {
                        Text(text = stringResource(R.string.play_with_transcoding))
                    }
                }
                TextButton(
                    stringRes = R.string.send_app_logs,
                    onClick = { errorViewModel.sendLogs() },
                    glassFilled = true,
                    modifier = if (canTranscode) Modifier else Modifier.focusRequester(focusRequester),
                )
            }
            Text(
                text = stringResource(R.string.playback_error_back_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}
