package com.meshride.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meshride.domain.model.IntercomMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meshride_preferences")

@Singleton
class PreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyCallsign = stringPreferencesKey("rider_callsign")
    private val keyDefaultMode = stringPreferencesKey("default_intercom_mode")

    val callsignFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyCallsign] ?: "Rider 1"
    }

    val defaultModeFlow: Flow<IntercomMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[keyDefaultMode] ?: IntercomMode.PUSH_TO_TALK.name
        runCatching { IntercomMode.valueOf(raw) }.getOrDefault(IntercomMode.PUSH_TO_TALK)
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
}
