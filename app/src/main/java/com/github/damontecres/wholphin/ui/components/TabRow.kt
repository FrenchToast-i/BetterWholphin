package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.theme.glassCapsule
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.SortedMap

@Composable
fun TabRow(
    selectedTabIndex: Int,
    tabs: List<TabDetails>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex in tabs.indices) {
            state.animateScrollToItem(selectedTabIndex, -(state.layoutInfo.viewportSize.width / 3.5).toInt())
        }
    }
    var rowHasFocus by remember { mutableStateOf(false) }

    val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
    val currentOnClick by rememberUpdatedState(onClick)

    GlassTabStrip(modifier = modifier) {
        if (LocalLiquidGlass.current) {
            GlassTabScroller(
                tabs = tabs,
                selectedIndex = selectedTabIndex,
                rowHasFocus = rowHasFocus,
                onRowFocusChanged = { rowHasFocus = it },
                onEnterRequest = { direction ->
                    // If entering from left or right, use last or first tab. Otherwise use the
                    // selected tab.
                    val index = currentSelectedTabIndex
                    Timber.v("onEnter requestedFocusDirection=$direction, selectedTabIndex=$index")
                    when (direction) {
                        FocusDirection.Left -> tabs.lastOrNull()
                        FocusDirection.Right -> tabs.firstOrNull()
                        else -> tabs.getOrNull(index)
                    }
                },
                onClick = currentOnClick,
                ensureVisible = selectedTabIndex,
            )
        } else {
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onFocusChanged {
                            rowHasFocus = it.hasFocus
                        }.focusGroup()
                        .focusProperties {
                            onEnter = {
                                // If entering from left or right, use last or first tab
                                // Otherwise use the selected tab
                                val tab =
                                    when (requestedFocusDirection) {
                                        FocusDirection.Left -> tabs.lastOrNull()
                                        FocusDirection.Right -> tabs.firstOrNull()
                                        else -> tabs.getOrNull(currentSelectedTabIndex)
                                    }
                                (tab?.tabFocusRequester ?: FocusRequester.Default).tryRequestFocus()
                            }
                        },
            ) {
                itemsIndexed(tabs) { index, tab ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val onTabClick =
                        remember(index) {
                            {
                                currentOnClick(index)
                            }
                        }
                    Tab(
                        title = tab.title.getString(),
                        selected = index == selectedTabIndex,
                        rowActive = rowHasFocus,
                        interactionSource = interactionSource,
                        onClick = onTabClick,
                        modifier = Modifier.focusRequester(tab.tabFocusRequester),
                    )
                }
            }
        }
    }
}

/**
 * The glass tab strip's scrolling row.
 *
 * Deliberately a plain [Row] rather than a `LazyRow`: a lazy row always claims the whole width it
 * is offered, which is what made the strip stretch across the page like a second navigation bar.
 * A horizontally scrollable row measures to its content, so the capsule ends where the tabs end —
 * it reads as a segmented control floating over the artwork instead of as chrome.
 */
@Composable
private fun GlassTabScroller(
    tabs: List<TabDetails>,
    selectedIndex: Int,
    rowHasFocus: Boolean,
    onRowFocusChanged: (Boolean) -> Unit,
    onEnterRequest: (FocusDirection) -> TabDetails?,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    ensureVisible: Int? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .onFocusChanged { onRowFocusChanged(it.hasFocus) }
                .focusGroup()
                .focusProperties {
                    onEnter = {
                        (onEnterRequest(requestedFocusDirection)?.tabFocusRequester ?: FocusRequester.Default)
                            .tryRequestFocus()
                    }
                },
    ) {
        tabs.forEachIndexed { index, tab ->
            val interactionSource = remember { MutableInteractionSource() }
            Tab(
                title = tab.title.getString(),
                selected = index == selectedIndex,
                rowActive = rowHasFocus,
                interactionSource = interactionSource,
                onClick = { onClick(index) },
                modifier = Modifier.focusRequester(tab.tabFocusRequester),
                bringIntoViewRequester = tab.bringIntoViewRequester,
            )
        }
    }
    if (ensureVisible != null) {
        LaunchedEffect(ensureVisible) {
            tabs.getOrNull(ensureVisible)?.bringIntoViewRequester?.bringIntoView()
        }
    }
}

/**
 * Holds page-level sub-navigation in one capsule of glass, the same material and silhouette as
 * the destinations in the top bar.
 *
 * Without the capsule, the unselected tabs are white labels scattered across artwork and the
 * selected tab is a single white stone — that reads as a stray button rather than as a control
 * that belongs to the material. The strip gives the row an edge, a rim and something to refract,
 * and makes "Library / Collections / Genres" look like one segmented control.
 */
@Composable
private fun GlassTabStrip(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (!LocalLiquidGlass.current) {
        Box(modifier) { content() }
        return
    }
    Box(
        // No fillMaxWidth: the strip hugs its tabs. A full-width capsule under a full-width bar
        // is two stacked bars, and the second one has no job except to hold five words.
        //
        // One height with the top bar: the strip sits directly under those capsules, and a 4dp
        // difference between two stacked pieces of the same material reads as a mistake rather
        // than as a hierarchy.
        modifier =
            modifier
                .height(GlassTokens.pillHeight)
                .glassCapsule()
                .padding(horizontal = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
fun <T> KeyedTabRow(
    selectedTabKey: T,
    tabs: SortedMap<T, TabDetails>,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selectedTabKey) {
        tabs[selectedTabKey]?.bringIntoViewRequester?.bringIntoView()
    }
    var rowHasFocus by remember { mutableStateOf(false) }
    val currentSelectedTabKey by rememberUpdatedState(selectedTabKey)
    val currentOnClick by rememberUpdatedState(onClick)

    val tabKeyList = remember(tabs) { tabs.keys.filterNotNull().toList() }
    val tabList = remember(tabs) { tabs.values.toList() }

    GlassTabStrip(modifier = modifier) {
        if (LocalLiquidGlass.current) {
            GlassTabScroller(
                tabs = tabList,
                selectedIndex = tabKeyList.indexOf(selectedTabKey),
                rowHasFocus = rowHasFocus,
                onRowFocusChanged = { rowHasFocus = it },
                onEnterRequest = { direction ->
                    // If entering from left or right, use last or first tab. Otherwise use the
                    // selected tab.
                    Timber.v(
                        "onEnter requestedFocusDirection=%s, currentSelectedTabKey=%s",
                        direction,
                        currentSelectedTabKey,
                    )
                    when (direction) {
                        FocusDirection.Left -> tabs[tabs.lastKey()]
                        FocusDirection.Right -> tabs[tabs.firstKey()]
                        else -> tabs[currentSelectedTabKey]
                    }
                },
                onClick = { index -> tabKeyList.getOrNull(index)?.let(currentOnClick) },
                ensureVisible = tabKeyList.indexOf(selectedTabKey).takeIf { it >= 0 },
            )
        } else {
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onFocusChanged {
                            rowHasFocus = it.hasFocus
                        }.focusGroup()
                        .focusProperties {
                            onEnter = {
                                // If entering from left or right, use last or first tab
                                // Otherwise use the selected tab
                                val key =
                                    when (requestedFocusDirection) {
                                        FocusDirection.Left -> tabs.lastKey()
                                        FocusDirection.Right -> tabs.firstKey()
                                        else -> currentSelectedTabKey
                                    }
                                (tabs[key]?.tabFocusRequester ?: FocusRequester.Default)
                                    .tryRequestFocus()
                            }
                        },
            ) {
                items(tabKeyList, key = { it }) { tabKey ->
                    tabs[tabKey]?.let { tab ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val onTabClick =
                            remember(tabKey) {
                                {
                                    currentOnClick(tabKey)
                                }
                            }
                        Tab(
                            title = tab.title.getString(),
                            selected = tabKey == selectedTabKey,
                            rowActive = rowHasFocus,
                            interactionSource = interactionSource,
                            onClick = onTabClick,
                            modifier =
                                Modifier
                                    .focusRequester(tab.tabFocusRequester)
                                    .bringIntoViewRequester(tab.bringIntoViewRequester),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Tab(
    title: String,
    selected: Boolean,
    rowActive: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    bringIntoViewRequester: BringIntoViewRequester = remember { BringIntoViewRequester() },
) {
    if (LocalLiquidGlass.current) {
        GlassTab(
            title = title,
            selected = selected,
            onClick = onClick,
            bringIntoViewRequester = bringIntoViewRequester,
            modifier = modifier,
        )
        return
    }

    var tabWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val focused by interactionSource.collectIsFocusedAsState()
    val contentColor =
        if (rowActive || selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
        }
    Box(
        modifier =
            modifier
                .clickable(
                    enabled = true,
                    interactionSource = interactionSource,
                    onClick = onClick,
                    indication = null,
                ).onGloballyPositioned {
                    tabWidth = with(density) { it.size.width.toDp() }
                },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier,
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TabIndicator(
                selected = selected,
                rowActive = rowActive,
                focused = focused,
                tabWidth = tabWidth,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * The Liquid Glass tab: a capsule rather than an underline, so page-level sub-navigation matches the
 * top bar instead of looking like a second, flatter navigation system underneath it.
 */
@Composable
private fun GlassTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val contentColor = if (selected) Color.Black else Color.White.copy(alpha = 0.82f)
    val scope = rememberCoroutineScope()
    Surface(
        onClick = onClick,
        modifier =
            modifier
                // 42dp inside the 50dp strip: the same 4dp inset the top bar's selected pill
                // uses, so both pieces of chrome seat their controls identically.
                .height(GlassTokens.pillHeight - 8.dp)
                .bringIntoViewRequester(bringIntoViewRequester)
                // Keep the tab under the D-pad on screen when the strip is wider than the page
                // (a series with a dozen seasons). The scroll container answers the request.
                .onFocusChanged {
                    if (it.isFocused) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) Color.White else Color.Transparent,
                contentColor = contentColor,
                focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.26f),
                focusedContentColor = if (selected) Color.Black else Color.White,
                pressedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.34f),
                pressedContentColor = if (selected) Color.Black else Color.White,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (selected) 1.08f else 1.04f),
        glow =
            ClickableSurfaceDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow.None,
                pressedGlow = Glow.None,
            ),
        // Focus adds the fill and a bigger scale, but light alone cannot separate "focused" from
        // "selected" on a pale pane (both end up white), so it also gains the same dark rim the
        // top bar's pills use. Dark, never white: a bright outline reads as a drawn border.
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.38f)),
                        shape = shape,
                    ),
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // The TV Surface pins its content to the top-left; filling the slot is what centres
            // the label inside the capsule.
            modifier = Modifier.fillMaxHeight().padding(horizontal = 20.dp),
        ) {
            Text(
                text = title,
                style = if (selected) GlassType.tabSelected else GlassType.tab,
                color = contentColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun TabIndicator(
    selected: Boolean,
    rowActive: Boolean,
    focused: Boolean,
    tabWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(if (rowActive && focused) tabWidth else tabWidth * .25f)
    val backgroundColor =
        if (rowActive && focused) {
            MaterialTheme.colorScheme.border
        } else if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.Transparent
        }
    Box(
        modifier =
            modifier
                .height(2.dp)
                .fillMaxWidth()
                .width(width)
                .background(backgroundColor),
    )
}

@PreviewTvSpec
@Composable
private fun TabRowPreview() {
    WholphinTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            TabRow(
                selectedTabIndex = -1,
                tabs =
                    remember {
                        listOf("Tab 1", "Tab 2", "Tab 3").map { TabDetails(StringStringProvider(it)) }
                    },
                onClick = {},
            )
            Tab(
                title = "This is a Tab",
                selected = true,
                rowActive = true,
                onClick = {},
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}
