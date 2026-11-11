package com.landxcape.synkromesh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class TacticalVoiceCueEngineTest {

    private class RateLimiterTestHarness(private val cooldownMs: Long = 6000L) {
        private val lastCueTimes = ConcurrentHashMap<String, Long>()

        fun shouldSpeak(cueKey: String, currentTime: Long): Boolean {
            val lastTime = lastCueTimes[cueKey]
            if (lastTime != null && currentTime - lastTime < cooldownMs) return false
            lastCueTimes[cueKey] = currentTime
            return true
        }
    }

    @Test
    fun `test voice cue rate limiter blocks rapid duplicate announcements`() {
        val harness = RateLimiterTestHarness(6000L)

        // First announcement should pass
        assertTrue(harness.shouldSpeak("peer_join_Apex", 1000L))

        // Same announcement 2 seconds later should be suppressed
        assertFalse(harness.shouldSpeak("peer_join_Apex", 3000L))

        // Different announcement should pass
        assertTrue(harness.shouldSpeak("peer_join_Sweeper", 3500L))

        // After cooldown expires, announcement passes again
        assertTrue(harness.shouldSpeak("peer_join_Apex", 7500L))
    }
}
