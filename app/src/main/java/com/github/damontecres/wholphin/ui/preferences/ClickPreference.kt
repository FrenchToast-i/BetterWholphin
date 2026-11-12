package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem

@Composable
fun ClickPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            PreferenceTitle(title)
        },
        supportingContent = {
            PreferenceSummary(summary)
        },
        trailingContent = {
            GlassChevron(modifier = Modifier.padding(start = 12.dp, end = 2.dp))
        },
        colors = glassListItemColors(),
        shape = glassListItemShape(),
        scale = glassListItemScale(),
        glow = glassListItemGlow(),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}
