package com.meshride.domain.repository

import com.meshride.domain.model.AudioRoutingState
import kotlinx.coroutines.flow.Flow

interface BluetoothScoRepository {
    fun observeAudioRouting(): Flow<AudioRoutingState>
    suspend fun startBluetoothSco(): Result<Unit>
    suspend fun stopBluetoothSco(): Result<Unit>
}
