package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.BleBeaconCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BleBeaconCodecTest {

    @Test
    fun `test encoding and decoding valid 6-byte beacon payload`() {
        val channel = 5
        val deviceIdHash = 0x12345678
        val flags: Byte = 0x02

        val encoded = BleBeaconCodec.encodePayload(channel, deviceIdHash, flags)
        assertEquals(6, encoded.size)

        val decoded = BleBeaconCodec.decodePayload(encoded)
        assertNotNull(decoded)
        assertEquals(channel, decoded!!.first)
        assertEquals(deviceIdHash, decoded.second)
        assertEquals(flags, decoded.third)
    }

    @Test
    fun `test decoding truncated payload returns null`() {
        val truncated = byteArrayOf(0x01, 0x02, 0x03)
        val decoded = BleBeaconCodec.decodePayload(truncated)
        assertNull(decoded)
    }
}
