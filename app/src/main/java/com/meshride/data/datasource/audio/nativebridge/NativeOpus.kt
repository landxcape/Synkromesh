package com.meshride.data.datasource.audio.nativebridge

object NativeOpus {

    init {
        System.loadLibrary("meshride_audio")
    }

    const val APPLICATION_VOIP = 2048
    const val APPLICATION_AUDIO = 2049
    const val APPLICATION_RESTRICTED_LOWDELAY = 2051

    external fun nativeCreateEncoder(
        sampleRate: Int,
        channels: Int,
        bitrate: Int,
        application: Int
    ): Long

    external fun nativeEncode(
        encoderPtr: Long,
        pcmIn: ShortArray,
        pcmOffset: Int,
        frameSize: Int,
        opusOut: ByteArray,
        opusOffset: Int,
        maxBytes: Int
    ): Int

    external fun nativeDestroyEncoder(encoderPtr: Long)

    external fun nativeCreateDecoder(
        sampleRate: Int,
        channels: Int
    ): Long

    external fun nativeDecode(
        decoderPtr: Long,
        opusIn: ByteArray?,
        opusOffset: Int,
        opusLen: Int,
        pcmOut: ShortArray,
        pcmOffset: Int,
        frameSize: Int,
        decodeFec: Int
    ): Int

    external fun nativeDestroyDecoder(decoderPtr: Long)
}
