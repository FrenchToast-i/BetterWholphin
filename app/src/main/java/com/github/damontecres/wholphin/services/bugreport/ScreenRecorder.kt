package com.github.damontecres.wholphin.services.bugreport

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import timber.log.Timber
import java.io.File
import kotlin.concurrent.thread

/**
 * Records the screen into an MP4 for a bug report.
 *
 * A report is only useful if the viewer can say "it did this" while it is doing it, so the
 * recorder has to start from a single press and keep running with the app in the foreground.
 * MediaProjection supplies the frames, MediaCodec encodes them to H.264 and MediaMuxer writes
 * the file; there is no audio track because the household tunes with a remote, not a microphone.
 */
class ScreenRecorder(
    private val projection: MediaProjection,
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    private val densityDpi: Int,
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var drainThread: Thread? = null

    @Volatile
    private var running = false

    private var trackIndex = -1
    private var muxerStarted = false

    fun start() {
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                // 720p at 4 Mbps is legible for UI text at ten feet without making a report
                // too large to upload over a domestic connection.
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()
        codec = encoder

        val mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = mediaMuxer

        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    // The system can end the projection (permission revoked, another capture
                    // started). The encoder has to be told, or the file is left unplayable.
                    running = false
                }
            },
            null,
        )
        virtualDisplay =
            projection.createVirtualDisplay(
                "wholphin-bug-report",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null,
            )
        running = true
        drainThread =
            thread(name = "bug-report-encoder", isDaemon = true) {
                drain(encoder, mediaMuxer)
            }
        Timber.i("Bug report recording started: %s (%dx%d)", outputFile.name, width, height)
    }

    private fun drain(
        encoder: MediaCodec,
        mediaMuxer: MediaMuxer,
    ) {
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = mediaMuxer.addTrack(encoder.outputFormat)
                        mediaMuxer.start()
                        muxerStarted = true
                    }

                    index >= 0 -> {
                        val buffer = encoder.getOutputBuffer(index)
                        if (buffer != null) {
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                info.size = 0
                            }
                            if (info.size > 0 && muxerStarted) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                mediaMuxer.writeSampleData(trackIndex, buffer, info)
                            }
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex, "Error while encoding bug report")
        }
    }

    fun stop() {
        running = false
        runCatching { codec?.signalEndOfInputStream() }
        runCatching { drainThread?.join(3_000) }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching {
            codec?.stop()
            codec?.release()
        }
        codec = null
        runCatching {
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        }
        muxer = null
        runCatching { projection.stop() }
        if (!muxerStarted || outputFile.length() == 0L) {
            // Nothing was ever encoded; a zero-byte MP4 is worse than no file at all.
            outputFile.delete()
        }
        Timber.i("Bug report recording stopped: %s (%d bytes)", outputFile.name, outputFile.length())
    }
}
