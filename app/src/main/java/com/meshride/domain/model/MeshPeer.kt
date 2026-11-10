package com.meshride.domain.model

data class MeshPeer(
    val id: String,
    val callsign: String,
    val isLocal: Boolean = false,
    val ipAddress: String? = null,
    val signalDbm: Int = -60,
    val isTransmitting: Boolean = false,
    val lastSeenTimestampMs: Long = System.currentTimeMillis()
)
