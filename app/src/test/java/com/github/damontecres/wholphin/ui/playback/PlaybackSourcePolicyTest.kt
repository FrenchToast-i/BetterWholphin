package com.github.damontecres.wholphin.ui.playback

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PlayMethod
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
    fun `a container-local provider link is opened through the server host`() {
        assertEquals(
            "http://192.168.1.88:8101/stream/nfl.m3u8",
            PlaybackSourcePolicy.resolveProviderUrl(
                "http://host.containers.internal:8101/stream/nfl.m3u8",
                "http://192.168.1.88:8096",
            ),
        )
        assertEquals(
            "http://192.168.1.88:8101/seg/nfl/segment.ts",
            PlaybackSourcePolicy.resolveProviderUrl(
                "http://host.docker.internal:8101/seg/nfl/segment.ts",
                "http://192.168.1.88:8096",
            ),
        )
    }

    @Test
    fun `a loopback provider link is opened through the server host`() {
        assertEquals(
            "http://jellyfin.local:9090/live/1.m3u8",
            PlaybackSourcePolicy.resolveProviderUrl(
                "http://localhost:9090/live/1.m3u8",
                "https://jellyfin.local:8920/web/index.html",
            ),
        )
        assertEquals(
            "http://jellyfin.local:9090/live/1.m3u8",
            PlaybackSourcePolicy.resolveProviderUrl(
                "http://127.0.0.1:9090/live/1.m3u8",
                "http://jellyfin.local:8096",
            ),
        )
    }

    @Test
    fun `a provider link the device can resolve is left alone`() {
        assertEquals(
            "http://provider.example/live/1.m3u8",
            PlaybackSourcePolicy.resolveProviderUrl(
                "http://provider.example/live/1.m3u8",
                "http://192.168.1.88:8096",
            ),
        )
        assertEquals(
            "http://192.168.1.88:8101/live/1.m3u8",
            PlaybackSourcePolicy.resolveProviderUrl(
                "http://192.168.1.88:8101/live/1.m3u8",
                "http://192.168.1.88:8096",
            ),
        )
    }

    @Test
    fun `a provider link is unchanged without a server address`() {
        assertEquals(
            "http://host.containers.internal:8101/live/1.m3u8",
            PlaybackSourcePolicy.resolveProviderUrl(
                "http://host.containers.internal:8101/live/1.m3u8",
                null,
            ),
        )
        assertNull(PlaybackSourcePolicy.resolveProviderUrl(null, "http://192.168.1.88:8096"))
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

    @Test
    fun `a channel tune does not ask for intros`() {
        assertTrue(PlaybackSourcePolicy.wantsIntros(BaseItemKind.MOVIE, 0L, cinemaMode = true))
        assertFalse(PlaybackSourcePolicy.wantsIntros(BaseItemKind.TV_CHANNEL, 0L, cinemaMode = true))
        assertFalse(PlaybackSourcePolicy.wantsIntros(BaseItemKind.MOVIE, 1_000L, cinemaMode = true))
        assertFalse(PlaybackSourcePolicy.wantsIntros(BaseItemKind.MOVIE, 0L, cinemaMode = false))
    }

    @Test
    fun `a channel still being encoded is retried`() {
        assertTrue(
            PlaybackSourcePolicy.retriesLiveStream(
                type = BaseItemKind.TV_CHANNEL,
                badHttpStatus = true,
                playMethod = PlayMethod.TRANSCODE,
                attempts = 0,
            ),
        )
        assertTrue(
            PlaybackSourcePolicy.retriesLiveStream(
                type = BaseItemKind.TV_CHANNEL,
                badHttpStatus = true,
                playMethod = PlayMethod.DIRECT_STREAM,
                attempts = PlaybackSourcePolicy.MAX_LIVE_RETRIES - 1,
            ),
        )
    }

    @Test
    fun `a direct live failure is not retried`() {
        assertFalse(
            PlaybackSourcePolicy.retriesLiveStream(
                type = BaseItemKind.TV_CHANNEL,
                badHttpStatus = true,
                playMethod = PlayMethod.DIRECT_PLAY,
                attempts = 0,
            ),
        )
        assertFalse(
            PlaybackSourcePolicy.retriesLiveStream(
                type = BaseItemKind.MOVIE,
                badHttpStatus = true,
                playMethod = PlayMethod.TRANSCODE,
                attempts = 0,
            ),
        )
        assertFalse(
            PlaybackSourcePolicy.retriesLiveStream(
                type = BaseItemKind.TV_CHANNEL,
                badHttpStatus = true,
                playMethod = PlayMethod.TRANSCODE,
                attempts = PlaybackSourcePolicy.MAX_LIVE_RETRIES,
            ),
        )
    }
}
