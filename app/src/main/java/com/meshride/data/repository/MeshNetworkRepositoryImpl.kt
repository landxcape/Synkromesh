package com.meshride.data.repository

import com.meshride.data.datasource.mesh.LocalHotspotDriver
import com.meshride.data.datasource.mesh.NearbyMeshDiscoveryDriver
import com.meshride.data.datasource.mesh.UdpMeshSocketDriver
import com.meshride.domain.model.AudioPacket
import com.meshride.domain.model.MeshPeer
import com.meshride.domain.model.MeshState
import com.meshride.domain.repository.AudioEngineRepository
import com.meshride.domain.repository.MeshNetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshNetworkRepositoryImpl @Inject constructor(
    private val udpDriver: UdpMeshSocketDriver,
    private val nearbyDriver: NearbyMeshDiscoveryDriver,
    private val hotspotDriver: LocalHotspotDriver,
    private val audioEngineRepository: AudioEngineRepository
) : MeshNetworkRepository {

    private val meshScope = CoroutineScope(Dispatchers.IO)
    private var audioBridgeJob: Job? = null

    override fun observeMeshState(): Flow<MeshState> = nearbyDriver.meshState
    override fun observePeers(): Flow<List<MeshPeer>> = nearbyDriver.peersFlow
    override fun observeIncomingAudioPackets(): Flow<AudioPacket> = udpDriver.incomingAudioFlow

    override suspend fun startMeshSession(callsign: String): Result<Unit> = runCatching {
        // 1. Open low-latency UDP socket
        udpDriver.startSocket().getOrThrow()

        // 2. Start automated zero-config BLE discovery & Wi-Fi Direct handshake
        nearbyDriver.startDiscoveryAndAdvertising(callsign).getOrThrow()

        // 3. Start full-duplex audio bridging over UDP
        audioBridgeJob?.cancel()
        audioBridgeJob = meshScope.launch {
            // Outgoing transmission: mic -> Opus -> UDP broadcast
            launch {
                audioEngineRepository.observeOutgoingPackets().collect { outgoingPacket ->
                    udpDriver.sendPacket(outgoingPacket)
                }
            }
            // Incoming reception: UDP socket -> Opus decode -> helmet speaker
            launch {
                udpDriver.incomingAudioFlow.collect { incomingPacket ->
                    audioEngineRepository.playIncomingPacket(incomingPacket)
                }
            }
        }
    }

    override suspend fun stopMeshSession(): Result<Unit> = runCatching {
        audioBridgeJob?.cancel()
        audioBridgeJob = null
        nearbyDriver.stop().getOrThrow()
        udpDriver.stopSocket().getOrThrow()
        hotspotDriver.stopLocalHotspot().getOrThrow()
    }

    override suspend fun broadcastVoicePacket(packet: AudioPacket): Result<Unit> {
        return udpDriver.sendPacket(packet)
    }

    override suspend fun startLocalHotspot(): Result<Unit> {
        return hotspotDriver.startLocalHotspot()
    }

    override suspend fun stopLocalHotspot(): Result<Unit> {
        return hotspotDriver.stopLocalHotspot()
    }
}
