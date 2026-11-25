package com.landxcape.synkromesh.data.repository

import com.landxcape.synkromesh.data.datasource.audio.AudioCaptureDriver
import com.landxcape.synkromesh.data.datasource.audio.AudioPlaybackDriver
import com.landxcape.synkromesh.data.datasource.audio.OpusCodecEngine
import com.landxcape.synkromesh.data.datasource.local.PreferencesDataSource
import com.landxcape.synkromesh.domain.model.AudioLevel
import com.landxcape.synkromesh.domain.model.AudioPacket
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import com.landxcape.synkromesh.domain.repository.IntercomRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AudioEngineRepositoryImpl @Inject constructor(
    private val captureDriver: AudioCaptureDriver,
    private val playbackDriver: AudioPlaybackDriver,
    private val codecEngine: OpusCodecEngine,
    private val preferencesDataSource: PreferencesDataSource,
    private val intercomRepositoryProvider: Provider<IntercomRepository>
) : AudioEngineRepository {

    companion object {
        private const val VAD_HANGOVER_MS = 300L
        private const val MAX_PEER_JITTER_FRAMES = 6
        private const val LOCAL_LOOPBACK_KEY = "__local_loopback__"
    }

    private val engineScope = CoroutineScope(Dispatchers.IO)
    private var processingJob: Job? = null
    private var gatingJob: Job? = null
    private var configJob: Job? = null
    private var mixerJob: Job? = null
    private val sequenceCounter = AtomicLong(0)
    private val lastProcessedSeq = ConcurrentHashMap<String, Long>()
    private val peerPcmQueues = ConcurrentHashMap<String, ArrayDeque<ShortArray>>()
    private val canTransmit = AtomicBoolean(false)

    @Volatile
    private var vadThreshold: Float = 0.05f
    @Volatile
    private var activeMode: IntercomMode = IntercomMode.PUSH_TO_TALK
    @Volatile
    private var lastVoiceActivityMs: Long = 0L
    @Volatile
    private var mutedPeerIds: Set<String> = emptySet()

    private val _isLoopbackActive = MutableStateFlow(false)
    private val _isEngineActive = MutableStateFlow(false)
    private val _outgoingPackets = MutableSharedFlow<AudioPacket>(extraBufferCapacity = 16)

    private var localDeviceIdCache: String = ""

    override fun observeMicAudioLevel(): Flow<AudioLevel> = captureDriver.audioLevelFlow
    override fun observeIsLoopbackActive(): Flow<Boolean> = _isLoopbackActive.asStateFlow()
    override fun observeIsAudioEngineActive(): Flow<Boolean> = _isEngineActive.asStateFlow()
    override fun observeOutgoingPackets(): Flow<AudioPacket> = _outgoingPackets.asSharedFlow()
    override fun observeIsSpeakerphoneOn(): Flow<Boolean> = playbackDriver.isSpeakerphoneOn

    override fun setMutedPeerIds(peerIds: Set<String>) {
        mutedPeerIds = peerIds
    }

    override suspend fun startAudioEngine(): Result<Unit> = runCatching {
        if (_isEngineActive.value) return@runCatching

        localDeviceIdCache = preferencesDataSource.getDeviceId()
        val loudspeaker = preferencesDataSource.loudspeakerFlow.first()
        playbackDriver.setSpeakerphone(loudspeaker)

        playbackDriver.startPlayback().getOrThrow()
        captureDriver.startCapture().getOrThrow()
        _isEngineActive.value = true

        val intercomRepo = intercomRepositoryProvider.get()
        gatingJob?.cancel()
        gatingJob = engineScope.launch {
            combine(
                intercomRepo.observeTransmissionState(),
                intercomRepo.observeIsMuted(),
                intercomRepo.observeIsSessionActive(),
                intercomRepo.observeIntercomMode()
            ) { txState, isMuted, isSessionActive, mode ->
                activeMode = mode
                isSessionActive && txState == TransmissionState.TRANSMITTING && !isMuted
            }.collect { allowed ->
                canTransmit.set(allowed)
            }
        }

        configJob?.cancel()
        configJob = engineScope.launch {
            launch {
                preferencesDataSource.vadThresholdFlow.collect { threshold ->
                    vadThreshold = threshold
                }
            }
            launch {
                preferencesDataSource.opusBitrateFlow.collect { bitrateKbps ->
                    codecEngine.setBitrate(bitrateKbps * 1000)
                }
            }
        }

        mixerJob?.cancel()
        mixerJob = engineScope.launch {
            val mixAccumulator = IntArray(OpusCodecEngine.FRAME_SIZE_SAMPLES)
            val mixedFrame = ShortArray(OpusCodecEngine.FRAME_SIZE_SAMPLES)
            val activeFrames = ArrayList<ShortArray>(4)

            while (isActive && _isEngineActive.value) {
                activeFrames.clear()
                for ((_, queue) in peerPcmQueues) {
                    val frame = synchronized(queue) {
                        if (queue.isNotEmpty()) queue.removeFirst() else null
                    }
                    if (frame != null) {
                        activeFrames.add(frame)
                    }
                }

                when (activeFrames.size) {
                    0 -> Unit
                    1 -> playbackDriver.writePcm(activeFrames[0])
                    else -> {
                        mixAccumulator.fill(0)
                        for (frame in activeFrames) {
                            val len = minOf(frame.size, mixAccumulator.size)
                            for (i in 0 until len) {
                                mixAccumulator[i] += frame[i].toInt()
                            }
                        }
                        for (i in mixedFrame.indices) {
                            mixedFrame[i] = mixAccumulator[i]
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        }
                        playbackDriver.writePcm(mixedFrame)
                    }
                }
                delay(OpusCodecEngine.FRAME_SIZE_MS.toLong())
            }
        }

        processingJob = engineScope.launch {
            captureDriver.pcmFrameFlow.collect { pcmSamples ->
                // Hardware transmission gate: drop outgoing packets if mic is muted or not transmitting
                if (!canTransmit.get()) {
                    return@collect
                }

                // VAD noise gate in ALWAYS_ON mode with 300ms hangover
                if (activeMode == IntercomMode.ALWAYS_ON) {
                    val now = System.currentTimeMillis()
                    val level = captureDriver.audioLevelFlow.value.normalizedLevel
                    if (level >= vadThreshold) {
                        lastVoiceActivityMs = now
                    } else if (now - lastVoiceActivityMs > VAD_HANGOVER_MS) {
                        return@collect
                    }
                }

                // 1. Encode 20ms PCM frame to Opus packet
                val opusBytes = codecEngine.encode(pcmSamples) ?: return@collect

                val packet = AudioPacket(
                    sequenceNumber = sequenceCounter.incrementAndGet(),
                    senderId = localDeviceIdCache,
                    timestampMs = System.currentTimeMillis(),
                    payload = opusBytes
                )

                _outgoingPackets.tryEmit(packet)

                // 2. Loopback: ONLY if explicitly requested via self-test button
                if (_isLoopbackActive.value) {
                    val decodedSamples = codecEngine.decode(LOCAL_LOOPBACK_KEY, opusBytes)
                    if (decodedSamples != null) {
                        enqueuePeerFrame(LOCAL_LOOPBACK_KEY, decodedSamples)
                    }
                }
            }
        }
    }

    private fun enqueuePeerFrame(senderId: String, samples: ShortArray) {
        val queue = peerPcmQueues.computeIfAbsent(senderId) { ArrayDeque() }
        synchronized(queue) {
            while (queue.size >= MAX_PEER_JITTER_FRAMES) {
                queue.removeFirst()
            }
            queue.addLast(samples)
        }
    }

    override suspend fun stopAudioEngine(): Result<Unit> = runCatching {
        _isEngineActive.value = false
        canTransmit.set(false)
        gatingJob?.cancel()
        gatingJob = null
        configJob?.cancel()
        configJob = null
        mixerJob?.cancel()
        mixerJob = null
        processingJob?.cancel()
        processingJob = null
        lastProcessedSeq.clear()
        peerPcmQueues.clear()
        codecEngine.clearDecoders()
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

    override suspend fun setSpeakerphone(enabled: Boolean): Result<Unit> = runCatching {
        playbackDriver.setSpeakerphone(enabled)
        preferencesDataSource.saveLoudspeaker(enabled)
    }

    override suspend fun toggleSpeakerphone(): Result<Boolean> = runCatching {
        val next = !playbackDriver.isSpeakerphoneOn.value
        setSpeakerphone(next).getOrThrow()
        next
    }

    override suspend fun playIncomingPacket(packet: AudioPacket): Result<Unit> = runCatching {
        // PREVENT SELF-ECHO: Drop any packet originated from local device
        if (packet.senderId == localDeviceIdCache && localDeviceIdCache.isNotBlank()) {
            return@runCatching
        }

        // PER-PEER MUTE: Drop audio from locally muted riders
        if (mutedPeerIds.contains(packet.senderId)) {
            return@runCatching
        }

        // PREVENT DUAL-TRANSPORT DUPLICATION:
        // Drop duplicate sequence numbers from the same sender (handles UDP + Nearby dual-reception)
        val lastSeq = lastProcessedSeq[packet.senderId] ?: -1L
        if (packet.sequenceNumber <= lastSeq && lastSeq - packet.sequenceNumber < 100) {
            return@runCatching
        }
        lastProcessedSeq[packet.senderId] = packet.sequenceNumber

        val decodedSamples = codecEngine.decode(packet.senderId, packet.payload)
        if (decodedSamples != null) {
            enqueuePeerFrame(packet.senderId, decodedSamples)
        }
    }
}
