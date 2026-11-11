package com.landxcape.synkromesh.domain.model

data class MeshPeer(
    val id: String,
    val callsign: String,
    val isLocal: Boolean = false,
    val ipAddress: String? = null,
    val signalDbm: Int = -60,
    val isTransmitting: Boolean = false,
    val lastSeenTimestampMs: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val speedKmh: Float? = null,
    val bearingDegrees: Float? = null,
    val estimatedDistanceMeters: Float? = null,
    val relativeBearingDegrees: Float? = null,
    val batteryPercent: Int? = null,
    val rttMs: Int? = null,
    val packetLossPercent: Float? = null
)
