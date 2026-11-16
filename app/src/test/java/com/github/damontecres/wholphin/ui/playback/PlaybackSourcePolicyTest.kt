package com.github.damontecres.wholphin.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourcePolicyTest {
    @Test
    fun `direct live url accepts a trimmed http provider link`() {
        assertEquals(
            "http://provider.example/live/123.m3u8",
            PlaybackSourcePolicy.directLiveUrl(" http://provider.example/live/123.m3u8 "),
        )
    }

    @Test
    fun `direct live url accepts https`() {
        assertEquals(
            "https://example.test/a.ts",
            PlaybackSourcePolicy.directLiveUrl("https://example.test/a.ts"),
        )
    }

    @Test
    fun `direct live url rejects a local device path`() {
        assertNull(PlaybackSourcePolicy.directLiveUrl("/dev/dvb/adapter0/frontend0"))
        assertNull(PlaybackSourcePolicy.directLiveUrl(""))
        assertNull(PlaybackSourcePolicy.directLiveUrl(null as String?))
    }

    @Test
    fun `transcoding is off unless allowed`() {
        assertFalse(PlaybackSourcePolicy.allowsTranscoding(false, false))
        assertTrue(PlaybackSourcePolicy.allowsTranscoding(false, true))
    }

    @Test
    fun `play with transcoding wins for one title`() {
        assertTrue(PlaybackSourcePolicy.allowsTranscoding(true, false))
    }
}
