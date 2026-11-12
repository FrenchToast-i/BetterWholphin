package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchColors
import androidx.tv.material3.SwitchDefaults
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.LocalTheme

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    summaryOn: String?,
    summaryOff: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = SwitchPreference(
    title = title,
    value = value,
    onClick = onClick,
    modifier = modifier,
    summary = if (value) summaryOn else summaryOff,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
)

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
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
            Switch(
                checked = value,
                onCheckedChange = { onClick.invoke() },
                colors = SwitchColors(),
            )
        },
        colors = glassListItemColors(),
        shape = glassListItemShape(),
        scale = glassListItemScale(),
        glow = glassListItemGlow(),
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
fun SwitchColors(): SwitchColors {
    if (LocalLiquidGlass.current) {
        // Glass mode is monochrome: a lit white track with a black thumb, which is the same
        // on/off signal as the selected pill in the top bar. The stock lavender track is the
        // last purple widget in an otherwise neutral sheet and reads as a leftover from the
        // classic theme.
        return SwitchDefaults.colors(
            checkedThumbColor = Color.Black,
            checkedTrackColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White.copy(alpha = 0.80f),
            uncheckedTrackColor = Color.Black.copy(alpha = 0.32f),
            uncheckedBorderColor = Color.White.copy(alpha = 0.28f),
        )
    }
    val theme = LocalTheme.current
    return when (theme) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        -> {
            SwitchDefaults.colors()
        }

        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.BOLD_BLUE,
        AppThemeColors.OLED_BLACK,
        AppThemeColors.RED,
        AppThemeColors.BROWN,
        -> {
            SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
