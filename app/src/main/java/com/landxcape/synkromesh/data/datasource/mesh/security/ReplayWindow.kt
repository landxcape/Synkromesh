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
}
