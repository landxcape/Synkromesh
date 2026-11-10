package com.landxcape.synkromesh.domain.repository

import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.domain.model.TransmissionState
import kotlinx.coroutines.flow.Flow

interface IntercomRepository {
    fun observeTransmissionState(): Flow<TransmissionState>
    fun observeIntercomMode(): Flow<IntercomMode>
    fun observeConnectedRiders(): Flow<List<Rider>>
    fun observeIsMuted(): Flow<Boolean>
    fun observeIsSessionActive(): Flow<Boolean>

    suspend fun setIntercomMode(mode: IntercomMode): Result<Unit>
    suspend fun setMicrophoneMute(muted: Boolean): Result<Unit>
    suspend fun toggleMicrophoneMute(): Result<Boolean>
    suspend fun handlePttPress(isPressed: Boolean): Result<Unit>
    suspend fun startIntercomSession(): Result<Unit>
    suspend fun stopIntercomSession(): Result<Unit>
}
