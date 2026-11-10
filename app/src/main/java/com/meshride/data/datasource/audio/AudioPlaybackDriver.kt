package com.meshride.data.datasource.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AudioPlaybackDriver @Inject constructor() {

    private var audioTrack: AudioTrack? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun startPlayback(): Result<Unit> = runCatching {
        if (_isPlaying.value) return@runCatching

        val minBufferSize = AudioTrack.getMinBufferSize(
            OpusCodecEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, OpusCodecEngine.FRAME_SIZE_SAMPLES * 2 * 4)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(OpusCodecEngine.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        track.play()
        audioTrack = track
        _isPlaying.value = true
    }

    fun writePcm(samples: ShortArray): Int {
        val track = audioTrack ?: return -1
        return track.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    fun stopPlayback(): Result<Unit> = runCatching {
        _isPlaying.value = false
        audioTrack?.run {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
            flush()
            release()
        }
        audioTrack = null
    }
}
