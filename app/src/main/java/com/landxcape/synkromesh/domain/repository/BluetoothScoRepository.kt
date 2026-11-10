package com.landxcape.synkromesh.domain.repository

import com.landxcape.synkromesh.domain.model.AudioRoutingState
import kotlinx.coroutines.flow.Flow

interface BluetoothScoRepository {
    fun observeAudioRouting(): Flow<AudioRoutingState>
    suspend fun startBluetoothSco(): Result<Unit>
    suspend fun stopBluetoothSco(): Result<Unit>
}
