#include <jni.h>
#include <android/log.h>
#include <opus.h>
#include <cstdlib>

#define LOG_TAG "SynkromeshAudioJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_landxcape_synkromesh_data_datasource_audio_nativebridge_NativeOpus_nativeCreateEncoder(
        JNIEnv *env,
        jobject /* this */,
        jint sample_rate,
        jint channels,
        jint bitrate,
        jint application) {
    int error = 0;
    OpusEncoder *enc = opus_encoder_create(sample_rate, channels, application, &error);
    if (error != OPUS_OK || enc == nullptr) {
        LOGE("Failed to create Opus encoder: %d", error);
        return 0;
    }

    opus_encoder_ctl(enc, OPUS_SET_BITRATE(bitrate));
    opus_encoder_ctl(enc, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
    opus_encoder_ctl(enc, OPUS_SET_INBAND_FEC(1));
    opus_encoder_ctl(enc, OPUS_SET_PACKET_LOSS_PERC(15)); // Resilient for motorcycle mesh
    opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(10));       // Maximum acoustic fidelity
    opus_encoder_ctl(enc, OPUS_SET_VBR(1));               // Adaptive bit allocation for clear consonants
    opus_encoder_ctl(enc, OPUS_SET_VBR_CONSTRAINT(1));    // Constrained VBR for predictable packet sizes

    return reinterpret_cast<jlong>(enc);
}

JNIEXPORT jint JNICALL
Java_com_landxcape_synkromesh_data_datasource_audio_nativebridge_NativeOpus_nativeEncode(
        JNIEnv *env,
        jobject /* this */,
        jlong encoder_ptr,
        jshortArray pcm_in,
        jint pcm_offset,
        jint frame_size,
        jbyteArray opus_out,
        jint opus_offset,
        jint max_bytes) {
    auto *enc = reinterpret_cast<OpusEncoder *>(encoder_ptr);
    if (enc == nullptr) return -1;

    jshort *pcm_elements = env->GetShortArrayElements(pcm_in, nullptr);
    jbyte *opus_elements = env->GetByteArrayElements(opus_out, nullptr);

    if (pcm_elements == nullptr || opus_elements == nullptr) {
        if (pcm_elements) env->ReleaseShortArrayElements(pcm_in, pcm_elements, JNI_ABORT);
        if (opus_elements) env->ReleaseByteArrayElements(opus_out, opus_elements, JNI_ABORT);
        return -2;
    }

    const opus_int16 *pcm_ptr = pcm_elements + pcm_offset;
    unsigned char *out_ptr = reinterpret_cast<unsigned char *>(opus_elements + opus_offset);

    int encoded_bytes = opus_encode(enc, pcm_ptr, frame_size, out_ptr, max_bytes);

    env->ReleaseShortArrayElements(pcm_in, pcm_elements, JNI_ABORT);
    env->ReleaseByteArrayElements(opus_out, opus_elements, 0); // Copy back output

    return encoded_bytes;
}

JNIEXPORT void JNICALL
Java_com_landxcape_synkromesh_data_datasource_audio_nativebridge_NativeOpus_nativeDestroyEncoder(
        JNIEnv *env,
        jobject /* this */,
        jlong encoder_ptr) {
    auto *enc = reinterpret_cast<OpusEncoder *>(encoder_ptr);
    if (enc != nullptr) {
        opus_encoder_destroy(enc);
    }
}

JNIEXPORT jlong JNICALL
Java_com_landxcape_synkromesh_data_datasource_audio_nativebridge_NativeOpus_nativeCreateDecoder(
        JNIEnv *env,
        jobject /* this */,
        jint sample_rate,
        jint channels) {
    int error = 0;
    OpusDecoder *dec = opus_decoder_create(sample_rate, channels, &error);
    if (error != OPUS_OK || dec == nullptr) {
        LOGE("Failed to create Opus decoder: %d", error);
        return 0;
    }
    return reinterpret_cast<jlong>(dec);
}

JNIEXPORT jint JNICALL
Java_com_landxcape_synkromesh_data_datasource_audio_nativebridge_NativeOpus_nativeDecode(
        JNIEnv *env,
        jobject /* this */,
        jlong decoder_ptr,
        jbyteArray opus_in,
        jint opus_offset,
        jint opus_len,
        jshortArray pcm_out,
        jint pcm_offset,
        jint frame_size,
        jint decode_fec) {
    auto *dec = reinterpret_cast<OpusDecoder *>(decoder_ptr);
    if (dec == nullptr) return -1;

    jshort *pcm_elements = env->GetShortArrayElements(pcm_out, nullptr);
    if (pcm_elements == nullptr) return -2;

    opus_int16 *pcm_ptr = pcm_elements + pcm_offset;
    int decoded_samples = 0;

    if (opus_in == nullptr || opus_len <= 0) {
        // Packet Loss Concealment (PLC) mode: interpolate lost frame
        decoded_samples = opus_decode(dec, nullptr, 0, pcm_ptr, frame_size, decode_fec);
    } else {
        jbyte *opus_elements = env->GetByteArrayElements(opus_in, nullptr);
        if (opus_elements == nullptr) {
            env->ReleaseShortArrayElements(pcm_out, pcm_elements, JNI_ABORT);
            return -3;
        }
        const unsigned char *in_ptr = reinterpret_cast<const unsigned char *>(opus_elements + opus_offset);
        decoded_samples = opus_decode(dec, in_ptr, opus_len, pcm_ptr, frame_size, decode_fec);
        env->ReleaseByteArrayElements(opus_in, opus_elements, JNI_ABORT);
    }

    env->ReleaseShortArrayElements(pcm_out, pcm_elements, 0); // Copy back PCM samples
    return decoded_samples;
}

JNIEXPORT void JNICALL
Java_com_landxcape_synkromesh_data_datasource_audio_nativebridge_NativeOpus_nativeDestroyDecoder(
        JNIEnv *env,
        jobject /* this */,
        jlong decoder_ptr) {
    auto *dec = reinterpret_cast<OpusDecoder *>(decoder_ptr);
    if (dec != nullptr) {
        opus_decoder_destroy(dec);
    }
}

} // extern "C"
