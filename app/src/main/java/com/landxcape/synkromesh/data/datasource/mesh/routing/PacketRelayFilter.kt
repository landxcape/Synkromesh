package com.landxcape.synkromesh.data.datasource.mesh.routing

/**
 * Zero-allocation ring-buffer deduplication filter for multi-hop packet relaying.
 * Prevents broadcast loops and retransmission storms by caching the last [capacity]
 * packet fingerprints in O(1) time.
 */
class PacketRelayFilter(private val capacity: Int = 256) {
    private val set = HashSet<Long>(capacity)
    private val ring = LongArray(capacity)
    private var head = 0
    private val lock = Any()

    /**
     * Checks if the packet should be relayed.
     * Returns true if the packet has not been seen before and registers it.
     * Returns false if the packet is a duplicate or has already been relayed.
     */
    fun shouldRelay(senderDeviceId: String, seq: Long): Boolean {
        val fingerprint = (senderDeviceId.hashCode().toLong() shl 32) xor (seq and 0xFFFFFFFFL)
        synchronized(lock) {
            if (set.contains(fingerprint)) {
                return false
            }
            val old = ring[head]
            if (old != 0L) {
                set.remove(old)
            }
            ring[head] = fingerprint
            set.add(fingerprint)
            head = (head + 1) % capacity
            return true
        }
    }

    /**
     * Checks if the packet fingerprint has already been registered.
     */
    fun hasSeen(senderDeviceId: String, seq: Long): Boolean {
        val fingerprint = (senderDeviceId.hashCode().toLong() shl 32) xor (seq and 0xFFFFFFFFL)
        synchronized(lock) {
            return set.contains(fingerprint)
        }
    }

    /**
     * Clears all cached fingerprints.
     */
    fun clear() {
        synchronized(lock) {
            set.clear()
            ring.fill(0L)
            head = 0
        }
    }
}
