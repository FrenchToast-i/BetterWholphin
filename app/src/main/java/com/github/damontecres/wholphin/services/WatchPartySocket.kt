package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.sockets.SocketConnection
import org.jellyfin.sdk.api.sockets.SocketConnectionState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The raw text of every frame the server pushes over the app's socket.
 *
 * The SDK models a SyncPlay group update as a `GroupUpdate`, which carries only the group id and
 * the update type. The payload that actually says who is in the room, or which title the party is
 * on, is nested one level deeper (`Data.Data`) and the model does not declare it, so kotlinx drops
 * it without a word. A watch party cannot follow a queue it never sees.
 *
 * The connection the SDK opens is therefore wrapped by [TappedSocketConnection] and every frame is
 * republished here, for [WatchPartyService] to decode with the same serializer the SDK uses. The
 * tap is a mirror, not a replacement: the SDK still receives and dispatches every message.
 */
@Singleton
class WatchPartySocketTap
    @Inject
    constructor() {
        private val _frames =
            MutableSharedFlow<String>(
                // Deep enough for a burst of group updates; a frame that arrives while a dialog is
                // busy is still worth more than a blocked socket, so the oldest is dropped.
                extraBufferCapacity = 128,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val frames = _frames.asSharedFlow()

        fun onFrame(text: String) {
            _frames.tryEmit(text)
        }
    }

/**
 * Passes a [SocketConnection] through unchanged while mirroring its messages to [tap].
 *
 * [state] is the delegate's own flow rather than a re-published copy, so the SDK's own dispatch
 * keeps the exact behaviour it had before the tap existed.
 */
class TappedSocketConnection(
    private val delegate: SocketConnection,
    private val tap: WatchPartySocketTap,
    scope: CoroutineScope,
) : SocketConnection {
    override val state: StateFlow<SocketConnectionState> = delegate.state

    init {
        scope.launch {
            delegate.state.collect { current ->
                if (current is SocketConnectionState.Message) {
                    current.message?.let(tap::onFrame)
                }
            }
        }
    }

    override suspend fun connect(
        url: String,
        clientName: String,
        clientVersion: String,
        deviceId: String,
        deviceName: String,
        accessToken: String,
    ): Boolean = delegate.connect(url, clientName, clientVersion, deviceId, deviceName, accessToken)

    override suspend fun send(message: String): Boolean = delegate.send(message)

    override suspend fun disconnect() {
        delegate.disconnect()
    }
}
