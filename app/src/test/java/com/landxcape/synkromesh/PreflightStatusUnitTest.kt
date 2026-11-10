package com.landxcape.synkromesh

import com.landxcape.synkromesh.util.PreflightStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightStatusUnitTest {

    @Test
    fun defaultStatus_isNotReady() {
        val status = PreflightStatus()
        assertFalse(status.areCriticalPermissionsGranted)
        assertFalse(status.areRadiosEnabled)
        assertFalse(status.isReadyForIntercom)
    }

    @Test
    fun allGrantedAndEnabled_isReady() {
        val status = PreflightStatus(
            hasRecordAudio = true,
            hasBluetoothConnect = true,
            hasBluetoothScan = true,
            hasNearbyWifiDevices = true,
            hasLocation = true,
            hasPostNotifications = true,
            isBluetoothEnabled = true,
            isWifiEnabled = true,
            isLocationEnabled = true,
            isBatteryOptimizationIgnored = true
        )
        assertTrue(status.areCriticalPermissionsGranted)
        assertTrue(status.areRadiosEnabled)
        assertTrue(status.isReadyForIntercom)
    }

    @Test
    fun missingAudio_isNotCriticalGranted() {
        val status = PreflightStatus(
            hasRecordAudio = false,
            hasBluetoothConnect = true,
            hasNearbyWifiDevices = true,
            hasPostNotifications = true,
            isBluetoothEnabled = true,
            isWifiEnabled = true
        )
        assertFalse(status.areCriticalPermissionsGranted)
        assertFalse(status.isReadyForIntercom)
    }

    @Test
    fun bluetoothDisabled_radiosNotEnabled() {
        val status = PreflightStatus(
            hasRecordAudio = true,
            hasBluetoothConnect = true,
            hasNearbyWifiDevices = true,
            hasPostNotifications = true,
            isBluetoothEnabled = false,
            isWifiEnabled = true
        )
        assertTrue(status.areCriticalPermissionsGranted)
        assertFalse(status.areRadiosEnabled)
        assertFalse(status.isReadyForIntercom)
    }
}
