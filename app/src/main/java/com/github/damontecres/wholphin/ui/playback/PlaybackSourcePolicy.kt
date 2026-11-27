package com.github.damontecres.wholphin.ui.playback

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod

/**
 * The decisions that keep playback independent of the server.
 *
 * Kept out of the ViewModel so the rules can be unit tested without an Android player: when the
 * server is allowed to convert anything, whether a live channel carries a link this device
 * can open itself, and how long a channel that is still starting is worth waiting for.
 */
object PlaybackSourcePolicy {
    /**
     * How many times a live stream that answers with a bad status is retried before the viewer is
     * told the channel is off air.
     */
    const val MAX_LIVE_RETRIES = 2

    /**
     * The tuner's own URL, when Jellyfin returned one.
     *
     * Only http(s): a local tuner can expose a device path that the television cannot open by
     * itself, and a raw UDP/RTP address is not something ExoPlayer can fetch. The link is still
     * the server's view of it until [resolveProviderUrl] has mapped its host.
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
     * Names that mean "this machine" to the server and nothing to a television.
     *
     * Jellyfin fetches a live channel's provider from inside its own container, so the provider
     * link it stores is written for that network namespace; the container runtime's well-known
     * aliases are as unusable on the LAN as `localhost` is.
     */
    private val serverLocalHosts =
        setOf(
            "localhost",
            "0.0.0.0",
            "127.0.0.1",
            "::1",
            "host.containers.internal",
            "host.docker.internal",
            "gateway.docker.internal",
            "host.lima.internal",
        )

    /**
     * A provider link as this device has to open it.
     *
     * The provider is published on the server's host, but Jellyfin writes its link with the name
     * it uses from inside the container. Left alone, a channel fails before a single segment
     * arrives (`UnknownHostException`), and so does every segment of a playlist that lists the
     * internal name. Keeping the provider's port and path while swapping in the server's host is
     * the same stream from the provider's side and an address the television can actually reach.
     *
     * A link for a host this device can already resolve is returned untouched, and so is one when
     * the server's own address is unknown.
     */
    fun resolveProviderUrl(
        url: String?,
        serverUrl: String?,
    ): String? {
        val link = url ?: return null
        val parsed = link.toHttpUrlOrNull() ?: return link
        val host = parsed.host.trim('[', ']').lowercase()
        if (host !in serverLocalHosts && !host.endsWith(".localhost")) return link
        val server = serverUrl?.toHttpUrlOrNull() ?: return link
        return parsed.newBuilder().host(server.host).build().toString()
    }

    /**
     * Transcoding is off unless the viewer allowed it, and an explicit "Play with transcoding"
     * always wins for that one title.
     */
    fun allowsTranscoding(
        forceTranscoding: Boolean,
        allowTranscoding: Boolean,
    ): Boolean = forceTranscoding || allowTranscoding

    /**
     * Whether to ask the server for intros before the title.
     *
     * A channel tune is already on the air, so the request's answer is always empty while it sits
     * between the button press and the stream request -- on the one path where the viewer is
     * waiting for the game rather than for a trailer.
     */
    fun wantsIntros(
        type: BaseItemKind,
        positionMs: Long,
        cinemaMode: Boolean,
    ): Boolean = cinemaMode && positionMs == 0L && type != BaseItemKind.TV_CHANNEL

    /**
     * Whether a failed channel segment is worth another attempt.
     *
     * A remux or a transcode has no first segment until the server's encoder has produced one, and
     * it answers 5xx until then; reading that as "this game has not started" returned the viewer to
     * the guide from a channel that was a second from playing. A direct play runs no encoder, so
     * its bad status is a real answer and repeating it only delays the message.
     */
    fun retriesLiveStream(
        type: BaseItemKind,
        badHttpStatus: Boolean,
        playMethod: PlayMethod,
        attempts: Int,
    ): Boolean =
        type == BaseItemKind.TV_CHANNEL &&
            badHttpStatus &&
            playMethod != PlayMethod.DIRECT_PLAY &&
            attempts < MAX_LIVE_RETRIES
}
