package com.landxcape.synkromesh.data.datasource.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AudioPlaybackDriver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speedAdaptiveGainController: SpeedAdaptiveGainController
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(true)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck volume to 25% while GPS navigation speaks (Google Maps / Waze)
                audioTrack?.setVolume(0.25f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Navigation prompt complete -> restore 100% volume
                audioTrack?.setVolume(1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                audioTrack?.setVolume(0.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                audioTrack?.setVolume(0.0f)
            }
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        _isSpeakerphoneOn.value = enabled
        applySpeakerphoneRouting(enabled)
    }

    private fun applySpeakerphoneRouting(enabled: Boolean) {
        if (_isPlaying.value) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                val devices = audioManager.availableCommunicationDevices
                val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }

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

        // Set communication mode and apply speakerphone routing
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        applySpeakerphoneRouting(_isSpeakerphoneOn.value)

        // Request Audio Focus with ducking permission for motorcycle navigation audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager.requestAudioFocus(request)
            audioFocusRequest = request
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

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
        speedAdaptiveGainController.start()
    }

    private var pcmGainBuffer = ShortArray(1920)

    fun writePcm(samples: ShortArray): Int {
        val track = audioTrack ?: return 0
        if (!_isPlaying.value) return 0

        val gain = speedAdaptiveGainController.currentGain
        val samplesToWrite = if (gain > 1.01f) {
            if (pcmGainBuffer.size < samples.size) {
                pcmGainBuffer = ShortArray(samples.size)
            }
            for (i in samples.indices) {
                val amplified = (samples[i] * gain).toInt()
                pcmGainBuffer[i] = amplified.coerceIn(-32768, 32767).toShort()
            }
            pcmGainBuffer
        } else {
            samples
        }
        return track.write(samplesToWrite, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    fun stopPlayback(): Result<Unit> = runCatching {
        _isPlaying.value = false
        speedAdaptiveGainController.stop()
        audioTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
        audioTrack = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        audioFocusRequest = null
    }
}
