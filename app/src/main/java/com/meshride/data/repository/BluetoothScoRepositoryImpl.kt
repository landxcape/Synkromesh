package com.meshride.data.repository

import com.meshride.data.datasource.bluetooth.BluetoothScoManager
import com.meshride.domain.model.AudioRoutingState
import com.meshride.domain.repository.BluetoothScoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothScoRepositoryImpl @Inject constructor(
    private val bluetoothScoManager: BluetoothScoManager
) : BluetoothScoRepository {

    override fun observeAudioRouting(): Flow<AudioRoutingState> = bluetoothScoManager.routingState

    override suspend fun startBluetoothSco(): Result<Unit> {
        return bluetoothScoManager.startSco()
    }

    override suspend fun stopBluetoothSco(): Result<Unit> {
        return bluetoothScoManager.stopSco()
    }
}
