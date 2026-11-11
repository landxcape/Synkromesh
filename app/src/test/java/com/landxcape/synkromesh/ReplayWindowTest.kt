package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.security.ReplayWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReplayWindowTest {

    private lateinit var replayWindow: ReplayWindow

    @Before
    fun setUp() {
        replayWindow = ReplayWindow(windowSize = 64)
    }

    @Test
    fun `sequential in-order packets are accepted`() {
        assertTrue(replayWindow.isPacketAcceptable(1L))
        assertTrue(replayWindow.isPacketAcceptable(2L))
        assertTrue(replayWindow.isPacketAcceptable(3L))
        assertTrue(replayWindow.isPacketAcceptable(4L))
    }

    @Test
    fun `duplicate sequence number is rejected`() {
        assertTrue(replayWindow.isPacketAcceptable(10L))
        assertFalse(replayWindow.isPacketAcceptable(10L)) // Replay
    }

    @Test
    fun `out of order packet within 64-frame window is accepted once`() {
        assertTrue(replayWindow.isPacketAcceptable(10L))
        assertTrue(replayWindow.isPacketAcceptable(8L)) // Out-of-order within window
        assertFalse(replayWindow.isPacketAcceptable(8L)) // Second time is duplicate
        assertTrue(replayWindow.isPacketAcceptable(9L)) // Still within window
        assertFalse(replayWindow.isPacketAcceptable(9L)) // Second time is duplicate
    }

    @Test
    fun `packet older than window size is rejected as stale`() {
        assertTrue(replayWindow.isPacketAcceptable(100L))
        // Window covers [37..100]
        assertTrue(replayWindow.isPacketAcceptable(50L))
        // Older than 100 - 64 = 36
        assertFalse(replayWindow.isPacketAcceptable(35L))
        assertFalse(replayWindow.isPacketAcceptable(20L))
        assertFalse(replayWindow.isPacketAcceptable(1L))
    }

    @Test
    fun `large sequence jump shifts window forward`() {
        assertTrue(replayWindow.isPacketAcceptable(10L))
        assertTrue(replayWindow.isPacketAcceptable(1000L)) // Jump
        // 10L is now far behind window [937..1000]
        assertFalse(replayWindow.isPacketAcceptable(10L))
        assertTrue(replayWindow.isPacketAcceptable(990L)) // Valid inside new window
    }

    @Test
    fun `packet loss calculation is 0 percent when all packets arrive in order`() {
        for (i in 0L..63L) {
            replayWindow.isPacketAcceptable(i)
        }
        org.junit.Assert.assertEquals(0f, replayWindow.getPacketLossPercentage(), 0.001f)
    }

    @Test
    fun `packet loss calculation reflects missing packets`() {
        // Send every other packet (50% loss over 64 frames: 0, 2, 4, ... 62)
        for (i in 0L..62L step 2L) {
            replayWindow.isPacketAcceptable(i)
        }
        // Highest is 62, effectiveWindow is 63, received 32, missing 31 -> ~49.2%
        val loss = replayWindow.getPacketLossPercentage()
        assertTrue("Loss should be roughly 49%", loss in 48f..51f)
    }
}
