package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.security.PayloadCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCipherTest {

    private val packToken = "private-ride-secret-alpha"
    private val key = PayloadCipher.deriveKey(packToken)
    private val deviceId = "device-pixel-pro"
    private val seq = 42L
    private val headerAad = "MAGIC|TYPE|42|TIMESTAMP|SENDER".toByteArray(Charsets.UTF_8)
    private val sampleOpusPayload = byteArrayOf(
        0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0x12, 0x34, 0x56, 0x78,
        0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte(), 0x22
    )

    @Test
    fun `encrypt and decrypt returns bit-exact original plaintext`() {
        val ciphertext = PayloadCipher.encrypt(key, deviceId, seq, headerAad, sampleOpusPayload)
        assertNotNull(ciphertext)
        assertFalse(sampleOpusPayload.contentEquals(ciphertext))
        // AES-GCM adds a 16-byte authentication tag
        assertEquals(sampleOpusPayload.size + 16, ciphertext.size)

        val decrypted = PayloadCipher.decrypt(key, deviceId, seq, headerAad, ciphertext)
        assertNotNull(decrypted)
        assertArrayEquals(sampleOpusPayload, decrypted)
    }

    @Test
    fun `decryption fails with wrong key from different pack token`() {
        val wrongKey = PayloadCipher.deriveKey("different-unauthorized-token")
        val ciphertext = PayloadCipher.encrypt(key, deviceId, seq, headerAad, sampleOpusPayload)

        val decrypted = PayloadCipher.decrypt(wrongKey, deviceId, seq, headerAad, ciphertext)
        assertNull("Decryption with wrong key must fail and return null", decrypted)
    }

    @Test
    fun `decryption fails if ciphertext byte is tampered`() {
        val ciphertext = PayloadCipher.encrypt(key, deviceId, seq, headerAad, sampleOpusPayload)
        // Flip one bit in ciphertext
        val tamperedCiphertext = ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()

        val decrypted = PayloadCipher.decrypt(key, deviceId, seq, headerAad, tamperedCiphertext)
        assertNull("Decryption of tampered ciphertext must fail GCM tag check", decrypted)
    }

    @Test
    fun `decryption fails if AAD header is tampered`() {
        val ciphertext = PayloadCipher.encrypt(key, deviceId, seq, headerAad, sampleOpusPayload)
        val tamperedAad = "MAGIC|TYPE|999|TIMESTAMP|SENDER".toByteArray(Charsets.UTF_8) // Changed seq in AAD

        val decrypted = PayloadCipher.decrypt(key, deviceId, seq, tamperedAad, ciphertext)
        assertNull("Decryption with mismatched AAD header must fail", decrypted)
    }

    @Test
    fun `decryption fails if sequence number in nonce differs`() {
        val ciphertext = PayloadCipher.encrypt(key, deviceId, seq, headerAad, sampleOpusPayload)
        val wrongSeq = seq + 1

        val decrypted = PayloadCipher.decrypt(key, deviceId, wrongSeq, headerAad, ciphertext)
        assertNull("Decryption with different sequence number nonce must fail", decrypted)
    }

    @Test
    fun `nonces are unique across sequence numbers and devices`() {
        val nonce1 = PayloadCipher.buildNonce("device1", 1L)
        val nonce2 = PayloadCipher.buildNonce("device1", 2L)
        val nonce3 = PayloadCipher.buildNonce("device2", 1L)

        assertFalse(nonce1.contentEquals(nonce2))
        assertFalse(nonce1.contentEquals(nonce3))
        assertEquals(12, nonce1.size)
    }
}
