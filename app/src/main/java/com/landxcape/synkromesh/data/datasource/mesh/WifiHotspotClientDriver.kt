package com.landxcape.synkromesh.data.datasource.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiHotspotClientDriver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedSsid = MutableStateFlow<String?>(null)
    val connectedSsid: StateFlow<String?> = _connectedSsid.asStateFlow()

    private val _hotspotNetwork = MutableStateFlow<Network?>(null)
    val hotspotNetwork: StateFlow<Network?> = _hotspotNetwork.asStateFlow()

    private val _subnetBroadcastIp = MutableStateFlow<String?>(null)
    val subnetBroadcastIp: StateFlow<String?> = _subnetBroadcastIp.asStateFlow()

    fun connectToHotspot(ssid: String, password: String): Result<Unit> = runCatching {
        val manager = connectivityManager ?: error("ConnectivityManager unavailable")
        disconnect()

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
                    manager.bindProcessToNetwork(network)
                    _hotspotNetwork.value = network
                    _subnetBroadcastIp.value = computeSubnetBroadcast(network)
                    _isConnected.value = true
                    _connectedSsid.value = ssid
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    manager.bindProcessToNetwork(null)
                    _hotspotNetwork.value = null
                    _subnetBroadcastIp.value = null
                    _isConnected.value = false
                    _connectedSsid.value = null
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    _hotspotNetwork.value = null
                    _subnetBroadcastIp.value = null
                    _isConnected.value = false
                    _connectedSsid.value = null
                }
            }

            networkCallback = callback
            manager.requestNetwork(request, callback)
        }
    }

    private fun computeSubnetBroadcast(network: Network): String? {
        val lp = connectivityManager?.getLinkProperties(network) ?: return null
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

    fun disconnect(): Result<Unit> = runCatching {
        val manager = connectivityManager ?: return@runCatching
        networkCallback?.let { callback ->
            try {
                manager.bindProcessToNetwork(null)
                manager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
        networkCallback = null
        _hotspotNetwork.value = null
        _subnetBroadcastIp.value = null
        _isConnected.value = false
        _connectedSsid.value = null
    }
}
