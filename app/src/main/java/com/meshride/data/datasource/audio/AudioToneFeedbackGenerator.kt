package com.meshride.data.datasource.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

enum class FeedbackTone {
    MIC_OPEN,
    MIC_MUTED
}

@Singleton
class AudioToneFeedbackGenerator @Inject constructor(
    private val playbackDriver: AudioPlaybackDriver
) {

    companion object {
        private const val SAMPLE_RATE = OpusCodecEngine.SAMPLE_RATE // 16000 Hz
        private const val DURATION_MS = 50
        private const val SAMPLE_COUNT = (SAMPLE_RATE * DURATION_MS) / 1000 // 800 samples
    }

    private val micOpenTone: ShortArray = generateChirp(startFreq = 440f, endFreq = 880f)
    private val micMutedTone: ShortArray = generateChirp(startFreq = 880f, endFreq = 440f)

    fun playFeedbackTone(tone: FeedbackTone) {
        val samples = when (tone) {
            FeedbackTone.MIC_OPEN -> micOpenTone
            FeedbackTone.MIC_MUTED -> micMutedTone
        }
        playbackDriver.writePcm(samples)
    }

    private fun generateChirp(startFreq: Float, endFreq: Float): ShortArray {
        val samples = ShortArray(SAMPLE_COUNT)
        val durationSec = DURATION_MS / 1000.0

        for (i in 0 until SAMPLE_COUNT) {
            val t = i.toDouble() / SAMPLE_RATE
            // Instantaneous frequency linear chirp
            val freq = startFreq + (endFreq - startFreq) * (t / durationSec)
            val phase = 2.0 * PI * (startFreq * t + 0.5 * (endFreq - startFreq) * (t * t / durationSec))

            // Hanning window to prevent edge clicks
            val window = 0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / SAMPLE_COUNT))
            val amplitude = sin(phase) * window * 0.4 // 40% volume for comfortable in-ear level

            samples[i] = (amplitude * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }
}
