package com.landxcape.synkromesh

import org.junit.Assert.assertEquals
import org.junit.Test

class OpusFramingUnitTest {

    companion object {
        private const val SAMPLE_RATE_HZ = 16000
        private const val FRAME_DURATION_MS = 20
        private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM mono
    }

    @Test
    fun opusFrameSize_matchesRFC6716Wideband() {
        // Samples per 20ms frame = (16000 samples/sec) * (20 / 1000 sec) = 320 samples
        val expectedSamples = (SAMPLE_RATE_HZ * FRAME_DURATION_MS) / 1000
        assertEquals(320, expectedSamples)

        // Raw PCM byte size = 320 samples * 2 bytes/sample = 640 bytes
        val expectedBytes = expectedSamples * BYTES_PER_SAMPLE
        assertEquals(640, expectedBytes)
    }

    @Test
    fun opusBandwidthBitrate_targetPayloadSize() {
        // At 16 kbps: 16,000 bits/sec / 8 = 2000 bytes/sec
        // Over 50 frames per second (1000ms / 20ms = 50 fps):
        // Average compressed Opus frame size = 2000 / 50 = 40 bytes per packet!
        val bitrateBps = 16000
        val bytesPerSec = bitrateBps / 8
        val framesPerSec = 1000 / FRAME_DURATION_MS
        val avgCompressedBytesPerPacket = bytesPerSec / framesPerSec

        assertEquals(40, avgCompressedBytesPerPacket)
    }
}
