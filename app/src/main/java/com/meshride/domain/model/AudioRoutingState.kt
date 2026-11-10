package com.meshride.domain.model

enum class AudioRoutingTarget {
    BLUETOOTH_SCO_HEADSET,
    WIRED_HEADSET,
    DEVICE_SPEAKER,
    DISCONNECTED
}

data class AudioRoutingState(
    val activeTarget: AudioRoutingTarget = AudioRoutingTarget.DEVICE_SPEAKER,
    val isScoConnected: Boolean = false,
    val connectedDeviceName: String? = null
)
