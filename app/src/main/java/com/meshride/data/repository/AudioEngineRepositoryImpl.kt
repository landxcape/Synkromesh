package com.meshride.data.repository

import com.meshride.data.datasource.audio.AudioCaptureDriver
import com.meshride.data.datasource.audio.AudioPlaybackDriver
import com.meshride.data.datasource.audio.OpusCodecEngine
import com.meshride.domain.model.AudioLevel
import com.meshride.domain.model.AudioPacket
import com.meshride.domain.repository.AudioEngineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngineRepositoryImpl @Inject constructor(
    private val captureDriver: AudioCaptureDriver,
    private val playbackDriver: AudioPlaybackDriver,
    private val codecEngine: OpusCodecEngine
) : AudioEngineRepository {

    private val engineScope = CoroutineScope(Dispatchers.IO)
    private var processingJob: Job? = null
    private val sequenceCounter = AtomicLong(0)

    private val _isLoopbackActive = MutableStateFlow(false)
    private val _isEngineActive = MutableStateFlow(false)
    private val _outgoingPackets = MutableSharedFlow<AudioPacket>(extraBufferCapacity = 16)

    override fun observeMicAudioLevel(): Flow<AudioLevel> = captureDriver.audioLevelFlow
    override fun observeIsLoopbackActive(): Flow<Boolean> = _isLoopbackActive.asStateFlow()
    override fun observeIsAudioEngineActive(): Flow<Boolean> = _isEngineActive.asStateFlow()
    override fun observeOutgoingPackets(): Flow<AudioPacket> = _outgoingPackets.asSharedFlow()

    override suspend fun startAudioEngine(): Result<Unit> = runCatching {
        if (_isEngineActive.value) return@runCatching

        playbackDriver.startPlayback().getOrThrow()
        captureDriver.startCapture().getOrThrow()
        _isEngineActive.value = true

        processingJob = engineScope.launch {
            captureDriver.pcmFrameFlow.collect { pcmSamples ->
                // 1. Encode 20ms PCM frame to Opus packet
                val opusBytes = codecEngine.encode(pcmSamples) ?: return@collect

                val packet = AudioPacket(
                    sequenceNumber = sequenceCounter.incrementAndGet(),
                    senderId = "local_rider",
                    timestampMs = System.currentTimeMillis(),
                    payload = opusBytes
                )

                _outgoingPackets.tryEmit(packet)

                // 2. Loopback: If enabled, immediately decode and playback through helmet speaker
                if (_isLoopbackActive.value) {
                    val decodedSamples = codecEngine.decode(opusBytes)
                    if (decodedSamples != null) {
                        playbackDriver.writePcm(decodedSamples)
                    }
                }
            }
        }
    }

    override suspend fun stopAudioEngine(): Result<Unit> = runCatching {
        _isEngineActive.value = false
        processingJob?.cancel()
        processingJob = null
        captureDriver.stopCapture().getOrThrow()
        playbackDriver.stopPlayback().getOrThrow()
    }

    override suspend fun setLoopbackMode(enabled: Boolean): Result<Unit> = runCatching {
        _isLoopbackActive.value = enabled
        if (enabled && !_isEngineActive.value) {
            startAudioEngine().getOrThrow()
        }
    }

    override suspend fun toggleLoopbackMode(): Result<Boolean> = runCatching {
        val next = !_isLoopbackActive.value
        setLoopbackMode(next).getOrThrow()
        next
    }

    override suspend fun playIncomingPacket(packet: AudioPacket): Result<Unit> = runCatching {
        val decodedSamples = codecEngine.decode(packet.payload)
        if (decodedSamples != null) {
            playbackDriver.writePcm(decodedSamples)
        }
    }
}
