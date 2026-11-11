package com.landxcape.synkromesh.data.datasource.mesh.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic token authentication using HMAC-SHA256 for private pack isolation.
 * Computes a truncated 64-bit (16-char hex) signature over packet metadata.
 */
object PackTokenAuthenticator {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Computes an 8-byte HMAC-SHA256 token signature over [data] using [token] as key.
     * Returns a 16-character hexadecimal string.
     */
    fun computeTokenSignature(token: String, data: String): String {
        return try {
            val keySpec = SecretKeySpec(token.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
            val mac = Mac.getInstance(HMAC_ALGORITHM).apply {
                init(keySpec)
            }
            val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            // Take first 8 bytes (64 bits) and format as lowercase hex
            hash.take(8).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Validates whether [candidateSignature] matches the expected signature for [data] and [expectedToken].
     */
    fun isSignatureValid(expectedToken: String?, candidateSignature: String?, data: String): Boolean {
        if (expectedToken.isNullOrBlank()) {
            // Open mesh mode: no token required
            return true
        }
        if (candidateSignature.isNullOrBlank()) {
            // Private pack requires a signature; candidate has none
            return false
        }
        val expected = computeTokenSignature(expectedToken, data)
        return expected.isNotBlank() && expected.equals(candidateSignature, ignoreCase = true)
    }
}
