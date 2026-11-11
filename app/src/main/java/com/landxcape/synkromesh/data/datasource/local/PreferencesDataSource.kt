package com.landxcape.synkromesh.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.landxcape.synkromesh.domain.model.HelmetAcousticProfile
import com.landxcape.synkromesh.domain.model.IntercomMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "synkromesh_preferences")

@Singleton
class PreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyDeviceId = stringPreferencesKey("device_unique_id")
    private val keyCallsign = stringPreferencesKey("rider_callsign")
    private val keyDefaultMode = stringPreferencesKey("default_intercom_mode")
    private val keyRideChannel = intPreferencesKey("ride_channel")
    private val keyVadThreshold = floatPreferencesKey("vad_threshold")
    private val keyOpusBitrate = intPreferencesKey("opus_bitrate_kbps")
    private val keyAutoConnect = booleanPreferencesKey("auto_connect_mesh")
    private val keyLoudspeaker = booleanPreferencesKey("loudspeaker_enabled")
    private val keyHelmetProfile = stringPreferencesKey("helmet_acoustic_profile")

    val deviceIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyDeviceId] ?: run {
            val generated = UUID.randomUUID().toString().take(8)
            context.dataStore.edit { it[keyDeviceId] = generated }
            generated
        }
    }

    suspend fun getDeviceId(): String {
        return deviceIdFlow.first()
    }

    val callsignFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyCallsign] ?: "Rider 1"
    }

    val defaultModeFlow: Flow<IntercomMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[keyDefaultMode] ?: IntercomMode.PUSH_TO_TALK.name
        runCatching { IntercomMode.valueOf(raw) }.getOrDefault(IntercomMode.PUSH_TO_TALK)
    }

    val rideChannelFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[keyRideChannel] ?: 1
    }

    val vadThresholdFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[keyVadThreshold] ?: 0.05f
    }

    val opusBitrateFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[keyOpusBitrate] ?: 16
    }

    val autoConnectFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyAutoConnect] ?: true
    }

    val loudspeakerFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyLoudspeaker] ?: true
    }

    val helmetProfileFlow: Flow<HelmetAcousticProfile> = context.dataStore.data.map { prefs ->
        val raw = prefs[keyHelmetProfile] ?: HelmetAcousticProfile.MODULAR_URBAN.name
        runCatching { HelmetAcousticProfile.valueOf(raw) }.getOrDefault(HelmetAcousticProfile.MODULAR_URBAN)
    }

    suspend fun saveCallsign(callsign: String): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyCallsign] = callsign
        }
    }

    suspend fun saveDefaultMode(mode: IntercomMode): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyDefaultMode] = mode.name
        }
    }

    suspend fun saveRideChannel(channel: Int): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyRideChannel] = channel.coerceIn(1, 16)
        }
    }

    suspend fun saveVadThreshold(threshold: Float): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyVadThreshold] = threshold.coerceIn(0.01f, 0.50f)
        }
    }

    suspend fun saveOpusBitrate(bitrate: Int): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyOpusBitrate] = bitrate
        }
    }

    suspend fun saveAutoConnect(autoConnect: Boolean): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyAutoConnect] = autoConnect
        }
    }

    suspend fun saveLoudspeaker(enabled: Boolean): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyLoudspeaker] = enabled
        }
    }

    suspend fun saveHelmetProfile(profile: HelmetAcousticProfile): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            prefs[keyHelmetProfile] = profile.name
            prefs[keyVadThreshold] = profile.vadThreshold
            prefs[keyOpusBitrate] = profile.opusBitrateKbps
        }
    }
}
