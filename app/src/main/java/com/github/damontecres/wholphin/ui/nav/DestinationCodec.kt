package com.github.damontecres.wholphin.ui.nav

import com.github.damontecres.wholphin.util.requestSerializersModule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encodes and decodes the navigation back stack.
 *
 * The stack has to survive the process (saved instance state, and the continuity store that
 * restores the last screen after Android kills the app), and both callers must agree on the
 * format: a stack written by one and read by a differently configured [Json] would decode to
 * nothing. One codec, one configuration.
 *
 * Not every [Destination] is serializable (a grid carries a contextual request type), so both
 * directions are best-effort: a stack that cannot be encoded is simply not persisted instead of
 * crashing the caller.
 */
object DestinationCodec {
    private val json =
        Json {
            classDiscriminator = "_type"
            serializersModule = requestSerializersModule
            // A store written by an older build can hold a field that has since been renamed.
            ignoreUnknownKeys = true
        }

    fun encode(stack: List<Destination>): String? =
        runCatching { json.encodeToString(stack) }
            .getOrNull()

    fun decode(value: String): List<Destination>? =
        runCatching { json.decodeFromString<List<Destination>>(value) }
            .getOrNull()
}
