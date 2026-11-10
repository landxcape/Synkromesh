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
    val relativeBearingDegrees: Float? = null
) {
    val formattedDistance: String
        get() = when {
            estimatedDistanceMeters == null -> "—"
            estimatedDistanceMeters < 1000f -> "${estimatedDistanceMeters.toInt()}m"
            else -> String.format(java.util.Locale.US, "%.1f km", estimatedDistanceMeters / 1000f)
        }
}
