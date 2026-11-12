package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.services.AccountPreferences
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.UserConfiguration

/**
 * A combination of the app-specific preferences and server-side user configuration
 */
data class UserPreferences(
    val appPreferences: AppPreferences,
    val userPreferences: JellyfinUserPreferences?,
    /** Settings that belong to the signed-in account rather than to the device. */
    val account: AccountPreferences = AccountPreferences.Default,
) {
    /**
     * Whether the Liquid Glass material is on, resolved for this account.
     *
     * An account may override the device switch; with no override the device value is the
     * answer. Every surface reads this rather than the raw preference so the two profiles on a
     * television can disagree about the look.
     */
    val liquidGlass: Boolean
        get() = account.liquidGlass ?: appPreferences.interfacePreferences.liquidGlass

    /**
     * The TMDB key in force for this account.
     *
     * `null` on the account means "use the device key"; an empty string is an explicit choice
     * not to discover, so it must not fall through to the device key.
     */
    val tmdbApiKey: String
        get() =
            (account.tmdbApiKey ?: appPreferences.interfacePreferences.tmdbApiKey).trim()

    /** Whether this profile takes part in the household's Seerr connection. */
    val seerrEnabled: Boolean
        get() = account.seerrEnabled
}

val DefaultUserConfiguration =
    UserConfiguration(
        playDefaultAudioTrack = true,
        displayMissingEpisodes = false,
        groupedFolders = listOf(),
        subtitleMode = SubtitlePlaybackMode.DEFAULT,
        displayCollectionsView = false,
        enableLocalPassword = false,
        orderedViews = listOf(),
        latestItemsExcludes = listOf(),
        myMediaExcludes = listOf(),
        hidePlayedInLatest = true,
        rememberAudioSelections = true,
        rememberSubtitleSelections = true,
        enableNextEpisodeAutoPlay = true,
    )
