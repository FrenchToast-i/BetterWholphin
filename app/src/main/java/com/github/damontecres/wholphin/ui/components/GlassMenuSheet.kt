package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * The view-options menu: one smoked pane holding a title and a focus-driven list of rows.
 *
 * Every library, collection, guide and music page opens the same menu, so it is built once here
 * rather than copied four times. The copies had already drifted — one carried the title outside
 * the pane, one had a different width in the classic theme, none of them inset their first and
 * last rows from the pane's own rim — and a menu is exactly the kind of surface where three
 * subtly different versions read as three different apps.
 *
 * The list is a scroll area, not a fixed column: ten rows cannot fit on a 540dp television, and
 * the pane has to stay shorter than the bar above it. The content is therefore inset *inside* the
 * clip, so a focused row lands against a straight edge instead of being sliced by the pane's
 * rounded bottom corner.
 */
@Composable
fun GlassMenuSheet(
    title: String,
    onDismissRequest: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    // The rows are the only focusable thing in this window; asking for focus on the sheet makes
    // the first row the page's starting point instead of leaving the remote to wander in from
    // whatever the page behind it had focused.
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    GlassMenuDialog(onDismissRequest = onDismissRequest) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .width(if (glass) 280.dp else 256.dp)
                    .heightIn(max = 380.dp)
                    .focusRequester(focusRequester)
                    .then(
                        if (glass) {
                            // A popup over the grid cannot sample the shell's layer, so this is
                            // the dialog material: a smoked pane with the same silhouette as the
                            // sheets it opens from.
                            Modifier.glassDialogSurface(shape = GlassTokens.panelShape)
                        } else {
                            Modifier.background(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                                shape = RoundedCornerShape(8.dp),
                            )
                        },
                    )
                    // Painted before the content, so it insets the rows rather than shrinking the
                    // pane: a row must not run into the pane's rim or its rounded corners.
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        ) {
            Text(
                text = title,
                style = if (glass) GlassType.sectionTitle else MaterialTheme.typography.titleMedium,
                color = if (glass) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp),
            )
            CompositionLocalProvider(
                LocalContentColor provides if (glass) Color.White else LocalContentColor.current,
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                    content = content,
                )
            }
        }
    }
}
