package com.landxcape.synkromesh

import com.landxcape.synkromesh.domain.model.HotspotClientState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiHotspotAutoReconnectTest {

    private class MockHotspotReconnectController {
        var isSessionBound = false
        var targetSsid: String? = null
        var targetPassword: String? = null
        var clientState = HotspotClientState.IDLE
        var reconnectAttempts = 0

        fun connect(ssid: String, password: String) {
            disconnect()
            isSessionBound = true
            targetSsid = ssid
            targetPassword = password
            clientState = HotspotClientState.CONNECTING
        }

        fun onAvailable() {
            clientState = HotspotClientState.CONNECTED
        }

        fun onLost() {
            if (isSessionBound) {
                clientState = HotspotClientState.RECONNECTING
                reconnectAttempts++
            } else {
                clientState = HotspotClientState.IDLE
            }
        }

        fun onUnavailable() {
            if (isSessionBound) {
                clientState = HotspotClientState.RECONNECTING
                reconnectAttempts++
            } else {
                clientState = HotspotClientState.FAILED
            }
        }

        fun disconnect() {
            isSessionBound = false
            targetSsid = null
            targetPassword = null
            clientState = HotspotClientState.IDLE
        }
    }

    @Test
    fun `test hotspot client transitions to RECONNECTING instead of IDLE when session is active`() {
        val controller = MockHotspotReconnectController()

        controller.connect("Synkromesh-Lead-7821", "pass1234")
        assertEquals(HotspotClientState.CONNECTING, controller.clientState)
        assertTrue(controller.isSessionBound)
        assertEquals("Synkromesh-Lead-7821", controller.targetSsid)

        controller.onAvailable()
        assertEquals(HotspotClientState.CONNECTED, controller.clientState)

        // Rider drifts out of range -> onLost triggered
        controller.onLost()
        assertEquals(HotspotClientState.RECONNECTING, controller.clientState)
        assertEquals(1, controller.reconnectAttempts)
        // Session credentials preserved for zero-rescan reconnection
        assertEquals("Synkromesh-Lead-7821", controller.targetSsid)
        assertTrue(controller.isSessionBound)

        // Back in range -> onAvailable triggered
        controller.onAvailable()
        assertEquals(HotspotClientState.CONNECTED, controller.clientState)

        // Explicit disconnect resets state
        controller.disconnect()
        assertEquals(HotspotClientState.IDLE, controller.clientState)
        assertFalse(controller.isSessionBound)
    }

    @Test
    fun `test onUnavailable during active session triggers RECONNECTING retry`() {
        val controller = MockHotspotReconnectController()
        controller.connect("Synkromesh-Lead-9999", "pass5678")

        controller.onUnavailable()
        assertEquals(HotspotClientState.RECONNECTING, controller.clientState)
        assertEquals(1, controller.reconnectAttempts)
        assertEquals("Synkromesh-Lead-9999", controller.targetSsid)
    }
}
