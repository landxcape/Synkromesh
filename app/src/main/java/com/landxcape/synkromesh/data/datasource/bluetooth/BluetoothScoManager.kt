package com.landxcape.synkromesh.data.datasource.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.landxcape.synkromesh.domain.model.AudioRoutingState
import com.landxcape.synkromesh.domain.model.AudioRoutingTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothScoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _routingState = MutableStateFlow(
        AudioRoutingState(
            activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
            isScoConnected = false,
            connectedDeviceName = null
        )
    )
    val routingState: StateFlow<AudioRoutingState> = _routingState.asStateFlow()

    private var isScoRequested = false
    private var isReceiverRegistered = false

    private val scoReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                    )
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            _routingState.value = AudioRoutingState(
                                activeTarget = AudioRoutingTarget.BLUETOOTH_SCO_HEADSET,
                                isScoConnected = true,
                                connectedDeviceName = "Bluetooth Helmet Headset"
                            )
                        }
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                            _routingState.value = AudioRoutingState(
                                activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
                                isScoConnected = false,
                                connectedDeviceName = null
                            )
                            if (isScoRequested) {
                                // Attempt reconnection if headset is still linked
                                reconnectSco()
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val deviceName = try {
                        device?.name ?: "Bluetooth Helmet Headset"
                    } catch (e: SecurityException) {
                        "Bluetooth Helmet Headset"
                    }
                    if (isScoRequested) {
                        startSco()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (_routingState.value.isScoConnected) {
                        _routingState.value = AudioRoutingState(
                            activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
                            isScoConnected = false,
                            connectedDeviceName = null
                        )
                    }
                }
            }
        }
    }

    init {
        registerReceivers()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setupModernDeviceListener()
        }
    }

    @SuppressLint("MissingPermission")
    fun startSco(): Result<Unit> = runCatching {
        isScoRequested = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startModernSco()
        } else {
            startLegacySco()
        }
    }

    fun stopSco(): Result<Unit> = runCatching {
        isScoRequested = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopModernSco()
        } else {
            stopLegacySco()
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        _routingState.value = AudioRoutingState(
            activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
            isScoConnected = false,
            connectedDeviceName = null
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startModernSco() {
        val scoDevice = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }

        if (scoDevice != null) {
            val success = audioManager.setCommunicationDevice(scoDevice)
            if (success) {
                _routingState.value = AudioRoutingState(
                    activeTarget = AudioRoutingTarget.BLUETOOTH_SCO_HEADSET,
                    isScoConnected = true,
                    connectedDeviceName = scoDevice.productName.toString().ifBlank { "Bluetooth Helmet Headset" }
                )
            }
        } else {
            _routingState.value = AudioRoutingState(
                activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
                isScoConnected = false,
                connectedDeviceName = null
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun stopModernSco() {
        audioManager.clearCommunicationDevice()
    }

    @Suppress("DEPRECATION")
    private fun startLegacySco() {
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    @Suppress("DEPRECATION")
    private fun stopLegacySco() {
        audioManager.isBluetoothScoOn = false
        audioManager.stopBluetoothSco()
    }

    private fun reconnectSco() {
        scope.launch {
            if (isScoRequested) {
                startSco()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupModernDeviceListener() {
        audioManager.addOnCommunicationDeviceChangedListener(
            Executors.newSingleThreadExecutor()
        ) { device ->
            scope.launch {
                if (device != null && device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    _routingState.value = AudioRoutingState(
                        activeTarget = AudioRoutingTarget.BLUETOOTH_SCO_HEADSET,
                        isScoConnected = true,
                        connectedDeviceName = device.productName.toString().ifBlank { "Bluetooth Helmet Headset" }
                    )
                } else if (device == null || device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    _routingState.value = AudioRoutingState(
                        activeTarget = AudioRoutingTarget.DEVICE_SPEAKER,
                        isScoConnected = false,
                        connectedDeviceName = null
                    )
                }
            }
        }
    }

    private fun registerReceivers() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                @Suppress("DEPRECATION")
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            context.registerReceiver(scoReceiver, filter)
            isReceiverRegistered = true
        }
    }

    fun release() {
        stopSco()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scoReceiver)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
            isReceiverRegistered = false
        }
    }
}
