package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

@Composable
fun TvDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    elevation: Dp = 3.dp,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    // On glass the menu's fill is the smoked pane drawn by the call site, so the rows have to be
    // transparent rather than each painting an opaque elevation slab inside it. Focus is the same
    // wash of light the bar and the settings cards use.
    val glass = LocalLiquidGlass.current
    val backgroundColor =
        when {
            glass -> if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent
            focused -> MaterialTheme.colorScheme.inverseSurface
            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(elevation)
        }
    val contentColor =
        when {
            glass -> if (focused) Color.White else Color.White.copy(alpha = 0.90f)
            focused -> MaterialTheme.colorScheme.inverseOnSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        DropdownMenuItem(
            enabled = enabled,
            colors =
                MenuDefaults.itemColors(
                    textColor = contentColor,
                    leadingIconColor = contentColor,
                    trailingIconColor = contentColor,
                ),
            leadingIcon = leadingIcon,
            text = text,
            trailingIcon = trailingIcon,
            onClick = onClick,
            interactionSource = interactionSource,
            contentPadding = contentPadding,
            modifier =
                modifier.background(backgroundColor),
        )
    }
}
