package com.landxcape.synkromesh.domain.repository

import com.landxcape.synkromesh.domain.model.AudioPacket
import com.landxcape.synkromesh.domain.model.MeshPeer
import com.landxcape.synkromesh.domain.model.MeshState
import kotlinx.coroutines.flow.Flow

interface MeshNetworkRepository {
    fun observeMeshState(): Flow<MeshState>
    fun observePeers(): Flow<List<MeshPeer>>
    fun observeIncomingAudioPackets(): Flow<AudioPacket>
    fun observeIsHotspotActive(): Flow<Boolean>
    fun observeHotspotSsid(): Flow<String?>
    fun observeHotspotPassword(): Flow<String?>

    suspend fun startMeshSession(callsign: String, channel: Int = 1): Result<Unit>
    suspend fun stopMeshSession(): Result<Unit>
    suspend fun broadcastVoicePacket(packet: AudioPacket): Result<Unit>
    suspend fun broadcastTelemetry(
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        speedKmh: Float = 0f,
        bearingDegrees: Float = 0f,
        batteryPercent: Int = 100
    ): Result<Unit>
    suspend fun startLocalHotspot(): Result<Unit>
    suspend fun stopLocalHotspot(): Result<Unit>
    suspend fun toggleLocalHotspot(): Result<Boolean>
}
