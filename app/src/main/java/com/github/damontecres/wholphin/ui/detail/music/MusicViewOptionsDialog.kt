package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.components.GlassMenuSheet
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference

@Composable
fun MusicViewOptionsDialog(
    appPreferences: AppPreferences,
    onDismissRequest: () -> Unit,
    onViewOptionsChange: (AppPreferences) -> Unit,
    onEnableVisualizer: () -> Unit,
) {
    val items = remember { getMusicPreferences() }

    GlassMenuSheet(
        title = stringResource(R.string.view_options),
        onDismissRequest = onDismissRequest,
    ) {
        items(items) { pref ->
            pref as AppPreference<AppPreferences, Any>
            val interactionSource = remember { MutableInteractionSource() }
            val value = pref.getter.invoke(appPreferences)
            // Using title is a bit hacky
            when (pref.title) {
                R.string.show_visualizer -> {
                    ComposablePreference(
                        preference = pref,
                        value = value,
                        onNavigate = {},
                        onValueChange = { newValue ->
                            newValue as Boolean
                            if (newValue) {
                                onEnableVisualizer.invoke()
                            } else {
                                onViewOptionsChange.invoke(
                                    pref.setter(
                                        appPreferences,
                                        newValue,
                                    ),
                                )
                            }
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier,
                        onClickPreference = { pref ->
                        },
                    )
                }

                else -> {
                    ComposablePreference(
                        preference = pref,
                        value = value,
                        onNavigate = {},
                        onValueChange = { newValue ->
                            onViewOptionsChange.invoke(pref.setter(appPreferences, newValue))
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier,
                        onClickPreference = { pref ->
                        },
                    )
                }
            }
        }
    }
}
