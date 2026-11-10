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
                    _isConnected.value = true
                    _connectedSsid.value = ssid
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    manager.bindProcessToNetwork(null)
                    _isConnected.value = false
                    _connectedSsid.value = null
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    _isConnected.value = false
                    _connectedSsid.value = null
                }
            }

            networkCallback = callback
            manager.requestNetwork(request, callback)
        }
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
        _isConnected.value = false
        _connectedSsid.value = null
    }
}
