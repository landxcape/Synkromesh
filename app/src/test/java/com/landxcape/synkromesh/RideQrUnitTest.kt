package com.landxcape.synkromesh

import com.landxcape.synkromesh.util.RideQrData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RideQrUnitTest {

    @Test
    fun rideQrData_roundTripSerialization_matchesOriginal() {
        val original = RideQrData(
            channel = 3,
            callsign = "GhostRider",
            ssid = "Synkromesh-9481",
            password = "securePassword123"
        )

        val uriStr = original.toUriString()
        val parsed = RideQrData.fromUriString(uriStr)

        assertNotNull(parsed)
        assertEquals(original.channel, parsed!!.channel)
        assertEquals(original.callsign, parsed.callsign)
        assertEquals(original.ssid, parsed.ssid)
        assertEquals(original.password, parsed.password)
    }

    @Test
    fun rideQrData_withoutHotspot_serializesAndParsesCorrectly() {
        val original = RideQrData(
            channel = 7,
            callsign = "Lead Scout",
            ssid = null,
            password = null
        )

        val uriStr = original.toUriString()
        val parsed = RideQrData.fromUriString(uriStr)

        assertNotNull(parsed)
        assertEquals(7, parsed!!.channel)
        assertEquals("Lead Scout", parsed.callsign)
        assertNull(parsed.ssid)
        assertNull(parsed.password)
    }

    @Test
    fun rideQrData_invalidUri_returnsNull() {
        assertNull(RideQrData.fromUriString("https://google.com"))
        assertNull(RideQrData.fromUriString("synkromesh://other?ch=1"))
        assertNull(RideQrData.fromUriString("random_garbage_string"))
    }
}
