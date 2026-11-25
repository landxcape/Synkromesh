package com.landxcape.synkromesh.data.datasource.audio

import com.landxcape.synkromesh.data.datasource.audio.nativebridge.NativeOpus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpusCodecEngine @Inject constructor() {

    companion object {
        const val SAMPLE_RATE = 48000 // 48 kHz Fullband broadcast audio
        const val CHANNELS = 1
        const val FRAME_SIZE_MS = 20
        const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000 // 960 samples
        const val BITRATE_BPS = 24000 // 24 kbps wideband/fullband voice
        const val MAX_OPUS_PAYLOAD_BYTES = 500
        private const val LOCAL_DECODER_ID = "__local_loopback__"
    }

    private var encoderPtr: Long = 0L
    private val peerDecoders = ConcurrentHashMap<String, Long>()
    private val encodeLock = Any()
    private val decodeLock = Any()

    init {
        initCodec()
    }

    private fun initCodec() {
        synchronized(encodeLock) {
            if (encoderPtr == 0L) {
                encoderPtr = NativeOpus.nativeCreateEncoder(
                    SAMPLE_RATE,
                    CHANNELS,
                    BITRATE_BPS,
                    NativeOpus.APPLICATION_VOIP
                )
            }
        }
    }

    fun setBitrate(bitrateBps: Int) {
        synchronized(encodeLock) {
            if (encoderPtr != 0L) {
                NativeOpus.nativeSetBitrate(encoderPtr, bitrateBps.coerceIn(6000, 64000))
            }
        }
    }

    /**
     * Compresses a 20ms PCM frame into an Opus packet.
     * Returns the encoded byte array, or null if encoding failed.
     */
    fun encode(pcmSamples: ShortArray): ByteArray? = synchronized(encodeLock) {
        if (encoderPtr == 0L) return null
        val outBuffer = ByteArray(MAX_OPUS_PAYLOAD_BYTES)
        val encodedBytes = NativeOpus.nativeEncode(
            encoderPtr = encoderPtr,
            pcmIn = pcmSamples,
            pcmOffset = 0,
            frameSize = FRAME_SIZE_SAMPLES,
            opusOut = outBuffer,
            opusOffset = 0,
            maxBytes = MAX_OPUS_PAYLOAD_BYTES
        )
        return if (encodedBytes > 0) {
            outBuffer.copyOf(encodedBytes)
        } else {
            null
        }
    }

    /**
     * Decompresses an Opus packet using an isolated decoder instance per [senderId].
     * If opusPacket is null or empty, activates Packet Loss Concealment (PLC).
     */
    fun decode(senderId: String, opusPacket: ByteArray?, useFec: Boolean = true): ShortArray? = synchronized(decodeLock) {
        val ptr = peerDecoders.computeIfAbsent(senderId) {
            NativeOpus.nativeCreateDecoder(SAMPLE_RATE, CHANNELS)
        }
        if (ptr == 0L) return null
        val outPcm = ShortArray(FRAME_SIZE_SAMPLES)
        val decodedSamples = NativeOpus.nativeDecode(
            decoderPtr = ptr,
            opusIn = opusPacket,
            opusOffset = 0,
            opusLen = opusPacket?.size ?: 0,
            pcmOut = outPcm,
            pcmOffset = 0,
            frameSize = FRAME_SIZE_SAMPLES,
            decodeFec = if (useFec) 1 else 0
        )
        return if (decodedSamples > 0) outPcm else null
    }

    fun decode(opusPacket: ByteArray?, useFec: Boolean = true): ShortArray? {
        return decode(LOCAL_DECODER_ID, opusPacket, useFec)
    }

    fun clearDecoders() {
        synchronized(decodeLock) {
            peerDecoders.values.forEach { ptr ->
                if (ptr != 0L) {
                    NativeOpus.nativeDestroyDecoder(ptr)
                }
            }
            peerDecoders.clear()
        }
    }

    fun release() {
        synchronized(encodeLock) {
            if (encoderPtr != 0L) {
                NativeOpus.nativeDestroyEncoder(encoderPtr)
                encoderPtr = 0L
            }
        }
        clearDecoders()
    }
}
