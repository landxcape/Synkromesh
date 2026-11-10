package com.landxcape.synkromesh.data.repository

import com.landxcape.synkromesh.data.datasource.bluetooth.BluetoothScoManager
import com.landxcape.synkromesh.domain.model.AudioRoutingState
import com.landxcape.synkromesh.domain.repository.BluetoothScoRepository
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
