package com.github.damontecres.wholphin.services

import androidx.navigation3.runtime.NavBackStack
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating between pages and manages the app's back stack
 */
@Singleton
class NavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<Destination> = NavBackStack(Destination.Home())

    /**
     * Called after every navigation with a snapshot of the new stack.
     *
     * A single slot rather than a listener list: continuity has exactly one owner, the activity,
     * and a lambda left behind by a finished activity must not keep receiving navigations. The
     * sink is invoked from [log], which every mutator already funnels through, so no navigation
     * can be forgotten.
     */
    var onBackStackChanged: ((List<Destination>) -> Unit)? = null

    /**
     * Increments whenever [backStack] changes, including a wholesale restore.
     *
     * The navigation drawer highlights the destination at the top of the stack, and it used to
     * sample the stack once when it first composed. A cold start restores the stack *after* that,
     * so the viewer came back to their series page with Home still lit in the bar (and Back never
     * moved the highlight either). This is the signal that keeps the chrome honest.
     */
    val stackVersion = MutableStateFlow(0)

        /**
         * Go to the specified [com.github.damontecres.wholphin.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
            backStack.add(destination)
            log()
        }

        /**
         * Go to the specified [Destination], but reset the back stack to Home first
         */
        fun navigateToFromDrawer(destination: Destination) {
            goToHome()
            backStack.add(destination)
            log()
        }

        /**
         * Go to the previous page
         */
        fun goBack() {
            synchronized(this) {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            }
            log()
        }

        /**
         * Go all the way back to the home page
         */
        fun goToHome() {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack[0] !is Destination.Home) {
                backStack[0] = Destination.Home()
            }
            log()
        }

        /**
         * Go all the way back to the home page, and reload it from scratch
         */
        fun reloadHome() {
            goToHome()
            val id = (backStack[0] as Destination.Home).id + 1
            backStack[0] = Destination.Home(id)
            log()
        }

        /**
         * Resets the backstack to the specified destination
         */
        fun replace(destination: Destination) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack.isEmpty()) {
                backStack.add(0, destination)
            } else {
                backStack[0] = destination
            }
            log()
        }

        /**
         * Replaces the whole stack, e.g. when re-opening the page the viewer was last on.
         *
         * Goes through [log] like every other mutation: the drawer has to be told that the
         * current place changed, and continuity has to record what was restored.
         */
        fun replaceStack(destinations: List<Destination>) {
            backStack.clear()
            backStack.addAll(destinations)
            log()
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
            stackVersion.value++
            onBackStackChanged?.invoke(backStack.toList())
        }
    }
