package com.landxcape.synkromesh.data.datasource.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalHotspotDriver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _isHotspotActive = MutableStateFlow(false)
    val isHotspotActive: StateFlow<Boolean> = _isHotspotActive.asStateFlow()

    private val _isHotspotStarting = MutableStateFlow(false)
    val isHotspotStarting: StateFlow<Boolean> = _isHotspotStarting.asStateFlow()

    private val _hotspotError = MutableStateFlow<String?>(null)
    val hotspotError: StateFlow<String?> = _hotspotError.asStateFlow()

    private val _hotspotSsid = MutableStateFlow<String?>(null)
    val hotspotSsid: StateFlow<String?> = _hotspotSsid.asStateFlow()

    private val _hotspotPassword = MutableStateFlow<String?>(null)
    val hotspotPassword: StateFlow<String?> = _hotspotPassword.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLocalHotspot(): Result<Unit> = runCatching {
        if (_isHotspotActive.value || _isHotspotStarting.value) return@runCatching
        val manager = wifiManager ?: error("WifiManager unavailable")

        _isHotspotStarting.value = true
        _hotspotError.value = null

        manager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                super.onStarted(reservation)
                hotspotReservation = reservation
                _isHotspotStarting.value = false
                _isHotspotActive.value = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val config = reservation?.softApConfiguration
                    _hotspotSsid.value = config?.ssid
                    _hotspotPassword.value = config?.passphrase
                } else {
                    @Suppress("DEPRECATION")
                    _hotspotSsid.value = reservation?.wifiConfiguration?.SSID
                    @Suppress("DEPRECATION")
                    _hotspotPassword.value = reservation?.wifiConfiguration?.preSharedKey
                }
            }

            override fun onStopped() {
                super.onStopped()
                hotspotReservation = null
                _isHotspotStarting.value = false
                _isHotspotActive.value = false
                _hotspotSsid.value = null
                _hotspotPassword.value = null
            }

            override fun onFailed(reason: Int) {
                super.onFailed(reason)
                hotspotReservation = null
                _isHotspotStarting.value = false
                _isHotspotActive.value = false
                _hotspotSsid.value = null
                _hotspotPassword.value = null

                val err = when (reason) {
                    WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "Hotspot failed: No Wi-Fi channel available"
                    WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Hotspot failed: Tethering disallowed by system"
                    WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Hotspot failed: Incompatible Wi-Fi mode"
                    else -> "Hotspot failed to start (Error $reason)"
                }
                _hotspotError.value = err
            }
        }, null)
    }

    fun stopLocalHotspot(): Result<Unit> = runCatching {
        hotspotReservation?.close()
        hotspotReservation = null
        _isHotspotStarting.value = false
        _isHotspotActive.value = false
        _hotspotSsid.value = null
        _hotspotPassword.value = null
    }
}
