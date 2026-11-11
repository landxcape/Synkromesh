package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.routing.PacketRelayFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketRelayFilterTest {

    @Test
    fun `shouldRelay returns true for first appearance and false for duplicates`() {
        val filter = PacketRelayFilter(capacity = 16)
        assertTrue(filter.shouldRelay("peer-1", 100L))
        assertFalse(filter.shouldRelay("peer-1", 100L))
        assertTrue(filter.shouldRelay("peer-1", 101L))
        assertTrue(filter.shouldRelay("peer-2", 100L))
    }

    @Test
    fun `hasSeen reflects registered packets accurately`() {
        val filter = PacketRelayFilter(capacity = 16)
        assertFalse(filter.hasSeen("peer-A", 42L))
        filter.shouldRelay("peer-A", 42L)
        assertTrue(filter.hasSeen("peer-A", 42L))
    }

    @Test
    fun `filter evicts oldest entries when capacity is exceeded`() {
        val capacity = 4
        val filter = PacketRelayFilter(capacity = capacity)

        for (i in 1..capacity) {
            assertTrue(filter.shouldRelay("peer", i.toLong()))
        }

        // 1 to 4 should all be seen
        for (i in 1..capacity) {
            assertTrue(filter.hasSeen("peer", i.toLong()))
        }

        // Add 5th packet -> evicts 1
        assertTrue(filter.shouldRelay("peer", 5L))
        assertFalse(filter.hasSeen("peer", 1L))
        assertTrue(filter.hasSeen("peer", 2L))
        assertTrue(filter.hasSeen("peer", 5L))

        // Since 1 was evicted, shouldRelay for 1 should be true again
        assertTrue(filter.shouldRelay("peer", 1L))
    }

    @Test
    fun `clear resets all cached state`() {
        val filter = PacketRelayFilter(capacity = 8)
        filter.shouldRelay("peer-1", 1L)
        filter.shouldRelay("peer-2", 2L)
        filter.clear()
        assertFalse(filter.hasSeen("peer-1", 1L))
        assertFalse(filter.hasSeen("peer-2", 2L))
        assertTrue(filter.shouldRelay("peer-1", 1L))
    }
}
