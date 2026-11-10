package com.landxcape.synkromesh

import org.junit.Assert.assertEquals
import org.junit.Test

class OpusFramingUnitTest {

    companion object {
        private const val SAMPLE_RATE_HZ = 48000
        private const val FRAME_DURATION_MS = 20
        private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM mono
    }

    @Test
    fun opusFrameSize_matchesRFC6716Fullband() {
        // Samples per 20ms frame = (48000 samples/sec) * (20 / 1000 sec) = 960 samples
        val expectedSamples = (SAMPLE_RATE_HZ * FRAME_DURATION_MS) / 1000
        assertEquals(960, expectedSamples)

        // Raw PCM byte size = 960 samples * 2 bytes/sample = 1920 bytes
        val expectedBytes = expectedSamples * BYTES_PER_SAMPLE
        assertEquals(1920, expectedBytes)
    }

    @Test
    fun opusBandwidthBitrate_targetPayloadSize() {
        // At 24 kbps: 24,000 bits/sec / 8 = 3000 bytes/sec
        // Over 50 frames per second (1000ms / 20ms = 50 fps):
        // Average compressed Opus frame size = 3000 / 50 = 60 bytes per packet!
        val bitrateBps = 24000
        val bytesPerSec = bitrateBps / 8
        val framesPerSec = 1000 / FRAME_DURATION_MS
        val avgCompressedBytesPerPacket = bytesPerSec / framesPerSec

        assertEquals(60, avgCompressedBytesPerPacket)
    }
}
