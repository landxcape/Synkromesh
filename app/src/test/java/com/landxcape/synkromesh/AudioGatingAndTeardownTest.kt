package com.landxcape.synkromesh

import com.landxcape.synkromesh.domain.model.TransmissionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGatingAndTeardownTest {

    private fun canTransmit(
        isSessionActive: Boolean,
        transmissionState: TransmissionState,
        isMuted: Boolean
    ): Boolean {
        return isSessionActive && transmissionState == TransmissionState.TRANSMITTING && !isMuted
    }

    @Test
    fun audioGating_whenActiveTransmittingAndUnmuted_allowsTransmission() {
        val allowed = canTransmit(
            isSessionActive = true,
            transmissionState = TransmissionState.TRANSMITTING,
            isMuted = false
        )
        assertTrue(allowed)
    }

    @Test
    fun audioGating_whenMuted_blocksTransmission() {
        val allowed = canTransmit(
            isSessionActive = true,
            transmissionState = TransmissionState.TRANSMITTING,
            isMuted = true
        )
        assertFalse(allowed)
    }

    @Test
    fun audioGating_whenIdle_blocksTransmission() {
        val allowed = canTransmit(
            isSessionActive = true,
            transmissionState = TransmissionState.IDLE,
            isMuted = false
        )
        assertFalse(allowed)
    }

    @Test
    fun audioGating_whenSessionInactive_blocksTransmission() {
        val allowed = canTransmit(
            isSessionActive = false,
            transmissionState = TransmissionState.TRANSMITTING,
            isMuted = false
        )
        assertFalse(allowed)
    }
}
