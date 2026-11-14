package com.landxcape.synkromesh.data.datasource.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands-free heads-up voice announcement engine that routes tactical intercom
 * events (peer arrivals, drops, relays, battery warnings) directly into the
 * motorcycle helmet headset via Bluetooth SCO / Voice Call stream.
 */
@Singleton
class TacticalVoiceCueEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val CUE_COOLDOWN_MS = 6000L // 6s anti-flood gate per event signature
    }

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false

    var isEnabled: Boolean = true

    private val lastCueTimes = ConcurrentHashMap<String, Long>()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Set speech rate slightly faster (1.1x) for snappy cockpit updates
                    engine.setSpeechRate(1.1f)
                    engine.setPitch(1.0f)
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(attributes)
                    isInitialized = true
                }
            }
        }
    }

    fun speak(cueKey: String, phrase: String) {
        if (!isEnabled || !isInitialized) return

        val now = System.currentTimeMillis()
        val lastTime = lastCueTimes[cueKey]
        if (lastTime != null && now - lastTime < CUE_COOLDOWN_MS) return

        lastCueTimes[cueKey] = now
        tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, cueKey)
    }

    fun announcePeerJoined(callsign: String, channel: Int) {
        speak("peer_join_$callsign", "$callsign joined channel 0$channel")
    }

    fun announcePeerLost(callsign: String) {
        speak("peer_lost_$callsign", "$callsign link lost")
    }

    fun announceRelayEngaged(callsign: String, hopCount: Int) {
        speak("relay_$callsign", "Relay active. $hopCount hops via $callsign")
    }

    fun announceBatteryCritical(callsign: String, percent: Int) {
        speak("battery_crit_$callsign", "Warning: $callsign battery $percent percent")
    }

    fun announceEcoPower(isEco: Boolean) {
        speak("power_mode", if (isEco) "Mesh idle. Eco power" else "Performance mesh active")
    }

    fun announceMeshDisconnected() {
        speak("mesh_disconnected", "Mesh connection lost. Reconnecting")
    }

    fun announceMeshReconnected() {
        speak("mesh_reconnected", "Mesh connection restored")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        lastCueTimes.clear()
    }
}
