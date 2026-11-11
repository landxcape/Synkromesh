package com.landxcape.synkromesh.data.datasource.mesh.security

/**
 * Sliding-window replay protection for UDP datagram sequences.
 * Uses a 64-bit bitmap to track received packets within a 64-sequence window
 * behind the highest received sequence number.
 */
class ReplayWindow(private val windowSize: Int = 64) {
    private var maxSeq: Long = -1L
    private var windowBitmap: ULong = 0uL

    @Synchronized
    fun isPacketAcceptable(seq: Long): Boolean {
        if (maxSeq == -1L) {
            maxSeq = seq
            windowBitmap = 1uL
            return true
        }

        val diff = seq - maxSeq
        return when {
            // New highest sequence number received
            diff > 0 -> {
                if (diff < windowSize) {
                    windowBitmap = (windowBitmap shl diff.toInt()) or 1uL
                } else {
                    windowBitmap = 1uL
                }
                maxSeq = seq
                true
            }
            // Sequence is within the backward window [maxSeq - (windowSize - 1), maxSeq]
            diff <= 0 && diff > -windowSize -> {
                val bitIndex = (-diff).toInt()
                val mask = 1uL shl bitIndex
                if ((windowBitmap and mask) != 0uL) {
                    // Duplicate sequence (replay attack or network duplicate)
                    false
                } else {
                    // Out-of-order packet within window; accept and mark received
                    windowBitmap = windowBitmap or mask
                    true
                }
            }
            // Older than window size: stale packet
            else -> false
        }
    }

    @Synchronized
    fun reset() {
        maxSeq = -1L
        windowBitmap = 0uL
    }

    /**
     * Calculates the packet loss percentage over the active window.
     * Computes the ratio of missing packets to total expected packets in O(1) time.
     */
    @Synchronized
    fun getPacketLossPercentage(): Float {
        if (maxSeq == -1L || windowBitmap == 0uL) return 0f
        val receivedCount = java.lang.Long.bitCount(windowBitmap.toLong())
        val effectiveWindow = minOf(windowSize, (maxSeq + 1).toInt())
        if (effectiveWindow <= 1) return 0f
        val missingCount = effectiveWindow - receivedCount
        return if (missingCount > 0) (missingCount.toFloat() / effectiveWindow.toFloat()) * 100f else 0f
    }
}
