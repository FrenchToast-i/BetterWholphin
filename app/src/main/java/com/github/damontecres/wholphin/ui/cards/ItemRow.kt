package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun <T> ItemRow(
    title: String,
    items: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    showViewMore: Boolean = false,
    /**
     * A small line under the heading, aligned with it: a source credit, a count, or whatever
     * else the row owes the viewer. It belongs here rather than after the cards, because a
     * footer on the last shelf is the fragment left floating under the hero when the next row
     * scrolls into view.
     */
    subtitle: (@Composable () -> Unit)? = null,
    viewMoreCardContent: @Composable (Modifier) -> Unit = {},
) {
    val state = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    var position by rememberInt()
    // Glass mode insets the whole page, so the row adds no padding of its own: a shelf's first
    // card then starts on the same vertical line as the hero above it and the page title, instead
    // of sitting one row-gap to the right of everything else.
    val glass = LocalLiquidGlass.current
    val edgePadding = if (glass) 0.dp else horizontalPadding

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier.focusProperties {
                onEnter = {
                    focusRequester.tryRequestFocus()
                }
            },
    ) {
        // Aligned with the first card, not with the row's edge: a heading that sits 8dp inside
        // the shelf it labels is one of those details that makes a layout feel assembled in a
        // hurry.
        ItemRowTitle(title, startPadding = edgePadding)
        subtitle?.let { content ->
            Box(modifier = Modifier.padding(start = edgePadding)) {
                content()
            }
        }

        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(horizontal = edgePadding, vertical = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(firstFocus)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(items) { index, item ->
                val cardModifier =
                    remember(index, position) {
                        if (index == position) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                    }

                val onClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnClickItem(index, item)
                        }
                    }

                val onLongClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnLongClickItem(index, item)
                        }
                    }

                cardContent.invoke(
                    index,
                    item,
                    cardModifier,
                    onClick,
                    onLongClick,
                )
            }
            if (showViewMore) {
                item {
                    val cardModifier =
                        remember(items.size, position) {
                            if (position == items.size) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            }
                        }
                    viewMoreCardContent.invoke(cardModifier)
                }
            }
        }
    }
}

@Composable
@NonRestartableComposable
fun ItemRowTitle(
    title: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = 8.dp,
) {
    // In glass mode row titles sit directly on the artwork. They carry themselves with weight
    // and tracking rather than a drop shadow: a blurred dark halo behind every heading is the
    // tell of a skin applied over an old interface, not of a material.
    val glass = LocalLiquidGlass.current
    val style = if (glass) GlassType.rowTitle else MaterialTheme.typography.titleLarge
    Text(
        text = title,
        style = style,
        color = if (glass) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = startPadding),
    )
}
