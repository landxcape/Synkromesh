package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.LocationTelemetryDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTelemetryUnitTest {

    @Test
    fun calculateHaversineDistance_samePoint_returnsZero() {
        val lat = 27.7172
        val lon = 85.3240
        val dist = LocationTelemetryDataSource.calculateHaversineDistanceMeters(lat, lon, lat, lon)
        assertEquals(0.0f, dist, 0.01f)
    }

    @Test
    fun calculateHaversineDistance_knownPoints_returnsAccurateDistance() {
        // Points ~111 km apart on the equator (1 degree of latitude)
        val lat1 = 0.0
        val lon1 = 0.0
        val lat2 = 1.0
        val lon2 = 0.0
        val dist = LocationTelemetryDataSource.calculateHaversineDistanceMeters(lat1, lon1, lat2, lon2)
        // 1 degree of latitude is approximately 111,195 meters
        assertTrue(dist > 110_000f && dist < 112_000f)
    }

    @Test
    fun calculateBearing_dueNorth_returnsZeroDegrees() {
        val lat1 = 10.0
        val lon1 = 20.0
        val lat2 = 11.0
        val lon2 = 20.0
        val bearing = LocationTelemetryDataSource.calculateBearingDegrees(lat1, lon1, lat2, lon2)
        assertEquals(0.0f, bearing, 0.5f)
    }

    @Test
    fun calculateBearing_dueEast_returnsNinetyDegrees() {
        val lat1 = 0.0
        val lon1 = 0.0
        val lat2 = 0.0
        val lon2 = 1.0
        val bearing = LocationTelemetryDataSource.calculateBearingDegrees(lat1, lon1, lat2, lon2)
        assertEquals(90.0f, bearing, 0.5f)
    }

    @Test
    fun calculateRelativeBearing_andLabels_correctlyClassifies() {
        // Heading North (0°), Peer Bearing North (0°) -> Ahead
        val aheadRel = LocationTelemetryDataSource.calculateRelativeBearing(0f, 0f)
        assertEquals(0f, aheadRel, 0.1f)
        assertEquals("Ahead", LocationTelemetryDataSource.getRelativePositionLabel(aheadRel))

        // Heading North (0°), Peer Bearing East (90°) -> Right
        val rightRel = LocationTelemetryDataSource.calculateRelativeBearing(0f, 90f)
        assertEquals(90f, rightRel, 0.1f)
        assertEquals("Right", LocationTelemetryDataSource.getRelativePositionLabel(rightRel))

        // Heading North (0°), Peer Bearing South (180°) -> Behind
        val behindRel = LocationTelemetryDataSource.calculateRelativeBearing(0f, 180f)
        assertEquals(180f, behindRel, 0.1f)
        assertEquals("Behind", LocationTelemetryDataSource.getRelativePositionLabel(behindRel))

        // Heading North (0°), Peer Bearing West (270°) -> Left (-90°)
        val leftRel = LocationTelemetryDataSource.calculateRelativeBearing(0f, 270f)
        assertEquals(-90f, leftRel, 0.1f)
        assertEquals("Left", LocationTelemetryDataSource.getRelativePositionLabel(leftRel))
    }

    @Test
    fun estimateDistanceRssi_strongSignal_returnsCloseDistance() {
        val distClose = LocationTelemetryDataSource.estimateDistanceRssiMeters(-58)
        assertTrue(distClose in 0.9f..2.0f)

        val distFar = LocationTelemetryDataSource.estimateDistanceRssiMeters(-85)
        assertTrue(distFar > 5.0f)
    }

    @Test
    fun riderMetrics_formattingAndArrows_renderAccurately() {
        val riderNorth = com.landxcape.synkromesh.domain.model.Rider(
            id = "test-1",
            callsign = "GhostRider",
            relativeBearingDegrees = 0f,
            speedKmh = 62.4f,
            altitudeMeters = 1450.0,
            batteryPercent = 88,
            estimatedDistanceMeters = 250f
        )
        assertEquals("↑", riderNorth.bearingArrow)
        assertEquals("62 km/h", riderNorth.formattedSpeed)
        assertEquals("1450m", riderNorth.formattedAltitude)
        assertEquals("88%", riderNorth.formattedBattery)
        assertEquals("250m", riderNorth.formattedDistance)

        val riderNorthEast = riderNorth.copy(relativeBearingDegrees = 45f)
        assertEquals("↗", riderNorthEast.bearingArrow)

        val riderEast = riderNorth.copy(relativeBearingDegrees = 90f)
        assertEquals("→", riderEast.bearingArrow)

        val riderSouth = riderNorth.copy(relativeBearingDegrees = 180f)
        assertEquals("↓", riderSouth.bearingArrow)

        val riderWest = riderNorth.copy(relativeBearingDegrees = -90f)
        assertEquals("←", riderWest.bearingArrow)
    }

    @Test
    fun binaryTelemetryPacking_roundTrip_preservesAllMetrics() {
        val lat = 27.7172
        val lon = 85.3240
        val alt = 1350.5
        val speed = 55.0f
        val bearing = 120.0f
        val battery = 92

        val buffer = java.nio.ByteBuffer.allocate(33).apply {
            order(java.nio.ByteOrder.BIG_ENDIAN)
            putDouble(lat)
            putDouble(lon)
            putDouble(alt)
            putFloat(speed)
            putFloat(bearing)
            put(battery.toByte())
        }

        val bytes = buffer.array()
        assertEquals(33, bytes.size)

        val readBuf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN)
        assertEquals(lat, readBuf.double, 0.0001)
        assertEquals(lon, readBuf.double, 0.0001)
        assertEquals(alt, readBuf.double, 0.001)
        assertEquals(speed, readBuf.float, 0.01f)
        assertEquals(bearing, readBuf.float, 0.01f)
        assertEquals(battery, readBuf.get().toInt() and 0xFF)
    }
}
