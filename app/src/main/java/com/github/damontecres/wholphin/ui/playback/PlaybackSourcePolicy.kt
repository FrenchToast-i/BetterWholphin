package com.github.damontecres.wholphin.ui.playback

import org.jellyfin.sdk.model.api.MediaSourceInfo

/**
 * The two decisions that keep playback independent of the server.
 *
 * Kept out of the ViewModel so the rules can be unit tested without an Android player: when the
 * server is allowed to convert anything, and whether a live channel carries a link this device
 * can open itself.
 */
object PlaybackSourcePolicy {
    /**
     * The tuner's own URL, when Jellyfin returned one.
     *
     * Only http(s): a local tuner can expose a device path that the television cannot open by
     * itself, and a raw UDP/RTP address is not something ExoPlayer can fetch.
     */
    fun directLiveUrl(source: MediaSourceInfo?): String? = directLiveUrl(source?.path)

    fun directLiveUrl(path: String?): String? =
        path
            ?.trim()
            ?.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }

    /**
     * Transcoding is off unless the viewer allowed it, and an explicit "Play with transcoding"
     * always wins for that one title.
     */
    fun allowsTranscoding(
        forceTranscoding: Boolean,
        allowTranscoding: Boolean,
    ): Boolean = forceTranscoding || allowTranscoding
}
