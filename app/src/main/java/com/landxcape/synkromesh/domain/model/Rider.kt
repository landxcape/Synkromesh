package com.landxcape.synkromesh.domain.model

data class Rider(
    val id: String,
    val callsign: String,
    val isLocalUser: Boolean = false,
    val isTransmitting: Boolean = false,
    val signalStrengthDbm: Int = -60,
    val latencyMs: Long = 18L,
    val estimatedDistanceMeters: Float? = null,
    val relativePosition: String? = null,
    val relativeBearingDegrees: Float? = null,
    val altitudeMeters: Double? = null,
    val speedKmh: Float? = null,
    val batteryPercent: Int? = null,
    val packetLossPercent: Float = 0f,
    val hopCount: Int = 1,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val isRelayed: Boolean get() = hopCount > 1

    val formattedDistance: String
        get() = when {
            estimatedDistanceMeters == null -> "—"
            estimatedDistanceMeters < 1000f -> "${estimatedDistanceMeters.toInt()}m"
            else -> String.format(java.util.Locale.US, "%.1f km", estimatedDistanceMeters / 1000f)
        }

    val formattedSpeed: String
        get() = if (speedKmh != null && speedKmh > 0f) "${speedKmh.toInt()} km/h" else "0 km/h"

    val formattedAltitude: String
        get() = if (altitudeMeters != null) "${altitudeMeters.toInt()}m" else "—"

    val formattedBattery: String
        get() = if (batteryPercent != null) "$batteryPercent%" else "100%"

    val bearingArrow: String
        get() = when (relativeBearingDegrees) {
            null -> "•"
            in -22.5f..22.5f -> "↑"
            in 22.5f..67.5f -> "↗"
            in 67.5f..112.5f -> "→"
            in 112.5f..157.5f -> "↘"
            in -67.5f..-22.5f -> "↖"
            in -112.5f..-67.5f -> "←"
            in -157.5f..-112.5f -> "↙"
            else -> "↓"
        }
}
