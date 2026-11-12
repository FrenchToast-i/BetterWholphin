package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.TmdbAttribution
import com.github.damontecres.wholphin.ui.components.TmdbDetailDialogHost
import com.github.damontecres.wholphin.ui.components.TmdbPosterCard
import com.github.damontecres.wholphin.ui.components.TmdbWatchlistViewModel

/**
 * The home row made of titles the viewer saved while browsing discovery.
 *
 * This is the other half of the TMDB dialog: trending and "because you're watching" find things
 * the library does not own, and without somewhere to put them a discovery title was gone the
 * moment its dialog closed. The row is the answer to "what was that film I saw last week" — it
 * keeps the poster, the title and the year, so it draws with no network at all.
 *
 * Hidden while empty rather than showing a "save something" prompt on a home page the viewer did
 * not ask to configure. The dialog is where saving is taught, and only after they have found
 * something worth keeping.
 */
@Composable
fun WatchlistShelf(
    onFindInLibrary: (TmdbItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TmdbWatchlistViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    // Held outside the "is there a row" branch: removing the last title empties the row, and if
    // the dialog lived inside it the pane would vanish under the viewer's finger the moment they
    // pressed the button — which reads as a crash, not as an undo.
    var details by remember { mutableStateOf<TmdbItem?>(null) }

    // The row is drawn inside a Column that is always composed, so emptying the list does not
    // change the shape of the composition tree around the dialog. Branching the whole shelf out
    // when the list emptied recreated the dialog and dropped its focus onto the pane.
    Column(modifier = modifier.fillMaxWidth()) {
        if (items.isNotEmpty()) {
            ItemRow(
                title = stringResource(R.string.tmdb_watchlist),
                items = items,
                onClickItem = { _, item -> details = item },
                // A dead long-press on a TV remote reads as a broken card; the dialog is the
                // only action this row has.
                onLongClickItem = { _, item -> details = item },
                horizontalPadding = 16.dp,
                showViewMore = false,
                // TMDB's terms require the notice wherever its data is shown. It hugs the
                // heading for the same reason it does on the discovery rail: when focus moves
                // into the cards the page scrolls the heading away, and a trailing credit would
                // be the first thing clipped.
                subtitle = { TmdbAttribution(Modifier.padding(top = 2.dp, bottom = 6.dp)) },
                cardContent = { _, item, cardModifier, onClick, onLongClick ->
                    if (item != null) {
                        TmdbPosterCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = cardModifier,
                            // 2:3 at the home rows' 172dp card height, on the same baseline as
                            // every library row around it.
                            width = 115.dp,
                            // Posters only: a home row has to fit the fixed hero above it, and a
                            // caption under the poster is what pushed the focused card past the
                            // bottom edge. The dialog has the title when the art does not.
                            showCaption = false,
                            onFocus = { viewModel.submitBackdrop(item) },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    details?.let { item ->
        TmdbDetailDialogHost(
            item = item,
            onDismissRequest = { details = null },
            onFindInLibrary = onFindInLibrary,
        )
    }
}

