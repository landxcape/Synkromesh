package com.meshride.domain.model

data class AudioPacket(
    val sequenceNumber: Long,
    val senderId: String,
    val timestampMs: Long,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioPacket
        return sequenceNumber == other.sequenceNumber &&
                senderId == other.senderId &&
                timestampMs == other.timestampMs &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
