package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.ui.components.GlassMenuSheet
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsAspectRatio
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsColumns
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsContentScale
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsDetailHeader
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsImageType
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsReset
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsShowTitles
import com.github.damontecres.wholphin.ui.components.ViewOptions.Companion.ViewOptionsSpacing
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import kotlinx.serialization.Serializable

@Composable
fun CollectionViewOptionsDialog(
    viewOptions: CollectionViewOptions,
    onDismissRequest: () -> Unit,
    onViewOptionsChange: (CollectionViewOptions) -> Unit,
    defaultViewOptions: CollectionViewOptions = CollectionViewOptions(),
) {
    val options =
        if (viewOptions.separateTypes) CollectionViewOptions.SeparateOptions else CollectionViewOptions.MixedOptions
    GlassMenuSheet(
        title = stringResource(R.string.view_options),
        onDismissRequest = onDismissRequest,
    ) {
        item {
            val pref = CollectionViewOptions.SeparateTypes
            val interactionSource = remember { MutableInteractionSource() }
            ComposablePreference(
                preference = pref,
                value = viewOptions.separateTypes,
                onNavigate = {},
                onValueChange = { newValue ->
                    onViewOptionsChange.invoke(pref.setter(viewOptions, newValue))
                },
                interactionSource = interactionSource,
                modifier = Modifier,
                onClickPreference = {},
            )
        }
        items(options, key = { it.title }) { pref ->
            pref as AppPreference<ViewOptions, Any>
            val interactionSource = remember { MutableInteractionSource() }
            val value = pref.getter.invoke(viewOptions.cardViewOptions)
            ComposablePreference(
                preference = pref,
                value = value,
                onNavigate = {},
                onValueChange = { newValue ->
                    val newCardViewOptions =
                        pref.setter(viewOptions.cardViewOptions, newValue)
                    onViewOptionsChange.invoke(viewOptions.copy(cardViewOptions = newCardViewOptions))
                },
                interactionSource = interactionSource,
                modifier = Modifier.animateItem(),
                onClickPreference = { pref ->
                    if (pref == ViewOptionsReset) {
                        onViewOptionsChange.invoke(defaultViewOptions)
                    }
                },
            )
        }
    }
}

@Serializable
data class CollectionViewOptions(
    val separateTypes: Boolean = true,
    val cardViewOptions: ViewOptions =
        ViewOptions(
            showDetails = true,
            showTitles = true,
        ),
) {
    companion object {
        val SeparateTypes =
            AppSwitchPreference<CollectionViewOptions>(
                title = R.string.separate_types,
                defaultValue = false,
                getter = { it.separateTypes },
                setter = { vo, value -> vo.copy(separateTypes = value) },
            )

        val MixedOptions =
            listOf(
                ViewOptionsImageType,
                ViewOptionsAspectRatio,
                ViewOptionsDetailHeader,
                ViewOptionsShowTitles,
                ViewOptionsColumns,
                ViewOptionsSpacing,
                ViewOptionsContentScale,
                ViewOptionsReset,
            )

        val SeparateOptions =
            listOf(
                ViewOptionsDetailHeader,
                ViewOptionsShowTitles,
                ViewOptionsReset,
            )
    }
}
