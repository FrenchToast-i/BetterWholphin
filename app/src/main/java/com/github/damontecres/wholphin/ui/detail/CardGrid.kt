package com.github.damontecres.wholphin.ui.detail

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.isBackwardButton
import com.github.damontecres.wholphin.ui.playback.isForwardButton
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.glassCapsule
import com.github.damontecres.wholphin.ui.theme.glassSurface
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.ceil

private const val DEBUG = false

interface CardGridItem {
    val gridId: String
    val playable: Boolean
    val sortName: String
}

data class GridItemDetails<T : CardGridItem>(
    val item: T?,
    val index: Int,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val widthPx: Int,
    val mod: Modifier,
)

/**
 * Shows a vertical grid of [CardGridItem]s
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : CardGridItem> CardGrid(
    pager: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onClickPlay: (Int, T) -> Unit,
    letterPosition: suspend (Char) -> Int,
    gridFocusRequester: FocusRequester,
    showJumpButtons: Boolean,
    showLetterButtons: Boolean,
    modifier: Modifier = Modifier,
    initialPosition: Int = 0,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
    cardContent: @Composable (GridItemDetails<T>) -> Unit,
    columns: Int = 6,
    spacing: Dp = 16.dp,
    bringIntoViewSpec: BringIntoViewSpec = LocalBringIntoViewSpec.current,
) {
    val startPosition =
        remember(initialPosition, pager.size) {
            initialPosition.coerceIn(0, (pager.size - 1).coerceAtLeast(0))
        }

    var focusedIndex by rememberSaveable { mutableIntStateOf(startPosition) }
    val currentFocusedIndex by rememberUpdatedState(focusedIndex)
    val gridState =
        rememberLazyGridState(
            cacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 0.5f),
            initialFirstVisibleItemIndex = focusedIndex,
        )
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val zeroFocus = remember { FocusRequester() }
    var previouslyFocusedIndex by rememberSaveable { mutableIntStateOf(0) }

    var alphabetFocus by remember { mutableStateOf(false) }
    val focusOn =
        remember {
            { index: Int ->
                if (DEBUG) Timber.v("focusOn: focusedIndex=$currentFocusedIndex, index=$index")
                if (index != currentFocusedIndex) {
                    previouslyFocusedIndex = currentFocusedIndex
                }
                focusedIndex = index
            }
        }

    // Wait for a recomposition to focus
    val alphabetFocusRequester = remember { FocusRequester() }
    LaunchedEffect(alphabetFocus) {
        if (alphabetFocus) {
            alphabetFocusRequester.tryRequestFocus()
        }
        alphabetFocus = false
    }

    val useBackToJump = true // uiConfig.preferences.interfacePreferences.scrollTopOnBack
    val showFooter = true // uiConfig.preferences.interfacePreferences.showPositionFooter
    val useJumpRemoteButtons = true // uiConfig.preferences.interfacePreferences.pageWithRemoteButtons
    val jump2 =
        remember {
            if (pager.size >= 25_000) {
                columns * 2000
            } else if (pager.size >= 7_000) {
                columns * 200
            } else if (pager.size >= 2_000) {
                columns * 50
            } else {
                columns * 20
            }
        }
    val jump1 =
        remember {
            if (pager.size >= 25_000) {
                columns * 500
            } else if (pager.size >= 7_000) {
                columns * 50
            } else if (pager.size >= 2_000) {
                columns * 15
            } else {
                columns * 6
            }
        }

    val jump =
        remember {
            { jump: Int ->
                scope.launch(ExceptionHandler()) {
                    val newPosition =
                        (gridState.firstVisibleItemIndex + jump).coerceIn(0..<pager.size)
                    if (DEBUG) Timber.d("newPosition=$newPosition")
                    focusOn(newPosition)
                    gridState.scrollToItem(newPosition, 0)
                }
            }
        }
    val jumpToTop =
        remember {
            {
                scope.launch(ExceptionHandler()) {
                    if (currentFocusedIndex < (columns * 6)) {
                        // If close, animate the scroll
                        gridState.animateScrollToItem(0, 0)
                    } else {
                        gridState.scrollToItem(0, 0)
                    }
                    focusOn(0)
                    zeroFocus.tryRequestFocus()
                }
            }
        }

    val jumpToLetter: (Char) -> Unit =
        remember {
            { letter: Char ->
                scope.launch(ExceptionHandler()) {
                    val jumpPosition =
                        withContext(WholphinDispatchers.IO) {
                            letterPosition.invoke(letter)
                        }
                    Timber.d("Alphabet jump to $jumpPosition")
                    if (jumpPosition >= 0) {
                        pager.getOrNull(jumpPosition)
                        gridState.scrollToItem(jumpPosition)
                        focusOn(jumpPosition)
                        alphabetFocus = true
                    }
                }
            }
        }

    if (pager.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        ) {
            EmptyGridMessage()
        }
    } else {
        var longPressing by remember { mutableStateOf(false) }
        Row(
//        horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .onKeyEvent {
                        if (DEBUG) Timber.d("onKeyEvent: ${it.nativeKeyEvent}")
                        if (useBackToJump && it.key == Key.Back && it.nativeKeyEvent.isLongPress) {
                            longPressing = true
                            val newPosition = previouslyFocusedIndex
                            if (DEBUG) Timber.d("Back long pressed: newPosition=$newPosition")
                            if (newPosition > 0) {
                                scope.launch(ExceptionHandler()) {
                                    pager.getOrNull(newPosition)
                                    gridState.scrollToItem(newPosition)
                                    focusOn(newPosition)
                                    alphabetFocus = true
                                }
                            }
                            return@onKeyEvent true
                        } else if (it.type == KeyEventType.KeyUp) {
                            if (longPressing && it.key == Key.Back) {
                                longPressing = false
                                return@onKeyEvent true
                            }
                            longPressing = false
                        }
                        if (it.type != KeyEventType.KeyUp) {
                            return@onKeyEvent false
                        } else if (useBackToJump && it.key == Key.Back && focusedIndex > 0) {
                            jumpToTop()
                            return@onKeyEvent true
                        } else if (isPlayKeyUp(it)) {
                            val item = pager.getOrNull(focusedIndex)
                            if (item?.playable == true) {
                                Timber.v("Clicked play on ${item.gridId}")
                                onClickPlay.invoke(focusedIndex, item)
                            }
                            return@onKeyEvent true
                        } else if (useJumpRemoteButtons && isForwardButton(it)) {
                            jump(jump1)
                            return@onKeyEvent true
                        } else if (useJumpRemoteButtons && isBackwardButton(it)) {
                            jump(-jump1)
                            return@onKeyEvent true
                        } else if (showLetterButtons && pager.isNotEmpty() &&
                            it.nativeKeyEvent.keyCode in (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z)
                        ) {
                            val letter = it.nativeKeyEvent.unicodeChar.toChar()
                            jumpToLetter.invoke(letter)
                            return@onKeyEvent true
                        } else {
                            return@onKeyEvent false
                        }
                    },
        ) {
            if (showJumpButtons && pager.isNotEmpty()) {
                JumpButtons(
                    jump1 = jump1,
                    jump2 = jump2,
                    jumpClick = { jump(it) },
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            val density = LocalDensity.current
            var cardWidthPx by rememberSaveable { mutableIntStateOf(0) }

            Box(
                modifier = Modifier.weight(1f),
            ) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        state = gridState,
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .focusGroup()
                                .focusRestorer(firstFocus)
                                .focusProperties {
                                    onEnter = {
                                        if (focusedIndex < 0 && gridState.firstVisibleItemIndex <= startPosition) {
                                            focusedIndex = startPosition
                                        }
                                    }
                                }.onLayoutRectChanged(0, 0) {
                                    val width = it.width
                                    val spacingPx = with(density) { spacing.toPx() }
                                    val cardWidth =
                                        ceil((width - (spacingPx * (columns - 1))) / columns)
                                    cardWidthPx = cardWidth.toInt()
//                                    Timber.v("cardWidthPx=%s", cardWidthPx)
                                },
                    ) {
                        items(pager.size) { index ->
                            val item = pager[index]
                            val details =
                                remember(index, item, cardWidthPx, columns) {
                                    val mod =
                                        if ((index == currentFocusedIndex) or (currentFocusedIndex < 0 && index == 0)) {
                                            if (DEBUG) Timber.d("Adding firstFocus to focusedIndex $index")
                                            Modifier
                                                .focusRequester(firstFocus)
                                                .focusRequester(gridFocusRequester)
                                                .focusRequester(alphabetFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    GridItemDetails(
                                        item = item,
                                        index = index,
                                        onClick = {
                                            if (item != null) {
                                                focusedIndex = index
                                                onClickItem.invoke(index, item)
                                            }
                                        },
                                        onLongClick = {
                                            if (item != null) {
                                                onLongClickItem.invoke(index, item)
                                            }
                                        },
                                        widthPx = cardWidthPx,
                                        mod =
                                            mod
                                                .ifElse(
                                                    index == 0,
                                                    Modifier.focusRequester(zeroFocus),
                                                ).onFocusChanged { focusState ->
                                                    if (DEBUG) {
                                                        Timber.v(
                                                            "$index isFocused=${focusState.isFocused}",
                                                        )
                                                    }
                                                    if (focusState.isFocused) {
                                                        // Focused, so set that up
                                                        focusOn(index)
                                                        positionCallback?.invoke(columns, index)
                                                    }
                                                },
                                    )
                                }
                            cardContent.invoke(details)
                        }
                    }
                    if (pager.isEmpty()) {
//                focusedIndex = -1
                        Box(modifier = Modifier.fillMaxSize()) {
                            EmptyGridMessage(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    if (showFooter) {
                        // Footer
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .background(AppColors.TransparentBlack50),
                        ) {
                            val index = (focusedIndex + 1).takeIf { it > 0 } ?: "?"
//                        if (focusedIndex >= 0) {
//                            focusedIndex + 1
//                        } else {
//                            max(savedFocusedIndex, focusedIndexOnExit) + 1
//                        }
                            Text(
                                modifier = Modifier.padding(4.dp),
                                color = MaterialTheme.colorScheme.onBackground,
                                text = "$index / ${pager.size}",
                            )
                        }
                    }
                }
            }
            val letters = stringResource(R.string.jump_letters)
            // Letters
            val currentLetter =
                remember(focusedIndex) {
                    pager
                        .getOrNull(focusedIndex)
                        ?.sortName
                        ?.firstOrNull()
                        ?.uppercaseChar()
                        ?.let {
                            when (it) {
                                in '0'..'9' -> '#'
                                in 'A'..'Z' -> it
                                else -> null
                            }
                        }
                        ?: letters[0]
                }
            if (showLetterButtons && pager.isNotEmpty()) {
                AlphabetButtons(
                    letters = letters,
                    currentLetter = currentLetter,
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 16.dp),
                    // Add end padding to push away from edge
                    letterClicked = jumpToLetter,
                )
            } else {
                // Spacer ensures card sizes do not change if the alphabet buttons are not shown
                Spacer(
                    Modifier
                        .padding(start = 16.dp)
                        .width(alphabetRailWidth()),
                )
            }
        }
    }
}

private val letterHorizontalPadding = 2.dp
private val letterButtonSize = 14.dp

/**
 * Height of a letter cell in the glass rail.
 *
 * Tall enough that a 12sp capital has air around it, and the rail lays the alphabet out in two
 * columns so all 27 letters fit a television page without scrolling. One column of 27 cells came
 * to 444dp, which is taller than the grid area, so the rail used to end mid-alphabet at T and the
 * half that was left read as a clipping fault.
 */
private val glassLetterCell = 18.dp

/** Width of the glass rail itself: two letter columns and their gutters. */
private val glassAlphabetWidth = 46.dp

/** Fallback cap when the rail has no bounded height to measure against. */
private val glassAlphabetFallbackHeight = 340.dp

/**
 * Width the alphabet rail occupies, so a grid reserves the same gutter whether the rail is
 * currently shown or not. Without this the cards resize the moment the letters appear.
 */
@Composable
internal fun alphabetRailWidth(): Dp =
    if (LocalLiquidGlass.current) glassAlphabetWidth else letterHorizontalPadding * 2 + letterButtonSize

@Composable
fun JumpButtons(
    jump1: Int,
    jump2: Int,
    jumpClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        JumpButton(R.string.fa_angles_up, -jump2, jumpClick)
        JumpButton(R.string.fa_angle_up, -jump1, jumpClick)
        JumpButton(R.string.fa_angle_down, jump1, jumpClick)
        JumpButton(R.string.fa_angles_down, jump2, jumpClick)
    }
}

@Composable
fun JumpButton(
    @StringRes stringRes: Int,
    jumpAmount: Int,
    jumpClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.width(40.dp),
        contentPadding = PaddingValues(4.dp),
        onClick = {
            jumpClick.invoke(jumpAmount)
        },
    ) {
        Text(text = stringResource(stringRes), fontFamily = FontAwesome)
    }
}

@Composable
fun AlphabetButtons(
    letters: String,
    currentLetter: Char,
    letterClicked: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalLiquidGlass.current) {
        // The rail used to fill whatever height its row reported, and on a grid page that row is
        // the whole page area: 27 cells came out 400dp tall and the capsule ran off the bottom
        // edge of the television. Measuring the space actually available lets the capsule end
        // where the page does and turn the rest into a scroll.
        BoxWithConstraints(modifier = modifier) {
            GlassAlphabetRail(
                letters = letters,
                currentLetter = currentLetter,
                letterClicked = letterClicked,
                availableHeight = maxHeight,
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val index = remember(letters, currentLetter) { letters.indexOf(currentLetter) }
    LaunchedEffect(currentLetter) {
        scope.launch(ExceptionHandler()) {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val lastVisibleItemIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            if (index >= 0 && index !in firstVisibleItemIndex..lastVisibleItemIndex) {
                listState.animateScrollToItem(index)
            }
        }
    }
    // Focus & interaction states for each letter button
    val focusRequesters = remember { List(letters.length) { FocusRequester() } }
    val interactionSources = remember { List(letters.length) { MutableInteractionSource() } }

    // Track if the entire alphabet picker component has focus
    var alphabetPickerFocused by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 1.1.dp, horizontal = letterHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(1.1.dp),
        state = listState,
        modifier =
            modifier
                .onFocusChanged { focusState ->
                    alphabetPickerFocused = focusState.hasFocus
                }.focusProperties {
                    onEnter = {
                        focusRequesters[index.coerceIn(0, letters.length - 1)].tryRequestFocus()
                    }
                },
    ) {
        items(
            letters.length,
            key = { letters[it] },
        ) { index ->
            val interactionSource = interactionSources[index]
            val focused by interactionSource.collectIsFocusedAsState()

            val isCurrentLetter = letters[index] == currentLetter
            // Apply alpha to individual items, but keep selected letter fully visible when picker is unfocused
            val itemAlpha =
                when {
                    isCurrentLetter && !alphabetPickerFocused -> 1f
                    alphabetPickerFocused -> .85f
                    else -> .25f
                }

            // Only show circle background for the current letter (or when focused)
            // Wrap in Box with clipping to prevent focus indicator from overflowing
            Box(
                modifier =
                    Modifier
                        .size(letterButtonSize)
                        .clip(CircleShape)
                        .alpha(itemAlpha),
            ) {
                Button(
                    modifier =
                        Modifier
                            .size(letterButtonSize)
                            .focusRequester(focusRequesters[index]),
                    contentPadding = PaddingValues(0.dp), // No padding to maximize text space
                    interactionSource = interactionSource,
                    onClick = {
                        letterClicked.invoke(letters[index])
                    },
                    colors =
                        if (isCurrentLetter || focused) {
                            // Use default button colors for current letter or focused
                            ButtonDefaults.colors()
                        } else {
                            // Transparent background for non-current letters (no circle)
                            ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                ) {
                    // Use border color for selected letter when focused, tertiary for unfocused-selected
                    val color =
                        when {
                            isCurrentLetter && focused -> MaterialTheme.colorScheme.border
                            isCurrentLetter -> MaterialTheme.colorScheme.tertiary
                            focused -> LocalContentColor.current
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    Text(
                        text = letters[index].toString(),
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * The alphabet jump rail as a slim capsule of glass.
 *
 * The classic rail is bare type at 25% white, which on a busy poster wall reads as stray letters
 * rather than as a control. Glass gives the rail an edge and something to refract, and the
 * letters gain real contrast: the current letter is white, the rest sit back, and the letter the
 * D-pad is on takes a white capsule so focus is unmistakable without an outline.
 *
 * Two columns rather than one: 27 cells at a legible size are taller than a television's grid
 * area, and a rail that ends at T with the rest scrolled away reads as a bug. Splitting it down
 * the middle fits every letter with air around it and needs no scrolling at all, so the rail is
 * always the same shape and the current letter is always on screen.
 */
@Composable
private fun GlassAlphabetRail(
    letters: String,
    currentLetter: Char,
    letterClicked: (Char) -> Unit,
    availableHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val currentIndex = remember(letters, currentLetter) { letters.indexOf(currentLetter) }
    val focusRequesters = remember(letters) { List(letters.length) { FocusRequester() } }
    val interactionSources = remember(letters) { List(letters.length) { MutableInteractionSource() } }
    // Column-major on purpose — A–M in the first column, N–Z in the second — so the rail still
    // reads as one alphabet rather than as an A/B, C/D … table.
    val rows = remember(letters) { (letters.length + 1) / 2 }
    val columns = remember(letters, rows) { listOf(letters.take(rows), letters.drop(rows)) }

    // Never taller than the space the page actually offers; on a short window the cells shrink
    // rather than the rail running off the bottom of the screen.
    val boundedHeight =
        if (availableHeight.value.isFinite()) availableHeight else glassAlphabetFallbackHeight
    val cell =
        minOf(glassLetterCell, (boundedHeight - 12.dp) / rows)
            .coerceAtLeast(11.dp)
    val fontSize = (cell.value * 0.72f).coerceIn(9f, 12f).sp
    val railHeight = cell * rows + 12.dp
    // Each column gets half the rail. `fillMaxWidth` inside a Column takes the whole row's
    // constraint, so the two columns would each claim the full width and the second one would
    // hang off the right edge of the television.
    val columnWidth = (glassAlphabetWidth - 8.dp) / 2

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .width(glassAlphabetWidth)
                .height(railHeight)
                .glassCapsule()
                .focusProperties {
                    onEnter = {
                        focusRequesters[currentIndex.coerceIn(0, letters.length - 1)]
                            .tryRequestFocus()
                    }
                },
    ) {
        columns.forEach { column ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                column.forEach { letter ->
                    val index = letters.indexOf(letter)
                    val isCurrent = letter == currentLetter
                    val focused by interactionSources[index].collectIsFocusedAsState()
                    Box(
                        modifier =
                            Modifier
                                .width(columnWidth)
                                .height(cell)
                                .padding(horizontal = 1.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    when {
                                        focused -> Color.White
                                        isCurrent -> Color.White.copy(alpha = 0.16f)
                                        else -> Color.Transparent
                                    },
                                ).focusRequester(focusRequesters[index])
                                .clickable(
                                    interactionSource = interactionSources[index],
                                    indication = null,
                                ) {
                                    letterClicked(letter)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = letter.toString(),
                            color =
                                when {
                                    focused -> Color.Black
                                    isCurrent -> Color.White
                                    else -> Color.White.copy(alpha = 0.58f)
                                },
                            fontSize = fontSize,
                            fontWeight =
                                if (focused || isCurrent) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Medium
                                },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The "nothing here" line, as a pane of the material rather than white type on the artwork.
 *
 * A grid page has no letterbox behind it, so a bare sentence is drawn against whatever the
 * backdrop happens to be — and a pale cartoon frame turns white type into a ghost. The copy
 * pane is the material's own answer: clear over dark art, smoked over a light frame, the same
 * rule the detail sheets follow. It also gives the message an edge, so an empty page reads as
 * deliberately empty rather than as a page that failed to draw.
 */
@Composable
private fun EmptyGridMessage(modifier: Modifier = Modifier) {
    val glass = LocalLiquidGlass.current
    Box(
        modifier =
            modifier
                .then(
                    if (glass) {
                        // Not a content pane: this is the only copy on an otherwise empty page,
                        // so it takes a fixed smoke rather than the artwork ramp. The ramp leaves
                        // a mostly clear pill over a bright frame, and white type on it — the
                        // exact failure the smoke exists to prevent.
                        Modifier.glassSurface(
                            shape = RoundedCornerShape(24.dp),
                            tint = GlassTokens.emptyStateTint,
                            scrim = 1f,
                        )
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 30.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.no_results),
            style = if (glass) GlassType.rowTitle else MaterialTheme.typography.titleLarge,
            color = if (glass) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
