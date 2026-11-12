package com.github.damontecres.wholphin.ui.search

import android.view.Gravity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.glassDialogSurface
import com.github.damontecres.wholphin.ui.preferences.SwitchColors
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

@Composable
fun SearchViewOptionsDialog(
    combinedResults: Boolean,
    onCombinedResultsChange: (Boolean) -> Unit,
    voiceSearchButtonVisible: Boolean,
    onVoiceSearchButtonVisibleChange: (Boolean) -> Unit,
    onClickFilterTypes: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.CENTER)

        val glass = LocalLiquidGlass.current
        Box(
            modifier =
                Modifier
                    .width(400.dp)
                    // The old hand-rolled background was an opaque elevation surface, which on a
                    // glass page read as a grey card pasted over the artwork. The shared dialog
                    // surface gives the popup the same smoked material as every other menu, and
                    // its tint fallback is what a popup window can honestly use.
                    .glassDialogSurface(
                        shape = if (glass) GlassTokens.panelShape else RoundedCornerShape(28.dp),
                        classicElevation = 3.dp,
                    ).padding(24.dp),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (glass) Color.White else LocalContentColor.current,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.view_options),
                        style = if (glass) GlassType.pageTitle else MaterialTheme.typography.headlineSmall,
                        color = LocalContentColor.current,
                    )

                    ListItem(
                        selected = false,
                        headlineContent = {
                            Text(stringResource(R.string.combined_search_results))
                        },
                        supportingContent = {
                            Text(
                                if (combinedResults) {
                                    stringResource(R.string.combined_search_results_on)
                                } else {
                                    stringResource(R.string.combined_search_results_off)
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = combinedResults,
                                onCheckedChange = onCombinedResultsChange,
                                colors = SwitchColors(),
                            )
                        },
                        onClick = { onCombinedResultsChange(!combinedResults) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ListItem(
                        selected = false,
                        headlineContent = {
                            Text(stringResource(R.string.show_voice_search_button))
                        },
                        supportingContent = {
                            Text(
                                if (voiceSearchButtonVisible) {
                                    stringResource(R.string.visible_ui)
                                } else {
                                    stringResource(R.string.hidden_ui)
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = voiceSearchButtonVisible,
                                onCheckedChange = onVoiceSearchButtonVisibleChange,
                                colors = SwitchColors(),
                            )
                        },
                        onClick = { onVoiceSearchButtonVisibleChange(!voiceSearchButtonVisible) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ListItem(
                        selected = false,
                        headlineContent = {
                            Text(stringResource(R.string.include_types))
                        },
                        onClick = onClickFilterTypes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
