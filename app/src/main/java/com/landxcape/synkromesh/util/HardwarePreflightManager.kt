package com.landxcape.synkromesh.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PreflightStatus(
    val hasRecordAudio: Boolean = false,
    val hasBluetoothConnect: Boolean = false,
    val hasBluetoothScan: Boolean = false,
    val hasNearbyWifiDevices: Boolean = false,
    val hasLocation: Boolean = false,
    val hasPostNotifications: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isLocationEnabled: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false
) {
    val areCriticalPermissionsGranted: Boolean
        get() = hasRecordAudio &&
                hasBluetoothConnect &&
                hasNearbyWifiDevices &&
                hasPostNotifications

    val areRadiosEnabled: Boolean
        get() = isBluetoothEnabled && isWifiEnabled

    val isReadyForIntercom: Boolean
        get() = areCriticalPermissionsGranted && areRadiosEnabled
}

@Singleton
class HardwarePreflightManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _statusFlow = MutableStateFlow(computeStatus())
    val statusFlow: StateFlow<PreflightStatus> = _statusFlow.asStateFlow()

    fun refresh() {
        _statusFlow.value = computeStatus()
    }

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.RECORD_AUDIO)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun computeStatus(): PreflightStatus {
        val hasRecordAudio = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
        val hasBluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
        val hasBluetoothScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            true
        }
        val hasNearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            true
        }
        val hasLocation = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val isBluetoothEnabled = bluetoothAdapter?.isEnabled == true

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiEnabled = wifiManager?.isWifiEnabled == true

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocationEnabled = locationManager?.let { LocationManagerCompat.isLocationEnabled(it) } == true

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        return PreflightStatus(
            hasRecordAudio = hasRecordAudio,
            hasBluetoothConnect = hasBluetoothConnect,
            hasBluetoothScan = hasBluetoothScan,
            hasNearbyWifiDevices = hasNearbyWifi,
            hasLocation = hasLocation,
            hasPostNotifications = hasPostNotifications,
            isBluetoothEnabled = isBluetoothEnabled,
            isWifiEnabled = isWifiEnabled,
            isLocationEnabled = isLocationEnabled,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored
        )
    }
}
