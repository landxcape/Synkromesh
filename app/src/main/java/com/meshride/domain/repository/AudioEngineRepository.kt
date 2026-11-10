package com.meshride.domain.repository

import com.meshride.domain.model.AudioLevel
import com.meshride.domain.model.AudioPacket
import kotlinx.coroutines.flow.Flow

interface AudioEngineRepository {
    fun observeMicAudioLevel(): Flow<AudioLevel>
    fun observeIsLoopbackActive(): Flow<Boolean>
    fun observeIsAudioEngineActive(): Flow<Boolean>
    fun observeOutgoingPackets(): Flow<AudioPacket>

    suspend fun startAudioEngine(): Result<Unit>
    suspend fun stopAudioEngine(): Result<Unit>
    suspend fun setLoopbackMode(enabled: Boolean): Result<Unit>
    suspend fun toggleLoopbackMode(): Result<Boolean>
    suspend fun playIncomingPacket(packet: AudioPacket): Result<Unit>
}
