package com.github.damontecres.wholphin.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.TmdbItem
import com.github.damontecres.wholphin.services.TmdbService
import com.github.damontecres.wholphin.services.TmdbWatchlistService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The state around "save this discovery title".
 *
 * The list itself lives in [TmdbWatchlistService]; this exposes it to the dialog and to the home
 * row so both draw the same answer the moment either one changes it. Saving from the dialog is
 * therefore visible in the row behind it without reopening the page.
 */
@HiltViewModel
class TmdbWatchlistViewModel
    @Inject
    constructor(
        private val watchlist: TmdbWatchlistService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val items: StateFlow<List<TmdbItem>> = watchlist.items

        fun toggle(item: TmdbItem) {
            watchlist.toggle(item)
        }

        /**
         * Hand a focused poster's artwork to the shell.
         *
         * A TMDB title has no library item id, so it cannot go through
         * `BackdropService.submit(BaseItem)`. Without this the home page keeps refracting the
         * last library row while focus moves across saved posters.
         */
        fun submitBackdrop(item: TmdbItem) {
            val url = TmdbService.imageUrl(item.backdropPath, size = "w1280") ?: return
            viewModelScope.launch {
                backdropService.submit("tmdb_${item.id}", url)
            }
        }
    }
