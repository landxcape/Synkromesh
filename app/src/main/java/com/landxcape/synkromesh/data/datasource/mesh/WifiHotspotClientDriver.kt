package com.landxcape.synkromesh.data.datasource.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import com.landxcape.synkromesh.domain.model.HotspotClientState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiHotspotClientDriver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val driverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var reconnectJob: Job? = null

    @Volatile
    private var isSessionBound = false
    @Volatile
    private var targetSsid: String? = null
    @Volatile
    private var targetPassword: String? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _clientState = MutableStateFlow(HotspotClientState.IDLE)
    val clientState: StateFlow<HotspotClientState> = _clientState.asStateFlow()

    private val _connectedSsid = MutableStateFlow<String?>(null)
    val connectedSsid: StateFlow<String?> = _connectedSsid.asStateFlow()

    private val _hotspotNetwork = MutableStateFlow<Network?>(null)
    val hotspotNetwork: StateFlow<Network?> = _hotspotNetwork.asStateFlow()

    private val _subnetBroadcastIp = MutableStateFlow<String?>(null)
    val subnetBroadcastIp: StateFlow<String?> = _subnetBroadcastIp.asStateFlow()

    fun connectToHotspot(ssid: String, password: String): Result<Unit> = runCatching {
        disconnect()

        isSessionBound = true
        targetSsid = ssid
        targetPassword = password

        _clientState.value = HotspotClientState.CONNECTING
        _connectedSsid.value = ssid

        attemptConnection(ssid, password)
    }

    private fun attemptConnection(ssid: String, password: String) {
        if (!isSessionBound) return
        val manager = connectivityManager ?: return
        unregisterCallbackSilently()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .apply {
                    if (password.isNotBlank()) {
                        setWpa2Passphrase(password)
                    }
                }
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    reconnectJob?.cancel()
                    reconnectJob = null
                    _hotspotNetwork.value = network
                    updateSubnetBroadcast(network)
                    _isConnected.value = true
                    _connectedSsid.value = ssid
                    _clientState.value = HotspotClientState.CONNECTED
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    super.onLinkPropertiesChanged(network, linkProperties)
                    _hotspotNetwork.value = network
                    val bcast = computeSubnetBroadcast(linkProperties)
                    if (bcast != null) {
                        _subnetBroadcastIp.value = bcast
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    _hotspotNetwork.value = network
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    _hotspotNetwork.value = null
                    _subnetBroadcastIp.value = null
                    _isConnected.value = false
                    if (isSessionBound) {
                        _clientState.value = HotspotClientState.RECONNECTING
                        triggerAutoReconnect()
                    } else {
                        _connectedSsid.value = null
                        _clientState.value = HotspotClientState.IDLE
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    _hotspotNetwork.value = null
                    _subnetBroadcastIp.value = null
                    _isConnected.value = false
                    if (isSessionBound) {
                        _clientState.value = HotspotClientState.RECONNECTING
                        triggerAutoReconnect()
                    } else {
                        _connectedSsid.value = null
                        _clientState.value = HotspotClientState.FAILED
                    }
                }
            }

            networkCallback = callback
            try {
                manager.requestNetwork(request, callback)
            } catch (_: Exception) {
                if (isSessionBound) {
                    triggerAutoReconnect()
                }
            }
        }
    }

    private fun triggerAutoReconnect() {
        if (!isSessionBound) return
        reconnectJob?.cancel()
        reconnectJob = driverScope.launch {
            delay(2500L)
            if (isSessionBound && !_isConnected.value) {
                val ssid = targetSsid ?: return@launch
                val pwd = targetPassword.orEmpty()
                attemptConnection(ssid, pwd)
            }
        }
    }

    private fun updateSubnetBroadcast(network: Network) {
        val lp = connectivityManager?.getLinkProperties(network) ?: return
        val bcast = computeSubnetBroadcast(lp)
        if (bcast != null) {
            _subnetBroadcastIp.value = bcast
        }
    }

    private fun computeSubnetBroadcast(lp: LinkProperties): String? {
        val linkAddr = lp.linkAddresses.firstOrNull {
            it.address is java.net.Inet4Address
        } ?: return null
        val addr = linkAddr.address.address
        val prefix = linkAddr.prefixLength
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val addrInt = java.nio.ByteBuffer.wrap(addr).int
        val broadcast = addrInt or mask.inv()
        val b = java.nio.ByteBuffer.allocate(4).putInt(broadcast).array()
        return "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}.${b[3].toInt() and 0xFF}"
    }

    private fun unregisterCallbackSilently() {
        networkCallback?.let { callback ->
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
        networkCallback = null
    }

    fun disconnect(): Result<Unit> = runCatching {
        isSessionBound = false
        targetSsid = null
        targetPassword = null
        reconnectJob?.cancel()
        reconnectJob = null
        unregisterCallbackSilently()
        _hotspotNetwork.value = null
        _subnetBroadcastIp.value = null
        _isConnected.value = false
        _connectedSsid.value = null
        _clientState.value = HotspotClientState.IDLE
    }
}
