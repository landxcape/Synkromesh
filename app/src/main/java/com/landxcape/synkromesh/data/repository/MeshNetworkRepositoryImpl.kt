package com.landxcape.synkromesh.data.repository

import com.landxcape.synkromesh.data.datasource.audio.TacticalVoiceCueEngine
import com.landxcape.synkromesh.data.datasource.local.PreferencesDataSource
import com.landxcape.synkromesh.data.datasource.mesh.LocalHotspotDriver
import com.landxcape.synkromesh.data.datasource.mesh.LocationTelemetryDataSource
import com.landxcape.synkromesh.data.datasource.mesh.NearbyMeshDiscoveryDriver
import com.landxcape.synkromesh.data.datasource.mesh.UdpMeshSocketDriver
import com.landxcape.synkromesh.data.datasource.mesh.WifiHotspotClientDriver
import com.landxcape.synkromesh.domain.model.AudioPacket
import com.landxcape.synkromesh.domain.model.HotspotClientState
import com.landxcape.synkromesh.domain.model.MeshPeer
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.TransportMode
import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshNetworkRepositoryImpl @Inject constructor(
    private val udpDriver: UdpMeshSocketDriver,
    private val nearbyDriver: NearbyMeshDiscoveryDriver,
    private val hotspotDriver: LocalHotspotDriver,
    private val wifiHotspotClientDriver: WifiHotspotClientDriver,
    private val locationDataSource: LocationTelemetryDataSource,
    private val audioEngineRepository: AudioEngineRepository,
    private val preferencesDataSource: PreferencesDataSource,
    private val tacticalVoiceCueEngine: TacticalVoiceCueEngine
) : MeshNetworkRepository {

    private val meshScope = CoroutineScope(Dispatchers.IO)
    private var audioBridgeJob: Job? = null
    private var telemetryBroadcastJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectMonitorJob: Job? = null
    private var activePackToken: String? = null
    private var activeTransportMode: TransportMode = TransportMode.DUAL

    override fun setPackToken(token: String?) {
        activePackToken = token
        udpDriver.activePackToken = token
    }

    override fun observeMeshState(): Flow<MeshState> = combine(
        nearbyDriver.meshState,
        udpDriver.udpPeersFlow
    ) { nearbyState, udpPeers ->
        when {
            udpPeers.isNotEmpty() -> MeshState.CONNECTED
            activeTransportMode == TransportMode.DUAL && nearbyState == MeshState.CONNECTED -> MeshState.CONNECTED
            activeTransportMode == TransportMode.DUAL && nearbyState == MeshState.SEARCHING -> MeshState.SEARCHING
            else -> MeshState.DISCONNECTED
        }
    }

    override fun observePeers(): Flow<List<MeshPeer>> = combine(
        nearbyDriver.peersFlow,
        udpDriver.udpPeersFlow
    ) { nearbyPeers, udpPeers ->
        val activeNearby = if (activeTransportMode == TransportMode.DUAL) nearbyPeers else emptyList()
        val combined = (activeNearby + udpPeers)
        combined.distinctBy { it.callsign.lowercase().trim() }
    }

    override fun observeIncomingAudioPackets(): Flow<AudioPacket> = merge(
        udpDriver.incomingAudioFlow,
        nearbyDriver.incomingAudioFlow
    )

    override fun observeIsHotspotActive(): Flow<Boolean> = hotspotDriver.isHotspotActive
    override fun observeIsHotspotStarting(): Flow<Boolean> = hotspotDriver.isHotspotStarting
    override fun observeHotspotError(): Flow<String?> = hotspotDriver.hotspotError
    override fun observeHotspotSsid(): Flow<String?> = hotspotDriver.hotspotSsid
    override fun observeHotspotPassword(): Flow<String?> = hotspotDriver.hotspotPassword

    override suspend fun startMeshSession(
        callsign: String,
        channel: Int,
        transportMode: TransportMode
    ): Result<Unit> = runCatching {
        activeTransportMode = transportMode
        val deviceId = preferencesDataSource.getDeviceId()

        // 1. Configure and start UDP broadcast driver
        udpDriver.localDeviceId = deviceId
        udpDriver.localCallsign = callsign
        udpDriver.activeChannel = channel
        udpDriver.activePackToken = activePackToken

        val hotspotNet = if (transportMode == TransportMode.HOTSPOT_UDP) {
            wifiHotspotClientDriver.hotspotNetwork.value
        } else null

        val subnetBroadcast = if (transportMode == TransportMode.HOTSPOT_UDP) {
            wifiHotspotClientDriver.subnetBroadcastIp.value
        } else null

        udpDriver.setBroadcastTarget(subnetBroadcast)
        udpDriver.startSocket(hotspotNet).getOrThrow()

        // 2. Start automated zero-config BLE discovery & Wi-Fi Direct handshake for Channel (DUAL mode only)
        if (transportMode == TransportMode.DUAL) {
            nearbyDriver.startDiscoveryAndAdvertising(callsign, channel, deviceId).getOrThrow()
        }

        // 3. Start full-duplex audio bridging:
        // Transmits over UDP (and Nearby Connections if DUAL)
        audioBridgeJob?.cancel()
        audioBridgeJob = meshScope.launch {
            // Outgoing transmission: mic -> Opus -> UDP (+ Nearby if DUAL)
            launch {
                audioEngineRepository.observeOutgoingPackets().collect { outgoingPacket ->
                    udpDriver.sendPacket(outgoingPacket)
                    if (transportMode == TransportMode.DUAL) {
                        nearbyDriver.sendAudioPayload(outgoingPacket)
                    }
                }
            }
            // Incoming reception: UDP socket (+ Nearby socket if DUAL) -> Opus decode -> loud speaker or helmet
            launch {
                val incomingFlow = if (transportMode == TransportMode.DUAL) {
                    merge(udpDriver.incomingAudioFlow, nearbyDriver.incomingAudioFlow)
                } else {
                    udpDriver.incomingAudioFlow
                }
                incomingFlow.collect { incomingPacket ->
                    audioEngineRepository.playIncomingPacket(incomingPacket)
                }
            }
        }

        // 4. Periodic UDP presence beacon & telemetry (Every 1.5s so riders appear instantly on Wi-Fi/Hotspot)
        heartbeatJob?.cancel()
        heartbeatJob = meshScope.launch {
            while (true) {
                delay(1500L)
                val loc = locationDataSource.currentLocation.value
                if (loc != null) {
                    udpDriver.sendHeartbeat(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        alt = loc.altitude,
                        speedKmh = loc.speedKmh,
                        bearingDegrees = loc.bearingDegrees,
                        batteryPercent = loc.batteryPercent
                    )
                } else {
                    udpDriver.sendHeartbeat()
                }
            }
        }

        // 5. Periodic telemetry broadcasting for distance calculation (DUAL mode only)
        telemetryBroadcastJob?.cancel()
        if (transportMode == TransportMode.DUAL) {
            telemetryBroadcastJob = meshScope.launch {
                while (true) {
                    delay(2000L)
                    val loc = locationDataSource.currentLocation.value
                    if (loc != null) {
                        nearbyDriver.broadcastTelemetry(
                            lat = loc.latitude,
                            lon = loc.longitude,
                            alt = loc.altitude,
                            speedKmh = loc.speedKmh,
                            bearingDegrees = loc.bearingDegrees,
                            batteryPercent = loc.batteryPercent
                        )
                    }
                }
            }
        }

        // 6. Dynamic Reconnection & Socket Rebinding Monitor (HOTSPOT_UDP mode)
        reconnectMonitorJob?.cancel()
        if (transportMode == TransportMode.HOTSPOT_UDP) {
            reconnectMonitorJob = meshScope.launch {
                launch {
                    wifiHotspotClientDriver.subnetBroadcastIp.collect { bcast ->
                        if (bcast != null) {
                            udpDriver.setBroadcastTarget(bcast)
                        }
                    }
                }
                launch {
                    wifiHotspotClientDriver.hotspotNetwork.collect { net ->
                        if (net != null) {
                            udpDriver.updateNetworkBinding(net)
                        }
                    }
                }
                launch {
                    var previousState: HotspotClientState? = null
                    wifiHotspotClientDriver.clientState.collect { state ->
                        if (previousState != null && previousState != state) {
                            if (state == HotspotClientState.RECONNECTING) {
                                tacticalVoiceCueEngine.announceMeshDisconnected()
                            } else if (state == HotspotClientState.CONNECTED) {
                                tacticalVoiceCueEngine.announceMeshReconnected()
                            }
                        }
                        previousState = state
                    }
                }
            }
        }
    }

    override suspend fun stopMeshSession(): Result<Unit> = runCatching {
        reconnectMonitorJob?.cancel()
        reconnectMonitorJob = null
        audioBridgeJob?.cancel()
        audioBridgeJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        telemetryBroadcastJob?.cancel()
        telemetryBroadcastJob = null
        nearbyDriver.stop().getOrThrow()
        udpDriver.setBroadcastTarget(null)
        udpDriver.stopSocket().getOrThrow()
        hotspotDriver.stopLocalHotspot().getOrThrow()
        wifiHotspotClientDriver.disconnect().getOrThrow()
    }

    override suspend fun connectToLeadHotspot(ssid: String, password: String): Result<Unit> {
        return wifiHotspotClientDriver.connectToHotspot(ssid, password)
    }

    override suspend fun disconnectLeadHotspot(): Result<Unit> {
        return wifiHotspotClientDriver.disconnect()
    }

    override suspend fun broadcastVoicePacket(packet: AudioPacket): Result<Unit> {
        udpDriver.sendPacket(packet)
        if (activeTransportMode == TransportMode.DUAL) {
            nearbyDriver.sendAudioPayload(packet)
        }
        return Result.success(Unit)
    }

    override suspend fun broadcastTelemetry(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        speedKmh: Float,
        bearingDegrees: Float,
        batteryPercent: Int
    ): Result<Unit> {
        udpDriver.sendHeartbeat(latitude, longitude, altitude, speedKmh, bearingDegrees, batteryPercent)
        if (activeTransportMode == TransportMode.DUAL) {
            nearbyDriver.broadcastTelemetry(latitude, longitude, altitude, speedKmh, bearingDegrees, batteryPercent)
        }
        return Result.success(Unit)
    }

    override suspend fun startLocalHotspot(): Result<Unit> {
        return hotspotDriver.startLocalHotspot()
    }

    override suspend fun stopLocalHotspot(): Result<Unit> {
        return hotspotDriver.stopLocalHotspot()
    }

    override suspend fun toggleLocalHotspot(): Result<Boolean> = runCatching {
        if (hotspotDriver.isHotspotActive.value) {
            hotspotDriver.stopLocalHotspot().getOrThrow()
            false
        } else {
            hotspotDriver.startLocalHotspot().getOrThrow()
            true
        }
    }
}
