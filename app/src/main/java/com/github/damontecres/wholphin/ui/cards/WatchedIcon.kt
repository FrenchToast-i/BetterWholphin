package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@Composable
fun WatchedIcon(
    modifier: Modifier = Modifier,
    padding: Dp = 2.dp,
) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = WatchedIconColor(),
        modifier =
            modifier
                .background(WatchedIconBackground(), shape = CircleShape)
                .border(.5.dp, Color.Black, CircleShape)
                .padding(padding),
    )
}

@Composable
fun WatchedIconBackground(): Color {
    // The glass scheme's `border` is white so that cards and chips read as lit edges, which is
    // not a fill: used as one it produced a white disc. The badge takes the material's white
    // instead, and its tick goes dark (below).
    if (LocalLiquidGlass.current) return Color.White
    return when (LocalTheme.current) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.BOLD_BLUE,
        AppThemeColors.RED,
        AppThemeColors.BROWN,
        -> MaterialTheme.colorScheme.border.copy(alpha = 1f)

        AppThemeColors.OLED_BLACK -> MaterialTheme.colorScheme.secondaryContainer
    }
}

@Composable
fun WatchedIconColor(): Color {
    // White-on-white was invisible: a watched poster grew a blank dot instead of a tick. The
    // white disc with dark glyph is the same signal as a selected bar pill.
    if (LocalLiquidGlass.current) return Color.Black
    return when (LocalTheme.current) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.BOLD_BLUE,
        AppThemeColors.OLED_BLACK,
        AppThemeColors.RED,
        AppThemeColors.BROWN,
        -> Color.White // MaterialTheme.colorScheme.onSurface
    }
}

@PreviewTvSpec
@Composable
private fun WatchedIconPreview() {
    WholphinTheme {
        WatchedIcon(Modifier.size(64.dp))
    }
}
