package com.meshride.data.repository

import android.content.Context
import android.media.AudioManager
import com.meshride.domain.model.AudioRoutingState
import com.meshride.domain.model.AudioRoutingTarget
import com.meshride.domain.repository.BluetoothScoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothScoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BluetoothScoRepository {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val _routingState = MutableStateFlow(
        AudioRoutingState(
            activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
            isScoConnected = false,
            connectedDeviceName = null
        )
    )

    override fun observeAudioRouting(): Flow<AudioRoutingState> = _routingState.asStateFlow()

    override suspend fun startBluetoothSco(): Result<Unit> = runCatching {
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.startBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = true
            _routingState.value = AudioRoutingState(
                activeTarget = AudioRoutingTarget.BLUETOOTH_SCO_HEADSET,
                isScoConnected = true,
                connectedDeviceName = "Bluetooth Helmet Headset"
            )
        }
    }

    override suspend fun stopBluetoothSco(): Result<Unit> = runCatching {
        audioManager?.let { am ->
            @Suppress("DEPRECATION")
            am.stopBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = false
            am.mode = AudioManager.MODE_NORMAL
            _routingState.value = AudioRoutingState(
                activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
                isScoConnected = false,
                connectedDeviceName = null
            )
        }
    }
}
