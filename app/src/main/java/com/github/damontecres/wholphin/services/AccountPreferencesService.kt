package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The settings that belong to the person watching rather than to the television.
 *
 * The app's preference file is one object for the device, which is right for a player backend or
 * a skip-forward interval: those describe the hardware and the household. A look and a discovery
 * key are personal. Two profiles on one television should be able to disagree about Liquid Glass,
 * and one profile's TMDB key should not light up another profile's search page.
 *
 * Only the fields that genuinely differ per account live here. Everything else stays in
 * [com.github.damontecres.wholphin.preferences.AppPreferences] so this file does not become a
 * second copy of the settings tree.
 */
@Serializable
data class AccountPreferences(
    /** `null` means "follow the device setting", which is what every existing install gets. */
    val liquidGlass: Boolean? = null,
    /**
     * The account's own TMDB key. `null` falls back to the device key; a blank string is a
     * deliberate "no discovery on this account" and does not fall back.
     */
    val tmdbApiKey: String? = null,
    /**
     * Whether this profile uses the household's Seerr connection.
     *
     * The Seerr login is already stored per Jellyfin user, so one profile's request history is
     * never another's. This flag answers the different question: should this profile see the
     * Requests destination, the request shelf and the request actions at all. A child profile on
     * a server without request permissions otherwise grows a Discover door that only ever
     * refuses.
     */
    val seerrEnabled: Boolean = true,
) {
    companion object {
        val Default = AccountPreferences()
    }
}

/**
 * Reads and writes [AccountPreferences] per Jellyfin user.
 *
 * Keys are namespaced by user id through [KeyValueService], the same per-user mechanism the
 * recent-search history and the sports link store already use, so switching profiles switches
 * settings without copying a preferences file.
 */
@Singleton
class AccountPreferencesService
    @Inject
    constructor(
        private val keyValueService: KeyValueService,
        private val serverRepository: ServerRepository,
    ) {
        /** The current account's preferences, or the defaults when nobody is signed in. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val current: Flow<AccountPreferences> =
            serverRepository.currentUserFlow
                .map { it?.id }
                .distinctUntilChanged()
                .flatMapLatest { userId -> flowFor(userId) }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun flowFor(userId: UUID?): Flow<AccountPreferences> =
            if (userId == null) {
                flowOf(AccountPreferences.Default)
            } else {
                keyValueService.get(userId, KEY, AccountPreferences.Default)
            }

        /**
         * Resolve the look for an account, falling back to the device's own switch.
         *
         * This is the one place the fallback is written down; every surface that needs to know
         * whether glass is on reads the resolved value from
         * [com.github.damontecres.wholphin.preferences.UserPreferences] instead of asking twice.
         */
        fun resolveLiquidGlass(
            account: AccountPreferences,
            deviceDefault: Boolean,
        ): Boolean = account.liquidGlass ?: deviceDefault

        suspend fun setLiquidGlass(enabled: Boolean) {
            updateCurrent { it.copy(liquidGlass = enabled) }
        }

        suspend fun setSeerrEnabled(enabled: Boolean) {
            updateCurrent { it.copy(seerrEnabled = enabled) }
        }

        suspend fun setTmdbApiKey(key: String) {
            // Blank is stored as an empty override, not as null: that is how an account says
            // "do not discover for me" on a television where the device key is configured.
            updateCurrent { it.copy(tmdbApiKey = key.trim()) }
        }

        suspend fun updateCurrent(transform: (AccountPreferences) -> AccountPreferences) {
            val userId = serverRepository.currentUser?.id ?: return
            val existing = keyValueService.get(userId, KEY, AccountPreferences.Default)
            // The write is a whole-object replace (the key-value store has no atomic update), so
            // read the latest value first. Settings writes are one press at a time on a TV, and
            // the only fields here are two, so a lost update would require two simultaneous
            // presses on two different rows.
            val value = transform(existing.first())
            keyValueService.save(userId, KEY, value)
        }

        companion object {
            private const val KEY = "accountPreferences"
        }
    }
