package com.github.damontecres.wholphin.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.glassCapsule

/**
 * The search row: optional mic, the field, optional view-options disc.
 *
 * Both search surfaces (the library search and the Seerr Discover search) draw this row, because
 * the behaviours below are the ones a viewer notices when they are wrong, and a second copy of
 * them had already started to rot: the Discover search opened with the microphone armed instead of
 * a caret, and its Back handler moved focus instead of letting the viewer leave the page.
 *
 * Rules encoded here, once:
 * - A read-only field treats direction keys as navigation. Otherwise the text field eats them to
 *   move a caret nobody can see and D-pad down never reaches the results.
 * - Back is never intercepted here. The on-screen keyboard is its own window and already takes
 *   the first Back to close itself, so an app handler only ever sees the presses that follow the
 *   keyboard being gone — it cannot close a keyboard that is already closed, and it used to
 *   swallow them. The page then took three presses to leave. Moving focus on Back was worse
 *   still: the field keeps focus when nothing follows it, so every press was swallowed.
 * - Entering the page requests focus on the field (glass) or the row (classic); attaching the
 *   request to a focus group lands on its first focusable child, which is the mic.
 */
@Composable
fun SearchFieldRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    voiceInputManager: VoiceInputManager?,
    focusRequester: FocusRequester,
    glass: Boolean,
    modifier: Modifier = Modifier,
    showVoiceSearch: Boolean = true,
    downFocusRequester: FocusRequester? = null,
    onOpenViewOptions: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchActive by remember { mutableStateOf(false) }

    fun setTyping(active: Boolean) {
        isSearchActive = active
    }

    Row(
        horizontalArrangement =
            if (glass) {
                Arrangement.spacedBy(GlassTokens.capsuleGap, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(0.dp)
            },
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .focusGroup()
                .focusRestorer(focusRequester)
                .then(
                    if (glass) {
                        Modifier
                    } else {
                        Modifier.focusRequester(focusRequester)
                    },
                ),
    ) {
        AnimatedVisibility(
            visible = showVoiceSearch,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            val onSpeechResult = { spokenText: String ->
                setTyping(false)
                onQueryChange(spokenText)
                onSearch()
            }
            if (glass) {
                GlassIconCapsule {
                    VoiceSearchButton(
                        onSpeechResult = onSpeechResult,
                        voiceInputManager = voiceInputManager,
                        size = GlassTokens.pillHeight,
                        glass = true,
                    )
                }
            } else {
                VoiceSearchButton(
                    onSpeechResult = onSpeechResult,
                    voiceInputManager = voiceInputManager,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }

        SearchEditTextBox(
            value = query,
            onValueChange = {
                setTyping(true)
                onQueryChange(it)
            },
            onSearchClick = onSearch,
            readOnly = !isSearchActive,
            height = if (glass) GlassTokens.pillHeight else 40.dp,
            placeholder =
                if (glass) {
                    {
                        Text(
                            text = stringResource(R.string.glass_search_placeholder),
                            style = GlassType.tab,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                } else {
                    null
                },
            glass = glass,
            modifier =
                Modifier
                    .then(
                        if (downFocusRequester != null) {
                            // Below the field the nearest chip is "Clear" (it sits at the end of
                            // the row), so down is routed to the newest query explicitly:
                            // clearing the history by accident is not a navigation the viewer
                            // asked for.
                            Modifier.focusProperties { down = downFocusRequester }
                        } else {
                            Modifier
                        },
                    ).then(
                        if (glass) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    ).onFocusChanged { state ->
                        // Leaving the field ends the typing session, so the read-only state
                        // cannot outlive the focus that armed it.
                        if (!state.isFocused) setTyping(false)
                    }.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            val direction =
                                when (event.key) {
                                    Key.DirectionUp -> FocusDirection.Up
                                    Key.DirectionDown -> FocusDirection.Down
                                    Key.DirectionLeft -> FocusDirection.Left
                                    Key.DirectionRight -> FocusDirection.Right
                                    else -> null
                                }
                            if (direction != null && !isSearchActive) {
                                focusManager.moveFocus(direction)
                                return@onPreviewKeyEvent true
                            }
                        }
                        val isActivationKey =
                            event.key in listOf(Key.DirectionCenter, Key.Enter)
                        if (event.type == KeyEventType.KeyUp && isActivationKey && !isSearchActive) {
                            setTyping(true)
                            keyboardController?.show()
                            true
                        } else {
                            false
                        }
                    },
        )

        if (onOpenViewOptions != null) {
            if (glass) {
                // A circular filter capsule, like the bar. The label would not fit inside a
                // 50dp disc, so the icon carries the action and focus is a white wash.
                GlassIconCapsule {
                    ExpandableFaButton(
                        title = R.string.view_options,
                        iconStringRes = R.string.fa_sliders,
                        onClick = onOpenViewOptions,
                        size = GlassTokens.pillHeight,
                        glass = true,
                        expandLabel = false,
                    )
                }
            } else {
                ExpandableFaButton(
                    title = R.string.view_options,
                    iconStringRes = R.string.fa_sliders,
                    onClick = onOpenViewOptions,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * One circular control in the bar's material.
 *
 * The mic and the filter hover over the artwork beside the search capsule; giving each its own
 * pane keeps them part of the same material instead of the old flat circular buttons, and the
 * fixed size means they line up with the 50dp search field between them.
 */
@Composable
private fun GlassIconCapsule(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(GlassTokens.pillHeight)
                .glassCapsule(shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
