package com.landxcape.synkromesh.data.datasource.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class RiderLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val bearingDegrees: Float = 0f,
    val batteryPercent: Int = 100,
    val timestampMs: Long = System.currentTimeMillis()
)

@Singleton
class LocationTelemetryDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    fun getBatteryPercentage(): Int {
        return try {
            val capacity = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            if (capacity in 0..100) capacity else 100
        } catch (_: Exception) {
            100
        }
    }

    private val _currentLocation = MutableStateFlow<RiderLocation?>(null)
    val currentLocation: StateFlow<RiderLocation?> = _currentLocation.asStateFlow()

    private var isListening = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0f
            val bearing = if (location.hasBearing()) location.bearing else 0f
            _currentLocation.value = RiderLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                speedKmh = speedKmh,
                bearingDegrees = bearing,
                batteryPercent = getBatteryPercentage(),
                timestampMs = location.time
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isListening) return
        val lm = locationManager ?: return

        try {
            // Check for last known location first for instantaneous startup
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = when {
                lastGps != null && lastNetwork != null -> if (lastGps.time > lastNetwork.time) lastGps else lastNetwork
                lastGps != null -> lastGps
                else -> lastNetwork
            }
            if (bestLast != null) {
                _currentLocation.value = RiderLocation(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    altitude = bestLast.altitude,
                    speedKmh = if (bestLast.hasSpeed()) bestLast.speed * 3.6f else 0f,
                    bearingDegrees = if (bestLast.hasBearing()) bestLast.bearing else 0f,
                    batteryPercent = getBatteryPercentage()
                )
            }

            // Register for active updates (every 2 seconds or 2 meters)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    2.0f,
                    locationListener
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    5.0f,
                    locationListener
                )
            }
            isListening = true
        } catch (_: SecurityException) {
            // Location permission not yet granted
        }
    }

    fun stopLocationUpdates() {
        if (!isListening) return
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: SecurityException) {}
        isListening = false
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0

        /**
         * Calculates the great-circle distance between two GPS points using the Haversine formula.
         */
        fun calculateHaversineDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Float {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)

            val a = sin(dLat / 2).pow(2.0) +
                    sin(dLon / 2).pow(2.0) * cos(rLat1) * cos(rLat2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (EARTH_RADIUS_METERS * c).toFloat()
        }

        /**
         * Calculates initial compass bearing from point 1 to point 2 (0° to 360°).
         */
        fun calculateBearingDegrees(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Float {
            val rLat1 = Math.toRadians(lat1)
            val rLat2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)

            val y = sin(dLon) * cos(rLat2)
            val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
            val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
            return bearing.toFloat()
        }

        /**
         * Computes the relative angle (-180° to +180°) of a peer relative to our current heading.
         * ~0° = Ahead, ~180° = Behind, ~90° = Right, ~-90° = Left.
         */
        fun calculateRelativeBearing(
            myHeadingDegrees: Float,
            bearingToPeerDegrees: Float
        ): Float {
            var diff = bearingToPeerDegrees - myHeadingDegrees
            while (diff > 180f) diff -= 360f
            while (diff < -180f) diff += 360f
            return diff
        }

        /**
         * Returns human-readable relative position (e.g. "Ahead", "Behind", "Left", "Right").
         */
        fun getRelativePositionLabel(relativeBearingDegrees: Float): String {
            return when {
                relativeBearingDegrees in -45f..45f -> "Ahead"
                relativeBearingDegrees in 45f..135f -> "Right"
                relativeBearingDegrees in -135f..-45f -> "Left"
                else -> "Behind"
            }
        }

        /**
         * Log-distance path loss approximation when GPS fix is missing.
         * Assumes outdoor line-of-sight path loss exponent n = 2.8, TxPower @ 1m = -58 dBm.
         */
        fun estimateDistanceRssiMeters(signalDbm: Int): Float {
            val txPower = -58.0
            val n = 2.8
            val exponent = (txPower - signalDbm) / (10.0 * n)
            val dist = 10.0.pow(exponent).toFloat()
            return dist.coerceIn(1.0f, 250.0f)
        }
    }
}
