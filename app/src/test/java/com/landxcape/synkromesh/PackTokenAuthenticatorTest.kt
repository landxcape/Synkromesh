package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.security.PackTokenAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackTokenAuthenticatorTest {

    private val testToken = "test-pack-token-12345"
    private val packetData = "device-abc|channel-1"

    @Test
    fun `computeTokenSignature produces deterministic 16-char hex string`() {
        val sig1 = PackTokenAuthenticator.computeTokenSignature(testToken, packetData)
        val sig2 = PackTokenAuthenticator.computeTokenSignature(testToken, packetData)
        assertEquals(16, sig1.length)
        assertEquals(sig1, sig2)
    }

    @Test
    fun `different token produces different signature`() {
        val sig1 = PackTokenAuthenticator.computeTokenSignature("token-a", packetData)
        val sig2 = PackTokenAuthenticator.computeTokenSignature("token-b", packetData)
        assertFalse(sig1 == sig2)
    }

    @Test
    fun `valid signature passes authentication`() {
        val sig = PackTokenAuthenticator.computeTokenSignature(testToken, packetData)
        assertTrue(PackTokenAuthenticator.isSignatureValid(testToken, sig, packetData))
    }

    @Test
    fun `missing signature in private pack is rejected`() {
        // Closed pack expects a token; rogue sender supplies null or blank
        assertFalse(PackTokenAuthenticator.isSignatureValid(testToken, null, packetData))
        assertFalse(PackTokenAuthenticator.isSignatureValid(testToken, "", packetData))
    }

    @Test
    fun `tampered signature in private pack is rejected`() {
        val tamperedSig = "0123456789abcdef"
        assertFalse(PackTokenAuthenticator.isSignatureValid(testToken, tamperedSig, packetData))
    }

    @Test
    fun `open mesh mode without token accepts any packet`() {
        // Open mesh has no expectedToken
        assertTrue(PackTokenAuthenticator.isSignatureValid(null, null, packetData))
        assertTrue(PackTokenAuthenticator.isSignatureValid("", null, packetData))
        assertTrue(PackTokenAuthenticator.isSignatureValid(null, "some-sig", packetData))
    }
}
