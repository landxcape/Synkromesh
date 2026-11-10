package com.landxcape.synkromesh.data.datasource.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
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

    private val _hotspotSsid = MutableStateFlow<String?>(null)
    val hotspotSsid: StateFlow<String?> = _hotspotSsid.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startLocalHotspot(): Result<Unit> = runCatching {
        if (_isHotspotActive.value) return@runCatching
        val manager = wifiManager ?: error("WifiManager unavailable")

        manager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                super.onStarted(reservation)
                hotspotReservation = reservation
                _isHotspotActive.value = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    _hotspotSsid.value = reservation?.softApConfiguration?.ssid
                }
            }

            override fun onStopped() {
                super.onStopped()
                hotspotReservation = null
                _isHotspotActive.value = false
                _hotspotSsid.value = null
            }

            override fun onFailed(reason: Int) {
                super.onFailed(reason)
                hotspotReservation = null
                _isHotspotActive.value = false
                _hotspotSsid.value = null
            }
        }, null)
    }

    fun stopLocalHotspot(): Result<Unit> = runCatching {
        hotspotReservation?.close()
        hotspotReservation = null
        _isHotspotActive.value = false
        _hotspotSsid.value = null
    }
}
