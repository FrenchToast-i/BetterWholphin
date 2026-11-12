package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.theme.GlassBackdropWash
import com.github.damontecres.wholphin.ui.theme.LocalBackdropLight
import com.github.damontecres.wholphin.ui.theme.LocalBackdropLuminance
import com.github.damontecres.wholphin.ui.theme.LocalGlassBackdrop
import com.github.damontecres.wholphin.ui.theme.LocalLiquidGlass
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * The preferences of the current user, provided above the navigation host.
 *
 * A page is composed inside a [NavEntry], and an entry is built once per destination. A
 * preference captured when the entry was created therefore stays frozen for as long as that page
 * is on screen: switching Liquid Glass on in Settings, or hiding an item from the top bar, did not
 * reach the shell until the viewer navigated somewhere else. Reading the provider instead means
 * every page, and the bar above it, sees the value that is current now.
 */
val LocalUserPreferences = staticCompositionLocalOf<UserPreferences?> { null }

// Top scrim configuration for text readability (clock, season tabs)
const val TOP_SCRIM_ALPHA = 0.55f
const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel() {
        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
        }
    }

/**
 * Glass keeps the last artwork when a page asks for the backdrop to be cleared.
 *
 * A pane refracts its environment, so a destination that blanks the artwork leaves every sheet
 * and capsule with nothing to sample and the material collapses into dark chrome. The classic UI
 * still clears, because there the backdrop belongs to the page rather than to the frame.
 */
private fun keepBackdropForGlass(
    liquidGlass: Boolean,
    clear: () -> Unit,
): () -> Unit = if (liquidGlass) ({}) else clear

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    navigationManager: NavigationManager,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    val preferences =
        LocalUserPreferences.current
            ?: error("ApplicationContent requires LocalUserPreferences to be provided")
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val liquidGlass = preferences.liquidGlass
    // A single captured layer that every glass surface samples from
    val glassBackdrop = if (liquidGlass) rememberLayerBackdrop() else null
    // The palette drives both the wash over the artwork and how much smoke a copy pane needs:
    // a pale frame cannot hold white labels through clear glass.
    val backdrop by viewModel.backdropService.backdropFlow.collectAsStateWithLifecycle()
    Box(
        modifier = modifier,
    ) {
        // The backdrop is left exactly as the user configured it, including the dynamic colour
        // wash derived from the focused item's artwork. Glass mode changes the material over the
        // background, not the background itself.
        val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (glassBackdrop != null) {
                            Modifier.layerBackdrop(glassBackdrop)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Backdrop(
                drawerIsOpen = !liquidGlass && drawerState.isOpen,
                backdropStyle = backdropStyle,
                enableTopScrim = enableTopScrim,
                viewModel = viewModel,
                // The artwork still follows the focused item, but glass mode bleeds it across the
                // frame so every pane has something to refract.
                fullBleed = liquidGlass,
            )
            if (liquidGlass) {
                // The stock dynamic-colour backdrop, layered on top of the artwork: the frame
                // keeps taking its palette from whatever is focused.
                GlassBackdropWash(
                    primary = backdrop.primaryColor.orEmpty(),
                    secondary = backdrop.secondaryColor.orEmpty(),
                    tertiary = backdrop.tertiaryColor.orEmpty(),
                )
            }
        }
        val navDrawerListState = rememberLazyListState()
        CompositionLocalProvider(
            LocalLiquidGlass provides liquidGlass,
            LocalGlassBackdrop provides glassBackdrop,
            LocalBackdropLight provides backdrop.isPale,
            LocalBackdropLuminance provides backdrop.luminance,
        ) {
            NavDisplay(
                backStack = navigationManager.backStack,
                onBack = { navigationManager.goBack() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = { key ->
                    key as Destination
                    val contentKey = "${key}_${server?.id}_${user?.id}"
                    NavEntry(key, contentKey = contentKey) {
                        // Read the mode here rather than closing over the value from above: an
                        // entry that was already composed (Settings, typically) otherwise keeps
                        // the shell it was built with, and switching Liquid Glass on leaves that
                        // page without a bar until the user navigates away.
                        val glass = LocalLiquidGlass.current
                        // Settings is full screen in the classic UI, but in glass mode it keeps the
                        // rail so navigation stays one press away from every page.
                        val keepRail =
                            glass && key is Destination.Settings
                        if (key.fullScreen && !keepRail) {
                            DestinationContent(
                                destination = key,
                                preferences = LocalUserPreferences.current ?: preferences,
                                onClearBackdrop = keepBackdropForGlass(glass, viewModel::clearBackdrop),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (user != null && server != null) {
                            // The glass rail expects content that leaves room for it
                            NavDrawer(
                                destination = key,
                                preferences = LocalUserPreferences.current ?: preferences,
                                user = user,
                                server = server,
                                drawerState = drawerState,
                                navDrawerListState = navDrawerListState,
                                onClearBackdrop = keepBackdropForGlass(glass, viewModel::clearBackdrop),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            ErrorMessage("Trying to go to $key without a user logged in", null)
                        }
                    }
                },
            )
        }
    }
}

/** Unset palette entries simply contribute nothing to the wash. */
private fun Color.orEmpty(): Color = if (isSpecified) this else Color.Transparent
