@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.github.damontecres.wholphin.ui.playback.PlaybackSourcePolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.io.IOException
import java.net.ProtocolException

/**
 * A [DataSource] that opens a live channel's provider through an address the device can reach.
 *
 * Jellyfin relays a channel from inside its container, so the provider link it hands out (and the
 * absolute segment links inside the provider's playlist) name the container's own host. Those
 * names resolve only on the server, and every request to them fails with an unknown host before
 * any picture arrives. See [PlaybackSourcePolicy.resolveProviderUrl] for the mapping; it has to
 * happen on every open rather than only on the playlist, because HLS resolves each segment against
 * the playlist and asks for it as an absolute URL.
 */
@UnstableApi
class ProviderHostDataSource(
    private val upstream: DataSource,
    private val serverUrl: () -> String?,
) : DataSource {
    private var expectedLength: Long = -1
    private var bytesRead: Long = 0
    private var segment: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val resolved = PlaybackSourcePolicy.resolveProviderUrl(dataSpec.uri.toString(), serverUrl())
        val builder = dataSpec.buildUpon()
        if (resolved != null && resolved != dataSpec.uri.toString()) {
            builder.setUri(Uri.parse(resolved))
        }
        val rewritten = builder.build()
        Timber.v("Provider stream open: %s -> %s", dataSpec.uri, rewritten.uri)
        expectedLength = -1
        bytesRead = 0
        segment = isProviderSegment(rewritten.uri)
        return try {
            upstream.open(rewritten).also { expectedLength = it }
        } catch (ex: IOException) {
            Timber.w(ex, "Provider stream could not be opened: %s", rewritten.uri)
            throw ex
        }
    }

    /**
     * Whether this request is one transport-stream segment served by the provider itself.
     *
     * A live provider writes a segment while the television is already asking for it, so the
     * last packet can be cut short and the connection closes a few bytes before the length the
     * headers promised. That trailing fragment carries no whole frame, and failing the channel
     * over it drops the viewer out of a game that is on the air; the bytes that did arrive are
     * the segment. Jellyfin's own responses are not treated this way -- a short film is a real
     * failure -- so this is limited to segments on a different authority than the server.
     */
    private fun isProviderSegment(uri: Uri): Boolean {
        val path = uri.path ?: return false
        if (!path.endsWith(".ts", ignoreCase = true)) return false
        val server = serverUrl()?.toHttpUrlOrNull() ?: return true
        val url = uri.toString().toHttpUrlOrNull() ?: return false
        return url.host != server.host || url.port != server.port
    }

    @Throws(IOException::class)
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val read =
            try {
                upstream.read(buffer, offset, length)
            } catch (ex: IOException) {
                // Only the body ending early is tolerated, and only for a provider segment; a
                // reset or a timeout still reaches the player as the failure it is.
                if (segment && ex.endsWithBody()) {
                    Timber.w(
                        "Provider segment ended %s bytes short of %s, keeping what arrived: %s",
                        expectedLength - bytesRead,
                        expectedLength,
                        upstream.uri,
                    )
                    return C.RESULT_END_OF_INPUT
                }
                Timber.w(
                    ex,
                    "Provider stream read failed after %s of %s bytes: %s",
                    bytesRead,
                    expectedLength,
                    upstream.uri,
                )
                throw ex
            }
        if (read > 0) bytesRead += read
        return read
    }

    /** The HTTP stack reports a short body as a [ProtocolException] wrapped in its own error. */
    private fun Throwable.endsWithBody(): Boolean {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is ProtocolException &&
                cause.message?.contains("unexpected end of stream", ignoreCase = true) == true
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    override fun getUri(): Uri? = upstream.uri

    @Throws(IOException::class)
    override fun close() {
        upstream.close()
    }
}

/** Creates [ProviderHostDataSource]s over the standard file/http stack. */
@UnstableApi
class ProviderHostDataSourceFactory(
    private val context: Context,
    private val serverUrl: () -> String?,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        ProviderHostDataSource(
            DefaultDataSource.Factory(context).createDataSource(),
            serverUrl,
        )
}
