package com.landxcape.synkromesh.data.datasource.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.landxcape.synkromesh.domain.model.MeshPeer
import com.landxcape.synkromesh.domain.model.MeshState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyMeshDiscoveryDriver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDataSource: LocationTelemetryDataSource
) {

    companion object {
        private const val TAG = "NearbyMeshDiscovery"
        private const val BASE_SERVICE_ID = "com.landxcape.synkromesh.intercom"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val TELEMETRY_PREFIX = "TEL:"
    }

    private val client: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    private val _meshState = MutableStateFlow(MeshState.DISCONNECTED)
    val meshState: StateFlow<MeshState> = _meshState.asStateFlow()

    private val _peersMap = mutableMapOf<String, MeshPeer>()
    private val _peersFlow = MutableStateFlow<List<MeshPeer>>(emptyList())
    val peersFlow: StateFlow<List<MeshPeer>> = _peersFlow.asStateFlow()

    private var localCallsign: String = "Rider"
    private var localDeviceId: String = ""
    private var activeChannel: Int = 1

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept connection handshake on both sides
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { Log.w(TAG, "acceptConnection failed: ${it.message}") }

            val rawName = info.endpointName
            val callsign = rawName.substringBefore("|").ifBlank { "Rider $endpointId" }
            val peer = _peersMap[endpointId]?.copy(callsign = callsign) ?: MeshPeer(
                id = endpointId,
                callsign = callsign,
                estimatedDistanceMeters = LocationTelemetryDataSource.estimateDistanceRssiMeters(-58)
            )
            _peersMap[endpointId] = peer
            updatePeersList()
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                _meshState.value = MeshState.CONNECTED
                updatePeersList()
                broadcastCurrentLocationToEndpoint(endpointId)
            } else {
                Log.w(TAG, "Connection to $endpointId failed with status: ${resolution.status.statusCode}")
                // Keep discovered peer visible even if initial handshake retries
                updatePeersList()
            }
        }

        override fun onDisconnected(endpointId: String) {
            _peersMap.remove(endpointId)
            updatePeersList()
            if (_peersMap.isEmpty()) {
                _meshState.value = MeshState.SEARCHING
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val rawName = info.endpointName
            val callsign = rawName.substringBefore("|").ifBlank { "Rider $endpointId" }

            // INSTANT VISIBILITY: Show the discovered rider on Radar immediately!
            val peer = _peersMap[endpointId] ?: MeshPeer(
                id = endpointId,
                callsign = callsign,
                estimatedDistanceMeters = LocationTelemetryDataSource.estimateDistanceRssiMeters(-60)
            )
            _peersMap[endpointId] = peer
            updatePeersList()

            // Request connection
            val myTag = if (localDeviceId.isNotBlank()) "$localCallsign|$localDeviceId" else localCallsign
            client.requestConnection(myTag, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { err ->
                    // May already be connected or initiated simultaneously
                    Log.d(TAG, "requestConnection note for $endpointId: ${err.message}")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            _peersMap.remove(endpointId)
            updatePeersList()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val text = String(bytes, StandardCharsets.UTF_8)
                if (text.startsWith(TELEMETRY_PREFIX)) {
                    handleTelemetryPayload(endpointId, text.removePrefix(TELEMETRY_PREFIX))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun handleTelemetryPayload(endpointId: String, data: String) {
        val parts = data.split(",")
        if (parts.size >= 4) {
            val peerLat = parts[0].toDoubleOrNull() ?: return
            val peerLon = parts[1].toDoubleOrNull() ?: return
            val speed = parts[2].toFloatOrNull() ?: 0f
            val bearing = parts[3].toFloatOrNull() ?: 0f

            val myLoc = locationDataSource.currentLocation.value
            val (dist, relBearing) = if (myLoc != null) {
                val d = LocationTelemetryDataSource.calculateHaversineDistanceMeters(
                    myLoc.latitude, myLoc.longitude, peerLat, peerLon
                )
                val peerBearing = LocationTelemetryDataSource.calculateBearingDegrees(
                    myLoc.latitude, myLoc.longitude, peerLat, peerLon
                )
                val rel = LocationTelemetryDataSource.calculateRelativeBearing(
                    myLoc.bearingDegrees, peerBearing
                )
                Pair(d, rel)
            } else {
                Pair(null, null)
            }

            val existing = _peersMap[endpointId] ?: MeshPeer(id = endpointId, callsign = "Rider $endpointId")
            _peersMap[endpointId] = existing.copy(
                latitude = peerLat,
                longitude = peerLon,
                speedKmh = speed,
                bearingDegrees = bearing,
                estimatedDistanceMeters = dist ?: existing.estimatedDistanceMeters,
                relativeBearingDegrees = relBearing,
                lastSeenTimestampMs = System.currentTimeMillis()
            )
            updatePeersList()
        }
    }

    fun startDiscoveryAndAdvertising(
        callsign: String,
        channel: Int = 1,
        deviceId: String = ""
    ): Result<Unit> = runCatching {
        localCallsign = callsign
        activeChannel = channel
        localDeviceId = deviceId
        _meshState.value = MeshState.SEARCHING

        val serviceId = "${BASE_SERVICE_ID}.ch$channel"
        val advertisedName = if (deviceId.isNotBlank()) "$callsign|$deviceId" else callsign

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        client.startAdvertising(advertisedName, serviceId, connectionLifecycleCallback, advertisingOptions)
            .addOnFailureListener { Log.w(TAG, "startAdvertising failed: ${it.message}") }

        client.startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnFailureListener { Log.w(TAG, "startDiscovery failed: ${it.message}") }

        locationDataSource.startLocationUpdates()
    }

    fun broadcastTelemetry(lat: Double, lon: Double, speedKmh: Float, bearingDegrees: Float): Result<Unit> = runCatching {
        if (_peersMap.isEmpty()) return Result.success(Unit)
        val payloadStr = "$TELEMETRY_PREFIX$lat,$lon,$speedKmh,$bearingDegrees"
        val payload = Payload.fromBytes(payloadStr.toByteArray(StandardCharsets.UTF_8))
        val endpoints = _peersMap.keys.toList()
        client.sendPayload(endpoints, payload)
    }

    private fun broadcastCurrentLocationToEndpoint(endpointId: String) {
        val loc = locationDataSource.currentLocation.value ?: return
        val payloadStr = "$TELEMETRY_PREFIX${loc.latitude},${loc.longitude},${loc.speedKmh},${loc.bearingDegrees}"
        client.sendPayload(endpointId, Payload.fromBytes(payloadStr.toByteArray(StandardCharsets.UTF_8)))
    }

    fun stop(): Result<Unit> = runCatching {
        locationDataSource.stopLocationUpdates()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _peersMap.clear()
        _peersFlow.value = emptyList()
        _meshState.value = MeshState.DISCONNECTED
    }

    private fun updatePeersList() {
        _peersFlow.value = _peersMap.values.toList()
    }
}
