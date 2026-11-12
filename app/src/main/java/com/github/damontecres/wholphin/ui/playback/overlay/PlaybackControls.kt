@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.github.damontecres.wholphin.ui.playback.overlay

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.services.SleepTimerPlan
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.components.glassDialogSurface
import com.github.damontecres.wholphin.ui.components.glassMenuRowColors
import com.github.damontecres.wholphin.ui.components.glassMenuRowGlow
import com.github.damontecres.wholphin.ui.components.glassMenuRowShape
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.skipStringRes
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.theme.glassPlayerDeck
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Possible actions the user can take during playback
 */
sealed interface PlaybackAction {
    data object ShowDebug : PlaybackAction

    data object ShowPlaylist : PlaybackAction

    data object ShowVideoFilterDialog : PlaybackAction

    data object SearchCaptions : PlaybackAction

    data class ToggleCaptions(
        val index: Int,
    ) : PlaybackAction

    data class ToggleAudio(
        val index: Int,
    ) : PlaybackAction

    data class PlaybackSpeed(
        val value: Float,
    ) : PlaybackAction

    data class Scale(
        val scale: ContentScale,
    ) : PlaybackAction

    data object Previous : PlaybackAction

    data object Next : PlaybackAction

    data class ChangeBitrate(
        val maxBitrate: Long,
    ) : PlaybackAction
}

/**
 * UI for actual playback controls such as seek bar, play/pause and seek buttons
 */
@OptIn(UnstableApi::class)
@Composable
fun PlaybackControls(
    player: Player,
    controllerViewState: ControllerViewState,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekProgress: (Long) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBarIntervals: Int,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    sleepTimerRemainingSeconds: Int? = null,
    onSleepTimerClick: () -> Unit = {},
    initialFocusRequester: FocusRequester = remember { FocusRequester() },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val scope = rememberCoroutineScope()

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val onControllerInteraction = {
        scope.launch(ExceptionHandler()) {
            bringIntoViewRequester.bringIntoView()
        }
        controllerViewState.pulseControls()
    }
    LaunchedEffect(controllerViewState.controlsVisible) {
        if (controllerViewState.controlsVisible) {
            initialFocusRequester.tryRequestFocus()
        }
    }
    val glass = LocalLiquidGlass.current
    Column(
        modifier =
            modifier
                .then(
                    if (glass) {
                        // The deck floats clear of the screen edges so the picture keeps its frame,
                        // and the transport sits on the material instead of on the scrim alone.
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .glassPlayerDeck()
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    } else {
                        Modifier
                    },
                ).bringIntoViewRequester(bringIntoViewRequester),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SeekBar(
            player = player,
            controllerViewState = controllerViewState,
            onSeekProgress = onSeekProgress,
            interactionSource = seekBarInteractionSource,
            isEnabled = seekEnabled,
            intervals = seekBarIntervals,
            seekBack = seekBack,
            seekForward = seekForward,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            modifier =
                Modifier
                    .padding(vertical = 0.dp)
                    .fillMaxWidth(.95f),
        )
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
        ) {
            LeftPlaybackButtons(
                onControllerInteraction = onControllerInteraction,
                onClickPlaybackDialogType = onClickPlaybackDialogType,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            PlaybackButtons(
                player = player,
                initialFocusRequester = initialFocusRequester,
                onControllerInteraction = onControllerInteraction,
                onPlaybackActionClick = onPlaybackActionClick,
                showPlay = showPlay,
                previousEnabled = previousEnabled,
                nextEnabled = nextEnabled,
                seekBack = seekBack,
                seekForward = seekForward,
                skipBackOnResume = skipBackOnResume,
                modifier = Modifier.align(Alignment.Center),
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                currentSegment?.let { segment ->
                    TextButton(
                        stringRes = segment.type.skipStringRes,
                        onClick = {
                            player.seekTo(segment.endTicks.ticks.inWholeMilliseconds)
                        },
                        modifier =
                            Modifier
                                .align(Alignment.CenterVertically)
                                .padding(end = 32.dp),
                    )
                }
                RightPlaybackButtons(
                    onControllerInteraction = onControllerInteraction,
                    onClickPlaybackDialogType = onClickPlaybackDialogType,
                    sleepTimerActive = sleepTimerRemainingSeconds != null,
                    onSleepTimerClick = {
                        onControllerInteraction()
                        onSleepTimerClick()
                    },
                    modifier = Modifier,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(
    player: Player,
    isEnabled: Boolean,
    intervals: Int,
    controllerViewState: ControllerViewState,
    onSeekProgress: (Long) -> Unit,
    seekBack: Duration,
    seekForward: Duration,
    modifier: Modifier = Modifier,
    sleepTimerRemainingSeconds: Int? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var bufferedProgress by remember(player) { mutableFloatStateOf(player.bufferedPosition.toFloat() / player.duration) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var progress by remember(player) { mutableFloatStateOf(player.currentPosition.toFloat() / player.duration) }
    LaunchedEffect(player) {
        while (isActive) {
            bufferedProgress = player.bufferedPosition.toFloat() / player.duration
            position = player.currentPosition
            progress = player.currentPosition.toFloat() / player.duration
            delay(250L)
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IntervalSeekBarImpl(
            progress = progress,
            bufferedProgress = bufferedProgress,
            onSeek = {
                onSeekProgress(it)
            },
            controllerViewState = controllerViewState,
//            intervals = intervals,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = isEnabled,
            durationMs = player.duration,
            seekBack = seekBack,
            seekForward = seekForward,
        )
        SeekTimecodes(
            positionMs = position,
            durationMs = player.duration,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SeekTimecodes(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    sleepTimerRemainingSeconds: Int? = null,
) {
    val currentLayoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val resources = LocalResources.current
            val positionSec = positionMs / 1000
            val remainingSec = (durationMs - positionMs) / 1000
            val positionText =
                remember(positionSec) { resources.formatDuration(positionSec.seconds) }
            val remainingText =
                remember(remainingSec) { "-${resources.formatDuration(remainingSec.seconds)}" }
            CompositionLocalProvider(LocalLayoutDirection provides currentLayoutDirection) {
                Text(
                    text = positionText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .padding(8.dp),
                )
            }
            // The countdown sits between the two timecodes rather than beside the transport:
            // the row already has the whole width, and a fourth control on the right would have
            // pushed the seek bar's own labels out of the deck.
            sleepTimerRemainingSeconds?.let { remainingSeconds ->
                CompositionLocalProvider(LocalLayoutDirection provides currentLayoutDirection) {
                    Text(
                        text =
                            stringResource(
                                R.string.sleep_timer_remaining,
                                resources.formatDuration(SleepTimerPlan.remainingMinutes(remainingSeconds).minutes),
                            ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .padding(8.dp),
                    )
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides currentLayoutDirection) {
                Text(
                    text = remainingText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .padding(8.dp),
                )
            }
        }
    }
}

val buttonSpacing = 12.dp

@Composable
fun LeftPlaybackButtons(
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
    ) {
        PlaybackButton(
            iconRes = R.drawable.vector_settings,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.SETTINGS)
            },
            enabled = true,
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier,
        )
    }
}

@Composable
fun RightPlaybackButtons(
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    modifier: Modifier = Modifier,
    sleepTimerActive: Boolean = false,
    onSleepTimerClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
    ) {
        // Captions
        PlaybackButton(
            enabled = true,
            iconRes = R.drawable.captions_svgrepo_com,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.CAPTIONS)
            },
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier,
        )
        // Audio
        PlaybackFaButton(
            enabled = true,
            iconRes = R.string.fa_volume_high,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.AUDIO)
            },
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier,
        )
        onSleepTimerClick?.let { onClick ->
            // At the end of the deck because it is the least-used key, and lit while it runs so
            // the overlay explains the volume drop the fade causes at the end of the countdown.
            PlaybackFaButton(
                enabled = true,
                iconRes = R.string.fa_clock,
                onClick = {
                    onControllerInteraction.invoke()
                    onClick()
                },
                onControllerInteraction = onControllerInteraction,
                textColor = if (sleepTimerActive) MaterialTheme.colorScheme.border else Color.Unspecified,
                modifier = Modifier,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackButtons(
    player: Player,
    initialFocusRequester: FocusRequester,
    onControllerInteraction: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
        ) {
            PlaybackButton(
                iconRes = R.drawable.baseline_skip_previous_24,
                onClick = {
                    onControllerInteraction.invoke()
                    onPlaybackActionClick.invoke(PlaybackAction.Previous)
                },
                enabled = previousEnabled,
                onControllerInteraction = onControllerInteraction,
            )
            PlaybackButton(
                iconRes = R.drawable.baseline_fast_rewind_24,
                onClick = {
                    onControllerInteraction.invoke()
                    player.seekBack(seekBack)
                },
                onControllerInteraction = onControllerInteraction,
            )
            PlaybackButton(
                modifier = Modifier.focusRequester(initialFocusRequester),
                iconRes = if (showPlay) R.drawable.baseline_play_arrow_24 else R.drawable.baseline_pause_24,
                onClick = {
                    onControllerInteraction.invoke()
                    if (showPlay) {
                        player.play()
                        skipBackOnResume?.let {
                            player.seekBack(it)
                        }
                    } else {
                        player.pause()
                    }
                },
                onControllerInteraction = onControllerInteraction,
            )
            PlaybackButton(
                iconRes = R.drawable.baseline_fast_forward_24,
                onClick = {
                    onControllerInteraction.invoke()
                    player.seekForward(seekForward)
                },
                onControllerInteraction = onControllerInteraction,
            )
            PlaybackButton(
                iconRes = R.drawable.baseline_skip_next_24,
                onClick = {
                    onControllerInteraction.invoke()
                    onPlaybackActionClick.invoke(PlaybackAction.Next)
                },
                enabled = nextEnabled,
                onControllerInteraction = onControllerInteraction,
            )
        }
    }
}

@Composable
fun PlaybackButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val selectedColor = MaterialTheme.colorScheme.border
    val glass = LocalLiquidGlass.current
    var focused by remember { mutableStateOf(false) }
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(4.dp),
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(36.dp, 36.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    onControllerInteraction.invoke()
                },
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(iconRes),
            contentDescription = "",
            // The glass scheme's `border` (the focused fill) and `onSurface` (the glyph) are
            // both white, so the transport's focused key — the one control the viewer lands on
            // when the overlay opens — drew a white play triangle on a white disc. Focus is a
            // white fill with a dark glyph, the same signal as a selected bar pill.
            tint =
                when {
                    glass && focused -> Color.Black
                    LocalTheme.current == AppThemeColors.OLED_BLACK -> LocalContentColor.current
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
fun PlaybackFaButton(
    @StringRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    textColor: Color = Color.Unspecified,
) {
    val selectedColor = MaterialTheme.colorScheme.border
    val glass = LocalLiquidGlass.current
    var focused by remember { mutableStateOf(false) }
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(4.dp),
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(36.dp, 36.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    onControllerInteraction.invoke()
                },
    ) {
        Text(
            text = stringResource(iconRes),
            fontSize = 18.sp,
            fontFamily = FontAwesome,
            textAlign = TextAlign.Center,
            color =
                when {
                    textColor.isSpecified -> textColor
                    // White glyph on the white focus fill: same fix as the transport keys.
                    glass && focused -> Color.Black
                    LocalTheme.current == AppThemeColors.OLED_BLACK -> LocalContentColor.current
                    else -> MaterialTheme.colorScheme.onSurface
                },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically),
        )
    }
}

/**
 * Show a dialog aligned to the bottom of the screen instead of centered.
 *
 * @param gravity The [Gravity] to align the dialog to left or right
 */
@Composable
fun <T> BottomDialog(
    choices: List<BottomDialogItem<T>>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int, BottomDialogItem<T>) -> Unit,
    gravity: Int,
    currentChoice: BottomDialogItem<T>? = null,
    header: String? = null,
) {
    val initialIndex =
        remember(choices, currentChoice) { choices.indexOfFirstOrNull { it == currentChoice } } ?: 0
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialIndex,
        )
    val focusRequesters = remember(choices.size) { List(choices.size) { FocusRequester() } }
    val bringIntoViewRequesters =
        remember(choices.size) { List(choices.size) { BringIntoViewRequester() } }
    if (currentChoice != null) {
        LaunchedEffect(Unit) {
            bringIntoViewRequesters.getOrNull(initialIndex)?.bringIntoView()
            focusRequesters.getOrNull(initialIndex)?.tryRequestFocus()
        }
    }
    // TODO enforcing a width ends up ignore the gravity
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.BOTTOM or gravity) // Move down, by default dialogs are in the centre
            window.setDimAmount(0f) // Remove dimmed background of ongoing playback
        }

        val glass = LocalLiquidGlass.current
        Box(
            modifier =
                Modifier
                    .wrapContentSize()
                    .padding(8.dp)
                    .glassDialogSurface(
                        shape = if (glass) GlassTokens.panelShape else RoundedCornerShape(8.dp),
                        classicElevation = 1.dp,
                    ),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
//                        .widthIn(max = 240.dp)
                        .wrapContentWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                header?.let { title ->
                    // A sheet of bare options with no title makes the viewer read the whole list to
                    // learn what they are choosing; the timer is the one menu here whose rows do
                    // not name themselves ("In 30m" of what?).
                    item {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color =
                                if (LocalLiquidGlass.current) {
                                    Color.White.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
                itemsIndexed(choices) { index, choice ->
                    val interactionSource = remember { MutableInteractionSource() }
                    ListItem(
                        selected = choice == currentChoice,
                        enabled = choice.enabled,
                        shape = glassMenuRowShape(),
                        colors = glassMenuRowColors(),
                        glow = glassMenuRowGlow(),
                        onClick = {
                            onDismissRequest()
                            onSelectChoice(index, choice)
                        },
                        leadingContent = {
                            SelectedLeadingContent(choice == currentChoice)
                        },
                        headlineContent = {
                            Text(
                                text = choice.headline,
                            )
                        },
                        supportingContent = {
                            choice.supporting?.let {
                                Text(
                                    text = it,
                                )
                            }
                        },
                        interactionSource = interactionSource,
                        modifier =
                            Modifier
                                .bringIntoViewRequester(bringIntoViewRequesters[index])
                                .focusRequester(focusRequesters[index]),
                    )
                }
            }
        }
    }
}

data class BottomDialogItem<T>(
    val data: T,
    val headline: String,
    val supporting: String?,
    val enabled: Boolean = true,
)

@PreviewTvSpec
@Composable
private fun ButtonPreview() {
    WholphinTheme {
        Row(Modifier.background(Color.Red)) {
            PlaybackButton(
                iconRes = R.drawable.baseline_play_arrow_24,
                onClick = {},
                onControllerInteraction = {},
            )
            PlaybackFaButton(
                iconRes = R.string.fa_volume_high,
                onClick = {},
                onControllerInteraction = {},
                textColor = Color.Green,
            )
        }
    }
}
