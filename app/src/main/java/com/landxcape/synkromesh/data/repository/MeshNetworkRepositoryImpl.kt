package com.landxcape.synkromesh.data.repository

import com.landxcape.synkromesh.data.datasource.local.PreferencesDataSource
import com.landxcape.synkromesh.data.datasource.mesh.LocalHotspotDriver
import com.landxcape.synkromesh.data.datasource.mesh.LocationTelemetryDataSource
import com.landxcape.synkromesh.data.datasource.mesh.NearbyMeshDiscoveryDriver
import com.landxcape.synkromesh.data.datasource.mesh.UdpMeshSocketDriver
import com.landxcape.synkromesh.domain.model.AudioPacket
import com.landxcape.synkromesh.domain.model.MeshPeer
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshNetworkRepositoryImpl @Inject constructor(
    private val udpDriver: UdpMeshSocketDriver,
    private val nearbyDriver: NearbyMeshDiscoveryDriver,
    private val hotspotDriver: LocalHotspotDriver,
    private val locationDataSource: LocationTelemetryDataSource,
    private val audioEngineRepository: AudioEngineRepository,
    private val preferencesDataSource: PreferencesDataSource
) : MeshNetworkRepository {

    private val meshScope = CoroutineScope(Dispatchers.IO)
    private var audioBridgeJob: Job? = null
    private var telemetryBroadcastJob: Job? = null
    private var heartbeatJob: Job? = null

    override fun observeMeshState(): Flow<MeshState> = combine(
        nearbyDriver.meshState,
        udpDriver.udpPeersFlow
    ) { nearbyState, udpPeers ->
        when {
            udpPeers.isNotEmpty() -> MeshState.CONNECTED
            nearbyState == MeshState.CONNECTED -> MeshState.CONNECTED
            nearbyState == MeshState.SEARCHING -> MeshState.SEARCHING
            else -> MeshState.DISCONNECTED
        }
    }

    // DUAL-LAYER PEER AGGREGATION: Combines Nearby Connections + UDP Broadcast peers
    override fun observePeers(): Flow<List<MeshPeer>> = combine(
        nearbyDriver.peersFlow,
        udpDriver.udpPeersFlow
    ) { nearbyPeers, udpPeers ->
        val combined = (nearbyPeers + udpPeers)
        // Group by callsign or ID to avoid duplicates
        combined.distinctBy { it.callsign.lowercase().trim() }
    }

    override fun observeIncomingAudioPackets(): Flow<AudioPacket> = udpDriver.incomingAudioFlow

    override suspend fun startMeshSession(callsign: String, channel: Int): Result<Unit> = runCatching {
        val deviceId = preferencesDataSource.getDeviceId()

        // 1. Configure and start UDP broadcast driver
        udpDriver.localDeviceId = deviceId
        udpDriver.localCallsign = callsign
        udpDriver.activeChannel = channel
        udpDriver.startSocket().getOrThrow()

        // 2. Start automated zero-config BLE discovery & Wi-Fi Direct handshake for Channel
        nearbyDriver.startDiscoveryAndAdvertising(callsign, channel, deviceId).getOrThrow()

        // 3. Start full-duplex audio bridging over UDP
        audioBridgeJob?.cancel()
        audioBridgeJob = meshScope.launch {
            // Outgoing transmission: mic -> Opus -> UDP broadcast
            launch {
                audioEngineRepository.observeOutgoingPackets().collect { outgoingPacket ->
                    udpDriver.sendPacket(outgoingPacket)
                }
            }
            // Incoming reception: UDP socket -> Opus decode -> loud speaker or helmet
            launch {
                udpDriver.incomingAudioFlow.collect { incomingPacket ->
                    audioEngineRepository.playIncomingPacket(incomingPacket)
                }
            }
        }

        // 4. Periodic UDP presence beacon (Every 1.5s so riders appear instantly on Wi-Fi/Hotspot)
        heartbeatJob?.cancel()
        heartbeatJob = meshScope.launch {
            while (true) {
                delay(1500L)
                udpDriver.sendHeartbeat()
            }
        }

        // 5. Periodic telemetry broadcasting for distance calculation
        telemetryBroadcastJob?.cancel()
        telemetryBroadcastJob = meshScope.launch {
            while (true) {
                delay(2500L)
                val loc = locationDataSource.currentLocation.value
                if (loc != null) {
                    nearbyDriver.broadcastTelemetry(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        speedKmh = loc.speedKmh,
                        bearingDegrees = loc.bearingDegrees
                    )
                }
            }
        }
    }

    override suspend fun stopMeshSession(): Result<Unit> = runCatching {
        audioBridgeJob?.cancel()
        audioBridgeJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        telemetryBroadcastJob?.cancel()
        telemetryBroadcastJob = null
        nearbyDriver.stop().getOrThrow()
        udpDriver.stopSocket().getOrThrow()
        hotspotDriver.stopLocalHotspot().getOrThrow()
    }

    override suspend fun broadcastVoicePacket(packet: AudioPacket): Result<Unit> {
        return udpDriver.sendPacket(packet)
    }

    override suspend fun broadcastTelemetry(
        latitude: Double,
        longitude: Double,
        speedKmh: Float,
        bearingDegrees: Float
    ): Result<Unit> {
        return nearbyDriver.broadcastTelemetry(latitude, longitude, speedKmh, bearingDegrees)
    }

    override suspend fun startLocalHotspot(): Result<Unit> {
        return hotspotDriver.startLocalHotspot()
    }

    override suspend fun stopLocalHotspot(): Result<Unit> {
        return hotspotDriver.stopLocalHotspot()
    }
}
