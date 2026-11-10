package com.meshride.data.datasource.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.meshride.domain.model.AudioLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Singleton
class AudioCaptureDriver @Inject constructor() {

    private val captureScope = CoroutineScope(Dispatchers.IO)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _pcmFrameFlow = MutableSharedFlow<ShortArray>(extraBufferCapacity = 8)
    val pcmFrameFlow: SharedFlow<ShortArray> = _pcmFrameFlow.asSharedFlow()

    private val _audioLevelFlow = MutableStateFlow(AudioLevel())
    val audioLevelFlow: StateFlow<AudioLevel> = _audioLevelFlow.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startCapture(): Result<Unit> = runCatching {
        if (_isCapturing.value) return@runCatching

        val minBufferSize = AudioRecord.getMinBufferSize(
            OpusCodecEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, OpusCodecEngine.FRAME_SIZE_SAMPLES * 2 * 4)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            OpusCodecEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioRecord failed to initialize")
        }

        record.startRecording()
        audioRecord = record
        _isCapturing.value = true

        captureJob = captureScope.launch {
            val sampleBuffer = ShortArray(OpusCodecEngine.FRAME_SIZE_SAMPLES)
            while (isActive && _isCapturing.value) {
                var samplesRead = 0
                while (samplesRead < OpusCodecEngine.FRAME_SIZE_SAMPLES && isActive) {
                    val read = record.read(
                        sampleBuffer,
                        samplesRead,
                        OpusCodecEngine.FRAME_SIZE_SAMPLES - samplesRead
                    )
                    if (read > 0) {
                        samplesRead += read
                    } else {
                        break
                    }
                }

                if (samplesRead == OpusCodecEngine.FRAME_SIZE_SAMPLES) {
                    val frameCopy = sampleBuffer.copyOf()
                    _pcmFrameFlow.tryEmit(frameCopy)

                    // Calculate RMS and normalized vocal level
                    val level = computeAudioLevel(frameCopy)
                    _audioLevelFlow.value = level
                }
            }
        }
    }

    fun stopCapture(): Result<Unit> = runCatching {
        _isCapturing.value = false
        captureJob?.cancel()
        captureJob = null
        audioRecord?.run {
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
            }
            release()
        }
        audioRecord = null
        _audioLevelFlow.value = AudioLevel()
    }

    private fun computeAudioLevel(samples: ShortArray): AudioLevel {
        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += (sample * sample).toDouble()
        }
        val meanSquare = sumSquares / samples.size
        val rms = sqrt(meanSquare)
        if (rms <= 1.0) {
            return AudioLevel(rmsDb = -96f, normalizedLevel = 0.0f)
        }

        val db = (20.0 * log10(rms / 32767.0)).toFloat()
        // Map speech dynamic range [-55 dB (whisper) to -10 dB (loud)] into [0.0, 1.0]
        val normalized = min(1.0f, max(0.0f, (db + 55f) / 45f))
        return AudioLevel(rmsDb = db, normalizedLevel = normalized)
    }
}
