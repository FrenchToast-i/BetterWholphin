package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SurpriseService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * The bar's "Play something" action.
 *
 * Lives in a ViewModel rather than in the click lambda because the pick is two server queries and
 * the page that asked is not where the answer goes: the viewer is sent straight into playback.
 */
@HiltViewModel
class SurpriseViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val surpriseService: SurpriseService,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private var picking = false

        fun playSomething() {
            // A second press while the first pick is in flight would queue a second playback.
            if (picking) return
            picking = true
            viewModelScope.launchIO {
                try {
                    val item = surpriseService.pick()
                    if (item == null) {
                        showToast(
                            context,
                            context.getString(R.string.play_something_empty),
                            Toast.LENGTH_SHORT,
                        )
                    } else {
                        Timber.v("Play something picked %s", item.id)
                        // A series answers with its next unwatched episode and the rest of the
                        // queue, because that is what the app already does when a series starts.
                        navigationManager.navigateTo(Destination.Playback(item.id, 0))
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not pick something to play")
                    showToast(
                        context,
                        context.getString(R.string.play_something_failed),
                        Toast.LENGTH_SHORT,
                    )
                } finally {
                    picking = false
                }
            }
        }
    }
