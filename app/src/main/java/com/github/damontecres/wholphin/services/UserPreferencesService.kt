package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Get the current user's [UserPreferences]
 */
@Singleton
class UserPreferencesService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
        private val accountPreferencesService: AccountPreferencesService,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        val flow: Flow<UserPreferences> =
            preferencesDataStore.data
                .combine(serverRepository.currentUserFlow) { appPrefs, user -> appPrefs to user }
                .flatMapLatest { (appPrefs, user) ->
                    // The account overlay lives in a separate store, so the combined object has
                    // to wait for it: resolving the glass flag against an empty overlay would
                    // flash the device default for one frame on every profile switch.
                    accountPreferencesService.flowFor(user?.id).map { account ->
                        UserPreferences(appPrefs, user?.appPreferences, account)
                    }
                }

        suspend fun getCurrent(): UserPreferences = flow.first()
    }
