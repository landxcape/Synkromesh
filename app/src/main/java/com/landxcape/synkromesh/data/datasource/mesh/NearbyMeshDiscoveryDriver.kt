package com.landxcape.synkromesh.data.datasource.mesh

import android.content.Context
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyMeshDiscoveryDriver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val SERVICE_ID = "com.landxcape.synkromesh.intercom"
        private val STRATEGY = Strategy.P2P_CLUSTER
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

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept connection handshake
            client.acceptConnection(endpointId, payloadCallback)
            val peer = MeshPeer(
                id = endpointId,
                callsign = info.endpointName.ifBlank { "Rider $endpointId" }
            )
            _peersMap[endpointId] = peer
            updatePeersList()
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                _meshState.value = MeshState.CONNECTED
                updatePeersList()
            } else {
                _peersMap.remove(endpointId)
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
            // Initiate connection to discovered bike
            client.requestConnection(localCallsign, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            _peersMap.remove(endpointId)
            updatePeersList()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // Handshake & metadata payloads can be received here
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun startDiscoveryAndAdvertising(callsign: String): Result<Unit> = runCatching {
        localCallsign = callsign
        _meshState.value = MeshState.SEARCHING

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        client.startAdvertising(callsign, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
        client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
    }

    fun stop(): Result<Unit> = runCatching {
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
