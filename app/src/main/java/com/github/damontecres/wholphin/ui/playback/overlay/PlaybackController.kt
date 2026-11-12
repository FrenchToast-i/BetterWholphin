package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.WatchParty
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.MediaSegmentDto
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlaybackController(
    item: BaseItem?,
    nextState: OverlayViewState?,
    player: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    currentSegment: MediaSegmentDto?,
    onChangeState: (OverlayViewState) -> Unit,
    modifier: Modifier = Modifier,
    sleepTimerRemainingSeconds: Int? = null,
    onSleepTimerClick: () -> Unit = {},
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    watchParty: WatchParty? = null,
    onWatchPartyClick: () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Controller(
            title = item?.title,
            subtitle = item?.subtitleLong,
            player = player,
            controllerViewState = controllerViewState,
            showPlay = showPlay,
            previousEnabled = previousEnabled,
            nextEnabled = nextEnabled,
            seekEnabled = seekEnabled,
            seekBack = seekBack,
            skipBackOnResume = skipBackOnResume,
            seekForward = seekForward,
            onPlaybackActionClick = onPlaybackActionClick,
            onClickPlaybackDialogType = onClickPlaybackDialogType,
            onSeekProgress = onSeekBarChange,
            seekBarInteractionSource = seekBarInteractionSource,
            currentSegment = currentSegment,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onSleepTimerClick = onSleepTimerClick,
            watchParty = watchParty,
            onWatchPartyClick = onWatchPartyClick,
            modifier = Modifier,
        )
        when (nextState) {
            OverlayViewState.CHAPTERS -> {
                Text(
                    text = stringResource(R.string.chapters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 0.dp)
                            .onFocusChanged {
                                if (it.isFocused) onChangeState.invoke(nextState)
                            }.focusable(),
                )
            }

            OverlayViewState.QUEUE -> {
                Text(
                    text = stringResource(R.string.queue),
                    style = MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 0.dp)
                            .onFocusChanged {
                                if (it.isFocused) onChangeState.invoke(nextState)
                            }.focusable(),
                )
            }

            else -> {
                Unit
            }
        }
    }
}

internal val titleTextSize = 28.sp
internal val subtitleTextSize = 18.sp

/**
 * A wrapper for the playback controls to show title and other information, plus the actual controls
 *
 * @see PlaybackControls
 */
@Composable
fun Controller(
    title: String?,
    player: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekProgress: (Long) -> Unit,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sleepTimerRemainingSeconds: Int? = null,
    onSleepTimerClick: () -> Unit = {},
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    watchParty: WatchParty? = null,
    onWatchPartyClick: () -> Unit = {},
) {
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    val verticalOffset by animateDpAsState(
        targetValue = if (seekBarFocused) (-32).dp else 0.dp,
        label = "TitleBumpOffset",
        animationSpec =
            spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = Dp.VisibilityThreshold,
            ),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .offset(y = verticalOffset),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = titleTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.fillMaxWidth(.75f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
            ) {
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = subtitleTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.fillMaxWidth(.75f),
                    )
                }

                val context = LocalContext.current
                var endTimeStr by remember { mutableStateOf("...") }
                LaunchedEffect(player, context) {
                    while (isActive) {
                        val remaining =
                            (player.duration - player.currentPosition)
                                .div(player.playbackParameters.speed)
                                .toLong()
                                .milliseconds
                        val endTime = LocalTime.now().plusSeconds(remaining.inWholeSeconds)
                        endTimeStr = formatTime(context, endTime)
                        delay(1.seconds)
                    }
                }
                val party = watchParty
                if (party != null) {
                    // A party replaces the end-time readout rather than sitting beside it: the
                    // header row has one spare slot, and "who is in this with me" is the more
                    // useful fact while the room controls the transport. It is also the way back
                    // to the dialog (leave, or see who joined) without leaving the player.
                    Button(
                        onClick = onWatchPartyClick,
                        glassFilled = true,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        contentHeight = 32.dp,
                        modifier = Modifier.padding(end = 32.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fa_users),
                            fontFamily = FontAwesome,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.watch_party_members, party.members),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.ends_at, endTimeStr),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .padding(end = 32.dp),
                    )
                }
            }
        }
        PlaybackControls(
            modifier = Modifier.fillMaxWidth(),
            player = player,
            onPlaybackActionClick = onPlaybackActionClick,
            controllerViewState = controllerViewState,
            onSeekProgress = {
                onSeekProgress(it)
            },
            showPlay = showPlay,
            previousEnabled = previousEnabled,
            nextEnabled = nextEnabled,
            seekEnabled = seekEnabled,
            seekBarInteractionSource = seekBarInteractionSource,
            seekBarIntervals = 16,
            seekBack = seekBack,
            seekForward = seekForward,
            skipBackOnResume = skipBackOnResume,
            currentSegment = currentSegment,
            onClickPlaybackDialogType = onClickPlaybackDialogType,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onSleepTimerClick = onSleepTimerClick,
        )
    }
}
