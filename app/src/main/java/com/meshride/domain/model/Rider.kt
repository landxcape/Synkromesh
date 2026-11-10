package com.meshride.domain.model

data class Rider(
    val id: String,
    val callsign: String,
    val isLocalUser: Boolean = false,
    val isTransmitting: Boolean = false,
    val signalStrengthDbm: Int = -60,
    val latencyMs: Long = 18L
)
