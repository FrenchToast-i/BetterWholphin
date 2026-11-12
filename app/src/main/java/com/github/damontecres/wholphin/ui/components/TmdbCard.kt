package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.TmdbRequestPhase
import com.github.damontecres.wholphin.data.model.TmdbRequestUiState
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.services.TmdbService
import com.github.damontecres.wholphin.services.containsWatchlist
import com.github.damontecres.wholphin.ui.nav.LocalUserPreferences
import com.github.damontecres.wholphin.ui.theme.GlassTokens
import com.github.damontecres.wholphin.ui.theme.GlassType
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass

/**
 * One TMDB title, drawn as a poster.
 *
 * Shared by the trending shelf on Search and the recommendation rail on Home so the two discovery
 * surfaces are the same object: same silhouette, same focus signal, same caption. A titled poster
 * rather than a card with a synopsis, because TMDB art is what the viewer recognises from outside
 * the library.
 */
@Composable
fun TmdbPosterCard(
    item: TmdbItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TmdbCardWidth,
    onLongClick: (() -> Unit)? = null,
    /**
     * Fires when the poster takes focus, so the shell's backdrop can follow it.
     *
     * Every other card in the app already hands its artwork to the backdrop on focus; discovery
     * posters did not, which left the page around them refracting whatever the previous screen
     * had focused.
     */
    onFocus: (() -> Unit)? = null,
    /**
     * Draw the title and year under the poster.
     *
     * On a home row they are not: the home page's list sits below a fixed hero, so a poster plus
     * two caption lines is taller than the viewport and the focused card was sliced by the bottom
     * edge every time. Library rows on Home carry no captions either, and the discovery shelves
     * should read as the same kind of row.
     */
    showCaption: Boolean = true,
    /**
     * Position in a ranked shelf, drawn as a badge over the poster.
     *
     * A "Top 10" row is a different promise from a recommendation row: the number is the reason
     * the card is there, so it has to survive the shelf's own visual noise. A dark disc on the
     * poster's top corner reads on any artwork and does not add a caption line to a home row.
     */
    rank: Int? = null,
    /** A short status word over the poster's bottom corner, e.g. "Downloading". */
    badge: String? = null,
) {
    val glass = LocalLiquidGlass.current
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier =
            modifier
                .width(width)
                .then(
                    if (onFocus != null) {
                        Modifier.onFocusChanged {
                            if (it.isFocused) onFocus.invoke()
                        }
                    } else {
                        Modifier
                    },
                ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow =
            ClickableSurfaceDefaults.glow(
                glow = androidx.tv.material3.Glow.None,
                focusedGlow = androidx.tv.material3.Glow.None,
                pressedGlow = androidx.tv.material3.Glow.None,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        border =
                            BorderStroke(
                                // The same bright rim the library's posters get. A 38%-black
                                // outline on glass was invisible against a dark poster, so the
                                // discovery shelf looked like a row with nothing focused in it.
                                if (glass) 1.5.dp else 2.dp,
                                Color.White.copy(alpha = if (glass) 0.95f else 1f),
                            ),
                        shape = shape,
                    ),
            ),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = TmdbService.imageUrl(item.posterPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(shape),
                )
                if (rank != null) {
                    Text(
                        text = rank.toString(),
                        style =
                            if (glass) {
                                GlassType.rankBadge
                            } else {
                                MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            },
                        color = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(color = Color.Black.copy(alpha = 0.62f), shape = CircleShape)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                if (badge != null) {
                    Text(
                        text = badge,
                        style =
                            if (glass) {
                                GlassType.posterBadge
                            } else {
                                MaterialTheme.typography.labelSmall
                            },
                        color = Color.White,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(color = Color.Black.copy(alpha = 0.66f), shape = RoundedCornerShape(percent = 50))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (showCaption) {
                Text(
                    text = item.displayTitle,
                    style = if (glass) GlassType.settingsTitle else MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
                item.displayYear?.let { year ->
                    Text(
                        text = year,
                        style = if (glass) GlassType.settingsSummary else MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

/**
 * One muted line for a discovery failure, in the current type scale.
 *
 * Discovery is silent by default, so the one line that does appear has to be worth reading:
 * it always names the thing the viewer can act on, never a stack of internals.
 */
@Composable
fun TmdbMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = if (LocalLiquidGlass.current) GlassType.meta else MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.6f),
        modifier = modifier.padding(top = 8.dp),
    )
}

/**
 * A titled block of TMDB posters, shared by every discovery surface.
 *
 * Search's trending shelf and its "also on TMDB" results are the same object: same heading, same
 * credit, same poster cards, same two-row wrap. Forking a second shelf is how the two surfaces
 * drift into different card widths and one of them loses long-press.
 *
 * The credit sits above the posters rather than after them. As a footer it is the last 14dp of the
 * shelf, and a home page that scrolls the next row into view leaves that fragment floating alone
 * under the hero, clipped at the top and attached to nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TmdbPosterShelf(
    heading: String,
    items: List<TmdbItem>,
    onSelect: (TmdbItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Applied to the first poster, so a page can hand focus straight to the shelf. */
    firstFocusRequester: FocusRequester? = null,
    /**
     * One row instead of two.
     *
     * The "nothing in your library matches" page has less height to work with than a full
     * screen: its message and the shelf's heading and credit all sit above the posters. One row
     * keeps the whole section inside the viewport, so handing focus to the first poster never
     * scrolls the heading (and the required credit) off the top.
     */
    singleRow: Boolean = false,
    /** Focused poster, so the page can hand its artwork to the shell's backdrop. */
    onFocusItem: ((TmdbItem) -> Unit)? = null,
) {
    val glass = LocalLiquidGlass.current
    val shown = items.take(if (singleRow) SINGLE_ROW_ITEMS else MAX_SHELF_ITEMS)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = heading,
            style = if (glass) GlassType.rowTitle else MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        TmdbAttribution(
            modifier = Modifier.padding(top = 2.dp, bottom = if (singleRow) 10.dp else 14.dp),
        )
        if (singleRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                shown.forEachIndexed { index, item ->
                    TmdbPosterCard(
                        item = item,
                        onClick = { onSelect(item) },
                        // Long-press is the same action as click: a dead long-press on a TV
                        // remote reads as a broken card, and the dialog is all this shelf has.
                        onLongClick = { onSelect(item) },
                        modifier = if (index == 0 && firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier,
                        onFocus = onFocusItem?.let { callback -> { callback(item) } },
                    )
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxLines = 2,
            ) {
                shown.forEachIndexed { index, item ->
                    TmdbPosterCard(
                        item = item,
                        onClick = { onSelect(item) },
                        onLongClick = { onSelect(item) },
                        modifier = if (index == 0 && firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier,
                        onFocus = onFocusItem?.let { callback -> { callback(item) } },
                    )
                }
            }
        }
    }
}

/**
 * The notice TMDB's terms require wherever its data is shown.
 *
 * Small and muted on purpose: it has to be present, not compete with the shelf it credits.
 */
@Composable
fun TmdbAttribution(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.tmdb_attribution),
        style = MaterialTheme.typography.labelSmall,
        // A required notice, not a decorative footnote: dim enough not to compete with the
        // shelf, bright enough to still be readable across a room.
        color = Color.White.copy(alpha = 0.5f),
        modifier = modifier,
    )
}

/**
 * A TMDB title, described in place.
 *
 * Discovery titles are not in the library, so there is no detail screen to navigate to. A dialog
 * keeps the shelf's focus position and gives the overview somewhere to live. With Seerr connected
 * the dialog is also where a title becomes something the server will fetch: [onRequest] asks, and
 * the answer is shown back as waiting, downloading, or playable. [onFindInLibrary] stays as the
 * bridge for a title the viewer already owns.
 *
 * Stateless on purpose: three different shelves open this dialog, so the status lives in
 * [TmdbRequestViewModel] and is handed in by [TmdbDetailDialogHost].
 */
@Composable
fun TmdbDetailDialog(
    item: TmdbItem,
    onDismissRequest: () -> Unit,
    onFindInLibrary: ((TmdbItem) -> Unit)? = null,
    requestState: TmdbRequestUiState = TmdbRequestUiState(),
    onRequest: (() -> Unit)? = null,
    onOpenInLibrary: (() -> Unit)? = null,
    /** Whether this title is already in the viewer's discovery list. */
    inWatchlist: Boolean = false,
    onToggleWatchlist: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        // The panel has to be as wide as a description, not the platform's phone-sized default;
        // with the default width the text column is squeezed to half the card.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val glass = LocalLiquidGlass.current
        // The action row is built from state that arrives after the dialog opens, so the first
        // focusable child to exist is "Find in library". Left alone, the D-pad lands on the
        // secondary action and the primary Request/Open pill never gets the first press. The
        // requesters claim focus once, when the lit action appears.
        val primaryFocus = remember(item.id) { FocusRequester() }
        var primaryClaimed by remember(item.id) { mutableStateOf(false) }
        var primaryHasFocus by remember(item.id) { mutableStateOf(false) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier =
                    Modifier
                        .width(TmdbDialogWidth)
                        .testTag("tmdb_detail_pane")
                        .glassDialogSurface(shape = GlassTokens.panelShape)
                        .padding(26.dp),
            ) {
                AsyncImage(
                    model = TmdbService.imageUrl(item.posterPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .width(150.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayTitle,
                        style = if (glass) GlassType.sectionTitle else MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    val meta =
                        listOfNotNull(
                            if (item.isMovie) {
                                stringResource(R.string.movies_title)
                            } else {
                                stringResource(R.string.tv_shows_title)
                            },
                            item.displayYear,
                            item.voteAverage.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                        ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.66f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (item.overview.isNotBlank()) {
                        Text(
                            text = item.overview,
                            style = if (glass) GlassType.settingsSummary else MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    // One sentence about the server's answer. It sits above the controls because
                    // it explains what the control will do: a Request button with no context
                    // reads as a download that may or may not be allowed here.
                    tmdbRequestMessage(requestState.phase)?.let { message ->
                        Text(
                            text = stringResource(message),
                            style = if (glass) GlassType.meta else MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    val status = requestState.status
                    val showRequest = status != null && !status.isAvailable && requestState.phase != TmdbRequestPhase.LOADING
                    val showOpen = requestState.canOpenInLibrary && onOpenInLibrary != null
                    val litAction = (showRequest && requestState.canRequestNow) || showOpen
                    LaunchedEffect(litAction) {
                        if (!litAction || primaryClaimed) return@LaunchedEffect
                        primaryClaimed = true
                        // The dialog window assigns its own initial focus as it gains focus, and
                        // at that moment the only focusable child is the secondary action: the
                        // status has not arrived yet. A single request loses that race, so
                        // re-assert across the next few frames until the lit action holds focus.
                        repeat(3) {
                            withFrameNanos { }
                            primaryFocus.requestFocus()
                            delay(80)
                            if (primaryHasFocus) return@LaunchedEffect
                        }
                    }
                    if (showRequest || showOpen || onFindInLibrary != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            if (showRequest && status != null) {
                                val text =
                                    when {
                                        requestState.submitting -> stringResource(R.string.tmdb_requesting)
                                        status.availability == SeerrAvailability.PENDING ||
                                            status.availability == SeerrAvailability.PROCESSING -> stringResource(R.string.tmdb_requested)
                                        status.availability == SeerrAvailability.BLOCKLISTED -> stringResource(R.string.unavailable)
                                        else -> stringResource(R.string.tmdb_request)
                                    }
                                val enabled = requestState.canRequestNow
                                Button(
                                    // Guarded as well as disabled: a control that already sent
                                    // its request must not send a second one if something other
                                    // than the D-pad reaches its click action.
                                    onClick = { if (requestState.canRequestNow) onRequest?.invoke() },
                                    enabled = enabled,
                                    modifier =
                                        if (enabled) {
                                            Modifier
                                                .focusRequester(primaryFocus)
                                                .onFocusChanged { primaryHasFocus = it.isFocused }
                                        } else {
                                            Modifier
                                        },
                                    // Only a live action is lit. A disabled pill that keeps the
                                    // prominent fill would read as the thing to press after a
                                    // request has already been sent.
                                    glassProminent = glass && enabled,
                                ) {
                                    Text(text = text)
                                }
                            }
                            if (showOpen) {
                                Button(
                                    onClick = {
                                        onDismissRequest()
                                        onOpenInLibrary?.invoke()
                                    },
                                    modifier =
                                        Modifier
                                            .focusRequester(primaryFocus)
                                            .onFocusChanged { primaryHasFocus = it.isFocused },
                                    glassProminent = glass,
                                ) {
                                    Text(text = stringResource(R.string.tmdb_open_in_library))
                                }
                            }
                            if (onToggleWatchlist != null) {
                                // Deliberately not part of the primary focus claim: saving is a
                                // side thought next to requesting or opening, so the viewer
                                // should still land on the action that completes the flow.
                                Button(
                                    onClick = onToggleWatchlist,
                                    // Text inside the pane, not a second chip: saving is a
                                    // bookmark beside the action that leads to watching, and a
                                    // filled pill beside the lit one made the row read as two
                                    // primary buttons. The focus wash is the only fill it gets.
                                    // A pill, not the default circle: the wash has to follow the
                                    // label, or a two-word action is drawn inside a grey disc.
                                    shape =
                                        ClickableSurfaceDefaults.shape(
                                            shape = RoundedCornerShape(percent = 50),
                                        ),
                                    glass = glass,
                                ) {
                                    Text(
                                        text =
                                            stringResource(
                                                if (inWatchlist) R.string.tmdb_watchlist_saved else R.string.tmdb_watchlist_add,
                                            ),
                                    )
                                }
                            }
                            if (onFindInLibrary != null) {
                                Button(
                                    onClick = {
                                        onDismissRequest()
                                        onFindInLibrary.invoke(item)
                                    },
                                    // With no request or open action this is the lit capsule: on
                                    // a smoked pane an unfilled button reads as a label, and
                                    // this is the step that turns discovery into something
                                    // watchable. Saving is a bookmark beside it, not the
                                    // dialog's purpose, so it stays secondary.
                                    glassProminent = glass && !showRequest && !showOpen,
                                    // Beside a lit action this is the secondary choice, and a
                                    // secondary control inside a glass dialog must still be
                                    // glass: the classic focused container is `onSurface`, which
                                    // is white in this colour scheme, so the label drew white on
                                    // white and the pill looked empty.
                                    glassFilled = glass,
                                ) {
                                    Text(
                                        text = stringResource(R.string.tmdb_find_in_library),
                                    )
                                }
                            }
                        }
                    }
                    // No weight on anything above: a weighted child makes the column consume the
                    // whole dialog window, and the pane then covers the screen instead of
                    // hugging the card.
                    TmdbAttribution(
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
            }
        }
    }
}

/**
 * The discovery dialog with its request state attached.
 *
 * The shelves should not each have to know how a request is made. They open this, it asks Seerr
 * what the server already has, and the dialog gains one more honest answer for a title the
 * library does not own. A viewer with no Seerr connected sees exactly the dialog they saw before.
 */
@Composable
fun TmdbDetailDialogHost(
    item: TmdbItem,
    onDismissRequest: () -> Unit,
    onFindInLibrary: ((TmdbItem) -> Unit)? = null,
    viewModel: TmdbRequestViewModel = hiltViewModel(),
    watchlistViewModel: TmdbWatchlistViewModel = hiltViewModel(),
) {
    // A profile with Seerr switched off should not see a request button, and should not make a
    // request-status call just to be told it cannot ask. The dialog is otherwise unchanged.
    val seerrEnabled = LocalUserPreferences.current?.seerrEnabled ?: true
    LaunchedEffect(item.id, item.isMovie, seerrEnabled) {
        if (seerrEnabled) viewModel.load(item)
    }
    val requestState by viewModel.state.collectAsStateWithLifecycle()
    val watchlist by watchlistViewModel.items.collectAsStateWithLifecycle()
    TmdbDetailDialog(
        item = item,
        onDismissRequest = onDismissRequest,
        onFindInLibrary = onFindInLibrary,
        requestState = if (seerrEnabled) requestState else TmdbRequestUiState(),
        onRequest = if (seerrEnabled) ({ viewModel.request(item) }) else null,
        onOpenInLibrary = {
            onDismissRequest()
            viewModel.openInLibrary(item)
        },
        inWatchlist = watchlist.containsWatchlist(item),
        onToggleWatchlist = { watchlistViewModel.toggle(item) },
    )
}

/**
 * The one sentence a request phase needs, or nothing when the phase explains itself.
 *
 * A request in flight is silent on purpose: the button already says "Requesting…", and a line
 * repeating it would make the dialog talk over itself.
 */
@StringRes
private fun tmdbRequestMessage(phase: TmdbRequestPhase): Int? =
    when (phase) {
        TmdbRequestPhase.HIDDEN,
        TmdbRequestPhase.LOADING,
        TmdbRequestPhase.SUBMITTING,
        -> null

        TmdbRequestPhase.FAILED -> R.string.tmdb_request_failed
        TmdbRequestPhase.REQUESTABLE -> R.string.tmdb_request_hint
        TmdbRequestPhase.DISABLED -> R.string.tmdb_request_disabled
        TmdbRequestPhase.REQUESTED -> R.string.tmdb_request_pending
        TmdbRequestPhase.PROCESSING -> R.string.tmdb_request_processing
        TmdbRequestPhase.PARTIAL -> R.string.tmdb_request_partial
        TmdbRequestPhase.AVAILABLE -> R.string.tmdb_request_available
        TmdbRequestPhase.BLOCKED -> R.string.tmdb_request_blocked
    }

private val TmdbCardWidth = 156.dp
private val TmdbDialogWidth = 720.dp

/** Two rows of seven fill the page without a third row pushing the credit out of view. */
private const val MAX_SHELF_ITEMS = 7

/**
 * A single row has to fit the width once: five posters at 156dp plus the gaps clear a 960dp
 * television without the last card hanging off the edge.
 */
private const val SINGLE_ROW_ITEMS = 5
