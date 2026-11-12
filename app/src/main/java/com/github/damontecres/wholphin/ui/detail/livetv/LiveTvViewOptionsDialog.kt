package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.liveTvPreferences
import com.github.damontecres.wholphin.ui.components.GlassMenuSheet
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference

@Composable
fun LiveTvViewOptionsDialog(
    preferences: AppPreferences,
    onDismissRequest: () -> Unit,
    onViewOptionsChange: (AppPreferences) -> Unit,
) {
    GlassMenuSheet(
        title = stringResource(R.string.view_options),
        onDismissRequest = onDismissRequest,
    ) {
        items(liveTvPreferences) { pref ->
            pref as AppPreference<AppPreferences, Any>
            val interactionSource = remember { MutableInteractionSource() }
            val value = pref.getter.invoke(preferences)
            ComposablePreference(
                preference = pref,
                value = value,
                onNavigate = {},
                onValueChange = { newValue ->
                    onViewOptionsChange.invoke(pref.setter(preferences, newValue))
                },
                interactionSource = interactionSource,
                modifier = Modifier,
            )
        }
    }
}
