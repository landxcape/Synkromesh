package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.security.PayloadCipher
import com.landxcape.synkromesh.data.datasource.mesh.security.ReplayWindow
import com.landxcape.synkromesh.domain.model.MeshPeer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningRegressionTest {

    @Test
    fun `payload cipher produces distinct nonces for different session salts`() {
        val n1 = PayloadCipher.buildNonce("device-1", 1L, salt = 1700000001000L)
        val n2 = PayloadCipher.buildNonce("device-1", 1L, salt = 1700000066000L)
        assertFalse(n1.contentEquals(n2))
    }

    @Test
    fun `payload cipher encrypts and decrypts with session salt and AAD`() {
        val key = PayloadCipher.deriveKey("pack-secret-123")
        val aad = "SM|dev1|1".toByteArray()
        val plaintext = "opus-frame-bytes".toByteArray()
        val salt = 1700000001234L

        val encrypted = PayloadCipher.encrypt(key, "dev1", 42L, aad, plaintext, salt = salt)
        val decrypted = PayloadCipher.decrypt(key, "dev1", 42L, aad, encrypted, salt = salt)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)

        // Wrong salt must fail GCM tag verification
        val wrongSaltDecrypted = PayloadCipher.decrypt(key, "dev1", 42L, aad, encrypted, salt = salt + 1L)
        assertNull(wrongSaltDecrypted)
    }

    @Test
    fun `replay window tracks maxSeq and resets cleanly for restarted peers`() {
        val window = ReplayWindow()
        for (seq in 1L..100L) {
            assertTrue(window.isPacketAcceptable(seq))
        }
        assertEquals(100L, window.getMaxSeq())
        assertFalse(window.isPacketAcceptable(1L))

        window.reset()
        assertEquals(-1L, window.getMaxSeq())
        assertTrue(window.isPacketAcceptable(1L))
    }

    @Test
    fun `peer deduplication by id preserves distinct riders sharing default callsign`() {
        val peers = listOf(
            MeshPeer(id = "dev-aaa", callsign = "Rider 1"),
            MeshPeer(id = "dev-bbb", callsign = "Rider 1"),
            MeshPeer(id = "dev-aaa", callsign = "Rider 1")
        )
        val deduped = peers.distinctBy { it.id }
        assertEquals(2, deduped.size)
        assertEquals(listOf("dev-aaa", "dev-bbb"), deduped.map { it.id })
    }
}
