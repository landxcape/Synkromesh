package com.landxcape.synkromesh.data.datasource.audio

import com.landxcape.synkromesh.data.datasource.audio.nativebridge.NativeOpus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpusCodecEngine @Inject constructor() {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val FRAME_SIZE_MS = 20
        const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_SIZE_MS) / 1000 // 320 samples
        const val BITRATE_BPS = 24000 // 24 kbps wideband voice
        const val MAX_OPUS_PAYLOAD_BYTES = 500
    }

    private var encoderPtr: Long = 0L
    private var decoderPtr: Long = 0L
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
        synchronized(decodeLock) {
            if (decoderPtr == 0L) {
                decoderPtr = NativeOpus.nativeCreateDecoder(SAMPLE_RATE, CHANNELS)
            }
        }
    }

    /**
     * Compresses a 20ms PCM frame (320 samples) into an Opus packet (~40-60 bytes).
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
     * Decompresses an Opus packet back into 320 PCM samples (20ms wideband audio).
     * If opusPacket is null or empty, activates Packet Loss Concealment (PLC).
     */
    fun decode(opusPacket: ByteArray?, useFec: Boolean = true): ShortArray? = synchronized(decodeLock) {
        if (decoderPtr == 0L) return null
        val outPcm = ShortArray(FRAME_SIZE_SAMPLES)
        val decodedSamples = NativeOpus.nativeDecode(
            decoderPtr = decoderPtr,
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

    fun release() {
        synchronized(encodeLock) {
            if (encoderPtr != 0L) {
                NativeOpus.nativeDestroyEncoder(encoderPtr)
                encoderPtr = 0L
            }
        }
        synchronized(decodeLock) {
            if (decoderPtr != 0L) {
                NativeOpus.nativeDestroyDecoder(decoderPtr)
                decoderPtr = 0L
            }
        }
    }
}
