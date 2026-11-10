package com.meshride.domain.repository

import com.meshride.domain.model.AudioPacket
import com.meshride.domain.model.MeshPeer
import com.meshride.domain.model.MeshState
import kotlinx.coroutines.flow.Flow

interface MeshNetworkRepository {
    fun observeMeshState(): Flow<MeshState>
    fun observePeers(): Flow<List<MeshPeer>>
    fun observeIncomingAudioPackets(): Flow<AudioPacket>

    suspend fun startMeshSession(callsign: String): Result<Unit>
    suspend fun stopMeshSession(): Result<Unit>
    suspend fun broadcastVoicePacket(packet: AudioPacket): Result<Unit>
    suspend fun startLocalHotspot(): Result<Unit>
    suspend fun stopLocalHotspot(): Result<Unit>
}
