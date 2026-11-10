package com.landxcape.synkromesh.data.datasource.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PairedHeadset(
    val name: String,
    val address: String,
    val isAudioDevice: Boolean,
    val isBonded: Boolean
)

@Singleton
class BluetoothHeadsetDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _devicesFlow = MutableStateFlow<List<PairedHeadset>>(emptyList())
    val devicesFlow: StateFlow<List<PairedHeadset>> = _devicesFlow.asStateFlow()

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _devicesFlow.value = emptyList()
            return
        }

        try {
            val bondedDevices: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
            val list = bondedDevices.map { device ->
                val major = device.bluetoothClass?.majorDeviceClass
                val isAudio = major == BluetoothClass.Device.Major.AUDIO_VIDEO
                PairedHeadset(
                    name = device.name ?: "Unknown Device",
                    address = device.address ?: "",
                    isAudioDevice = isAudio,
                    isBonded = true
                )
            }.sortedWith(compareByDescending<PairedHeadset> { it.isAudioDevice }.thenBy { it.name })
            _devicesFlow.value = list
        } catch (_: SecurityException) {
            _devicesFlow.value = emptyList()
        }
    }
}
