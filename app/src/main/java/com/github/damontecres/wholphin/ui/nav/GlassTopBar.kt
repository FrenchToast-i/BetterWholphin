package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.glassCapsule
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.util.splitClockFace

private val BarPillShape = RoundedCornerShape(percent = 50)

/**
 * Gap between the bar's capsules.
 *
 * Tight enough that a full bar — profile, every destination, the two global actions, the clock and
 * settings — still fits on a 960dp television without clipping the last destination's label.
 */
private val BarGap = 8.dp

/**
 * Vertical inset of a selected destination inside the destinations capsule.
 *
 * The pill used to fill the capsule's full height and grow when focused, so the white chip spilled
 * past the glass on both edges and the row read as a set of overlapping shapes. Inset by a few dp,
 * the selection is a chip *in* the material, the way a tvOS tab is, and the focus scale has room
 * to grow without leaving the capsule.
 */
private val PillInset = 4.dp

/**
 * The navigation bar used by Liquid Glass mode: one horizontal row of floating glass capsules
 * across the top of the screen, the way tvOS lays out its tabs.
 *
 * A top bar is the right shape for a 16:9 television. A left rail permanently spends ~230dp of
 * horizontal space that shelves need, and it makes every page look like a settings menu; running
 * the destinations along the top gives the artwork the full frame and keeps every destination a
 * single D-pad press away.
 *
 * The row is: identity, one capsule holding every destination, then the global actions, the clock
 * and settings. The capsules are separate bubbles so the artwork shows between them.
 *
 * Every destination in that middle capsule comes from the nav drawer's pinned items, so the bar is
 * toggled and reordered with the same Settings control as the classic sidebar. The controls that are
 * not destinations (identity, Home, Now Playing, Search, Settings, the clock) each have their own
 * switch under Interface preferences, so a viewer who wants nothing but libraries gets a bar of
 * nothing but libraries.
 *
 * **The bar measures itself.** Which controls are switched on changes how much room the row has,
 * so the layout is chosen from the labels' own widths rather than from a guess about the number of
 * destinations: a short bar gets generous spacing and a capsule that hugs its labels, a fuller one
 * steps down to the compact type size and a watch-face clock instead of dropping the time, and only
 * a bar that genuinely cannot fit falls back to scrolling the destination capsule. Every capsule
 * stays the same height in all three, and a trailing spacer keeps the right-hand cluster against
 * the screen edge whether the middle capsule is wide or narrow.
 */
@Composable
fun GlassTopBar(
    items: List<NavDrawerItem>,
    moreItems: List<NavDrawerItem>,
    selectedIndex: Int,
    moreExpanded: Boolean,
    nowPlayingEnabled: Boolean,
    nowPlayingTitle: String?,
    user: JellyfinUser,
    userImageUrl: String?,
    showClock: Boolean,
    showHome: Boolean,
    showNowPlaying: Boolean,
    showSettings: Boolean,
    showProfile: Boolean,
    showSearch: Boolean,
    showPlaySomething: Boolean,
    showTonight: Boolean,
    tonightSelected: Boolean,
    settingsSelected: Boolean,
    onProfileClick: () -> Unit,
    onNowPlayingClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSelect: (Int, NavDrawerItem) -> Unit,
    onMoreClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPlaySomethingClick: () -> Unit,
    onTonightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val barItems =
        buildList {
            if (showHome) {
                add(
                    BarItem(
                        key = "home",
                        label = stringResource(R.string.home),
                        selected = selectedIndex == HOME_INDEX,
                        onClick = onHomeClick,
                    ),
                )
            }
            if (nowPlayingEnabled && showNowPlaying) {
                add(
                    BarItem(
                        key = "now_playing",
                        label = nowPlayingTitle ?: stringResource(R.string.now_playing),
                        selected = selectedIndex == NOW_PLAYING_INDEX,
                        onClick = onNowPlayingClick,
                    ),
                )
            }
            items.forEachIndexed { index, item ->
                // Tonight is a place, but it is not a library: on this bar it is the moon disc
                // beside the global actions. A pill here costs ~90dp more than the disc, which is
                // exactly the width that pushed the last library off the capsule.
                if (item is NavDrawerItem.Tonight) return@forEachIndexed
                add(
                    BarItem(
                        key = "library_$index",
                        label = item.name(context),
                        selected = selectedIndex == index,
                        onClick = { onSelect(index, item) },
                    ),
                )
            }
            if (moreItems.isNotEmpty()) {
                add(
                    BarItem(
                        key = "more",
                        label = stringResource(R.string.more),
                        selected = selectedIndex == items.size,
                        onClick = onMoreClick,
                    ),
                )
                if (moreExpanded) {
                    moreItems.forEachIndexed { index, item ->
                        if (item is NavDrawerItem.Tonight) return@forEachIndexed
                        val adjustedIndex = items.size + index
                        add(
                            BarItem(
                                key = "more_$index",
                                label = item.name(context),
                                selected = selectedIndex == adjustedIndex,
                                onClick = { onSelect(adjustedIndex, item) },
                            ),
                        )
                    }
                }
            }
        }

    val time = LocalClock.current.timeString.value
    // The numeric face and the day period are found by shape, not by position: a locale can put
    // the day period first, or write the time with no space at all.
    val (face, meridiem) = splitClockFace(time)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        fun measure(
            text: String,
            style: TextStyle,
        ): Dp = with(density) { textMeasurer.measure(text = text, style = style).size.width.toDp() }

        // One measurement of a label is a few hundred microseconds, and it only reruns when the
        // labels or the enabled controls change, so the bar can afford to know its own width
        // instead of guessing from a count.
        val compactTab = GlassType.tab.copy(fontSize = 16.sp)

        fun capsuleWidth(compact: Boolean): Dp {
            if (barItems.isEmpty()) return 0.dp
            val style = if (compact) compactTab else GlassType.tab
            val pillPadding = if (compact) 10.dp else GlassTokens.pillPadding
            val pillGap = if (compact) 4.dp else 6.dp
            val capsulePadding = if (compact) 6.dp else 8.dp
            val pills =
                barItems.fold(0.dp) { total, item ->
                    total + measure(item.label, style) + pillPadding * 2
                }
            return pills + pillGap * (barItems.size - 1) + capsulePadding * 2
        }

        val discCount =
            listOf(showProfile, showTonight, showPlaySomething, showSearch, showSettings).count { it }
        val capsuleCount = if (barItems.isEmpty()) 0 else 1
        val clockCount = if (showClock) 1 else 0
        val slots = discCount + clockCount + capsuleCount
        val gaps = (slots - 1).coerceAtLeast(0)
        val fullClockWidth = measure(time, GlassType.clock) + 26.dp

        fun total(
            compact: Boolean,
            clockWidth: Dp,
        ): Dp =
            capsuleWidth(compact) +
                GlassTokens.pillHeight * discCount +
                clockWidth * clockCount +
                BarGap * gaps

        // Spacious until the labels stop fitting, compact until even the smaller type stops
        // fitting, and only then a capsule that scrolls. A television with every control turned
        // off gets one small capsule hugging three labels rather than a bar-wide sheet of glass.
        val available = if (maxWidth.value.isFinite()) maxWidth else Dp.Infinity
        val spacious = total(compact = false, clockWidth = fullClockWidth) <= available
        val compact = !spacious
        val scrolling =
            !spacious && total(compact = true, clockWidth = GlassTokens.pillHeight) > available
        val watchFace = compact && meridiem.isNotEmpty()

        val listState = rememberLazyListState()
        val selectedItemIndex = barItems.indexOfFirst { it.selected }
        // A tab bar that scrolls has to keep the current tab on screen, otherwise the bar drifts
        // away from the page it is describing.
        LaunchedEffect(selectedItemIndex, barItems.size, scrolling) {
            if (scrolling && selectedItemIndex >= 0) {
                // Align the tab *before* the current one to the edge. Landing the selected tab
                // exactly on the edge leaves the previous label sliced in half ("earch"), which
                // reads as a rendering bug rather than as a row that scrolls.
                listState.animateScrollToItem((selectedItemIndex - 1).coerceAtLeast(0))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(GlassTokens.barHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BarGap),
        ) {
            if (showProfile) {
                GlassCapsule(modifier = Modifier.size(GlassTokens.pillHeight)) {
                    ProfileButton(
                        user = user,
                        imageUrl = userImageUrl,
                        onClick = onProfileClick,
                    )
                }
            }
            if (barItems.isNotEmpty()) {
                GlassCapsule(
                    modifier =
                        if (scrolling) {
                            Modifier.weight(1f).height(GlassTokens.pillHeight)
                        } else {
                            Modifier.width(capsuleWidth(compact)).height(GlassTokens.pillHeight)
                        },
                    contentPadding =
                        PaddingValues(
                            horizontal = if (compact) 6.dp else 8.dp,
                            vertical = PillInset,
                        ),
                ) {
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(barItems, key = { it.key }) { item ->
                            BarPill(
                                label = item.label,
                                selected = item.selected,
                                onClick = item.onClick,
                                padding = if (compact) 10.dp else GlassTokens.pillPadding,
                                compact = compact,
                            )
                        }
                    }
                }
                if (!scrolling) {
                    // Holds the trailing cluster against the screen edge however narrow the
                    // destinations capsule is, so turning controls off never leaves the clock
                    // floating in the middle of the bar.
                    Spacer(Modifier.weight(1f))
                }
            }
            if (showTonight) {
                // The nightly plan, one press from any page. A disc rather than a destination
                // pill: the libraries need the capsule's width, and a moon is recognisable at this
                // size.
                GlassCapsule(modifier = Modifier.size(GlassTokens.pillHeight)) {
                    GlyphPill(
                        glyph = stringResource(R.string.fa_moon),
                        description = stringResource(R.string.tonight),
                        onClick = onTonightClick,
                        selected = tonightSelected,
                    )
                }
            }
            if (showPlaySomething) {
                // The one control that answers "what should I watch?" without opening a page: it
                // picks from the whole library in the viewer's taste and starts playing. It sits
                // beside search because both are global actions rather than places.
                GlassCapsule(modifier = Modifier.size(GlassTokens.pillHeight)) {
                    GlyphPill(
                        glyph = stringResource(R.string.fa_shuffle),
                        description = stringResource(R.string.play_something),
                        onClick = onPlaySomethingClick,
                    )
                }
            }
            if (showSearch) {
                // Search lives in the bar rather than floating over the page: a control that
                // hovers over the last poster in a grid is both an obstruction and outside the
                // focus path, so the D-pad could not reliably reach it. In the bar it is one press
                // from anywhere and still visually separate from the library destinations.
                GlassCapsule(modifier = Modifier.size(GlassTokens.pillHeight)) {
                    IconPill(
                        icon = Icons.Default.Search,
                        description = stringResource(R.string.search),
                        onClick = onSearchClick,
                    )
                }
            }
            if (showClock) {
                if (watchFace) {
                    // A full bar cannot spend ~120dp on the clock, but a television that hides the
                    // time whenever the viewer owns six libraries is worse than a smaller readout.
                    // A watch face — time over its meridiem — keeps it in a 50dp disc, the same
                    // width as every control around it. 24-hour locales carry no day period and
                    // take the single-line branch below.
                    GlassCapsule(modifier = Modifier.size(GlassTokens.pillHeight)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = face,
                                style =
                                    GlassType.clock.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 15.sp,
                                    ),
                                color = Color.White.copy(alpha = 0.94f),
                            )
                            Text(
                                text = meridiem,
                                style =
                                    GlassType.clock.copy(
                                        fontSize = 8.sp,
                                        lineHeight = 9.sp,
                                        letterSpacing = 0.6.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = Color.White.copy(alpha = 0.72f),
                            )
                        }
                    }
                } else if (compact) {
                    // No day period to stack: the time is one short line, and a disc keeps the row
                    // of equal shapes.
                    GlassCapsule(modifier = Modifier.size(GlassTokens.pillHeight)) {
                        Text(
                            text = time,
                            style = GlassType.clock.copy(fontSize = 13.sp),
                            color = Color.White.copy(alpha = 0.94f),
                            maxLines = 1,
                        )
                    }
                } else {
                    GlassCapsule(
                        modifier = Modifier.height(GlassTokens.pillHeight),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text(
                            text = time,
                            style = GlassType.clock,
                            color = Color.White.copy(alpha = 0.94f),
                        )
                    }
                }
            }
            if (showSettings) {
                GlassCapsule(modifier = Modifier.size(GlassTokens.pillHeight)) {
                    IconPill(
                        icon = Icons.Default.Settings,
                        description = stringResource(R.string.settings),
                        // Settings is a page, so the bar says so: the gear lights up like any
                        // other selected destination. Without it the last library pill stayed lit
                        // while the viewer was three sub-pages deep in settings.
                        selected = settingsSelected,
                        onClick = onSettingsClick,
                    )
                }
            }
        }
    }
}

private data class BarItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * One capsule in the bar. Shared radius, material and shadow so the row reads as one substance even
 * though it is drawn as separate bubbles.
 */
@Composable
private fun GlassCapsule(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .glassCapsule(shape = BarPillShape)
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ProfileButton(
    user: JellyfinUser,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        shape = ClickableSurfaceDefaults.shape(shape = BarPillShape),
        colors = BarColors(selected = false),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = NoGlow,
        border = BarFocusedBorder(),
    ) {
        // The TV Surface does not centre its slot: content is measured at its natural size and
        // pinned to the top-left. Filling the slot is what puts a glyph in the middle of a pill.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            UserIconCardImage(
                id = user.id,
                name = user.name,
                imageUrl = imageUrl,
                alpha = 1f,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

@Composable
private fun IconPill(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(GlassTokens.pillHeight),
        shape = ClickableSurfaceDefaults.shape(shape = BarPillShape),
        colors = BarColors(selected = selected),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (selected) 1.08f else 1.05f),
        glow = NoGlow,
        border = BarFocusedBorder(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (selected) Color.Black else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/**
 * An icon capsule whose glyph comes from the bundled Font Awesome font.
 *
 * The material icons are a small set, and the shuffle mark is not in it; the font is already in
 * the build for the drawer and filter glyphs, so the bar uses it rather than adding a dependency
 * for one symbol.
 */
@Composable
private fun GlyphPill(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(GlassTokens.pillHeight),
        shape = ClickableSurfaceDefaults.shape(shape = BarPillShape),
        colors = BarColors(selected = selected),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (selected) 1.08f else 1.05f),
        glow = NoGlow,
        border = BarFocusedBorder(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                fontFamily = FontAwesome,
                fontSize = 18.sp,
                // A selected disc is white, so the glyph flips like every other selected control.
                color = if (selected) Color.Black else Color.White.copy(alpha = 0.92f),
                // A glyph's baseline sits high in its line box, which leaves the mark ~1dp above
                // the capsule's centre; nudge it back or the disc looks off against the icons
                // drawn as vectors beside it.
                modifier = Modifier.offset(y = 1.dp),
            )
        }
    }
}

/**
 * A destination, as plain text inside a capsule the way tvOS draws its tabs. Text only: glyphs add
 * ~25dp each, which is the difference between every destination fitting on screen and the last one
 * being clipped.
 *
 * The current destination is a lit white capsule with dark type — the only pure white on screen.
 * It is inset inside the destinations capsule (see [PillInset]) and grows only a couple of percent
 * when focused, so the chip never spills past the glass it sits in.
 */
@Composable
private fun BarPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    padding: Dp,
    compact: Boolean,
) {
    val style =
        when {
            compact && selected -> GlassType.tabSelected.copy(fontSize = 16.sp)
            compact -> GlassType.tab.copy(fontSize = 16.sp)
            selected -> GlassType.tabSelected
            else -> GlassType.tab
        }
    Surface(
        onClick = onClick,
        // Fills the capsule's inner height rather than sitting at a size of its own, so the
        // selected tab occupies the full inset band and reads as the current page rather than as a
        // label that happens to be lit.
        modifier = Modifier.fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(shape = BarPillShape),
        colors = BarColors(selected = selected),
        // The selected tab is already white, so focus cannot be signalled by light alone: it gains
        // a dark rim and grows just enough to feel pressed toward the viewer. The growth is small
        // because [PillInset] is only 4dp — a 10% scale, as the old bar used, walked the chip out
        // over the capsule's edge.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        glow = NoGlow,
        border = BarFocusedBorder(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            // Fill the slot: the content is centred by this box, not by the Surface.
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = padding),
        ) {
            Text(
                text = label,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BarColors(selected: Boolean) =
    ClickableSurfaceDefaults.colors(
        containerColor = if (selected) Color.White else Color.Transparent,
        contentColor = if (selected) Color.Black else Color.White.copy(alpha = 0.86f),
        // Focus adds light on top of the selection instead of replacing it, so the current page
        // stays readable after the viewer has moved into the content.
        focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = 0.26f),
        focusedContentColor = if (selected) Color.Black else Color.White,
        pressedContainerColor = if (selected) Color.White.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.34f),
        pressedContentColor = if (selected) Color.Black else Color.White,
    )

@Composable
private fun BarFocusedBorder() =
    ClickableSurfaceDefaults.border(
        // Dark, never white: a bright outline reads as a drawn border on clear glass. The rim
        // plus the light focus fill gives focus on both bright and dark artwork.
        focusedBorder =
            Border(
                border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.38f)),
                shape = BarPillShape,
            ),
    )

private val NoGlow =
    ClickableSurfaceDefaults.glow(
        glow = Glow.None,
        focusedGlow = Glow.None,
        pressedGlow = Glow.None,
    )
