package com.landxcape.synkromesh.data.datasource.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

object BleBeaconCodec {
    const val MANUFACTURER_ID = 0x054D // SynkroMesh proprietary tag

    fun encodePayload(channel: Int, deviceIdHash: Int, flags: Byte): ByteArray {
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buffer.put(channel.toByte())
        buffer.putInt(deviceIdHash)
        buffer.put(flags)
        return buffer.array()
    }

    fun decodePayload(data: ByteArray): Triple<Int, Int, Byte>? {
        if (data.size < 6) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val channel = buffer.get().toInt() and 0xFF
        val deviceIdHash = buffer.int
        val flags = buffer.get()
        return Triple(channel, deviceIdHash, flags)
    }
}

@Singleton
class BleProximityBeaconDriver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val SYNCHROMESH_SERVICE_UUID: UUID = UUID.fromString("0000534D-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private val _isBeaconingActive = MutableStateFlow(false)
    val isBeaconingActive: StateFlow<Boolean> = _isBeaconingActive.asStateFlow()

    var onPeerProximityDetected: ((channel: Int, peerDeviceHash: Int) -> Unit)? = null

    private fun hasPermission(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun canAdvertise(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) && hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    private fun canScan(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun startBeaconing(channel: Int, localDeviceId: String, isSessionActive: Boolean) {
        if (_isBeaconingActive.value) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        val deviceHash = localDeviceId.hashCode()
        val flags: Byte = if (isSessionActive) 0x02 else 0x01
        val payload = BleBeaconCodec.encodePayload(channel, deviceHash, flags)

        // 1. Start BLE Advertising
        if (canAdvertise()) {
            advertiser = adapter.bluetoothLeAdvertiser
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(SYNCHROMESH_SERVICE_UUID))
                .addManufacturerData(BleBeaconCodec.MANUFACTURER_ID, payload)
                .build()

            val cb = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    _isBeaconingActive.value = true
                }
                override fun onStartFailure(errorCode: Int) {
                    _isBeaconingActive.value = false
                }
            }
            try {
                advertiser?.startAdvertising(settings, data, cb)
                advertiseCallback = cb
            } catch (_: SecurityException) {}
        }

        // 2. Start BLE Scanning for proximity wakeup
        if (canScan()) {
            scanner = adapter.bluetoothLeScanner
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SYNCHROMESH_SERVICE_UUID))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()

            val scb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { res ->
                        val record = res.scanRecord ?: return@let
                        val mfgData = record.getManufacturerSpecificData(BleBeaconCodec.MANUFACTURER_ID)
                        if (mfgData != null) {
                            val decoded = BleBeaconCodec.decodePayload(mfgData)
                            if (decoded != null) {
                                val (peerChannel, peerHash, _) = decoded
                                if (peerHash != deviceHash) {
                                    onPeerProximityDetected?.invoke(peerChannel, peerHash)
                                }
                            }
                        }
                    }
                }
            }
            try {
                scanner?.startScan(listOf(scanFilter), scanSettings, scb)
                scanCallback = scb
            } catch (_: SecurityException) {}
        }
    }

    fun stopBeaconing() {
        if (!_isBeaconingActive.value && advertiseCallback == null && scanCallback == null) return

        try {
            if (canAdvertise() && advertiseCallback != null) {
                advertiser?.stopAdvertising(advertiseCallback)
            }
        } catch (_: SecurityException) {}
        advertiseCallback = null
        advertiser = null

        try {
            if (canScan() && scanCallback != null) {
                scanner?.stopScan(scanCallback)
            }
        } catch (_: SecurityException) {}
        scanCallback = null
        scanner = null

        _isBeaconingActive.value = false
    }
}
