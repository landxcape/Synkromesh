package com.landxcape.synkromesh

import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.TransmissionState
import org.junit.Assert.assertEquals
import org.junit.Test

class TalkModeContinuityTest {

    private class ModeTransitionHarness {
        var mode: IntercomMode = IntercomMode.PUSH_TO_TALK
        var isSessionActive: Boolean = true
        var isMuted: Boolean = false
        var isToggleLatched: Boolean = false
        var isPttPressed: Boolean = false
        var transmissionState: TransmissionState = TransmissionState.IDLE

        fun handlePttPress(isPressed: Boolean) {
            isPttPressed = isPressed
            when (mode) {
                IntercomMode.PUSH_TO_TALK -> {
                    transmissionState = if (isPressed && !isMuted) TransmissionState.TRANSMITTING else TransmissionState.IDLE
                }
                IntercomMode.TOGGLE -> {
                    if (isPressed) {
                        isToggleLatched = !isToggleLatched
                        transmissionState = if (isToggleLatched && !isMuted) TransmissionState.TRANSMITTING else TransmissionState.IDLE
                    }
                }
                IntercomMode.ALWAYS_ON -> {}
            }
        }

        fun switchMode(newMode: IntercomMode) {
            val previousMode = mode
            mode = newMode

            if (!isSessionActive) {
                transmissionState = TransmissionState.IDLE
                return
            }

            when (newMode) {
                IntercomMode.ALWAYS_ON -> {
                    transmissionState = if (isMuted) TransmissionState.IDLE else TransmissionState.TRANSMITTING
                }
                IntercomMode.TOGGLE -> {
                    if (previousMode == IntercomMode.PUSH_TO_TALK && isPttPressed && !isMuted) {
                        isToggleLatched = true
                        transmissionState = TransmissionState.TRANSMITTING
                    } else {
                        isToggleLatched = false
                        transmissionState = TransmissionState.IDLE
                    }
                }
                IntercomMode.PUSH_TO_TALK -> {
                    isToggleLatched = false
                    transmissionState = if (isPttPressed && !isMuted) {
                        TransmissionState.TRANSMITTING
                    } else {
                        TransmissionState.IDLE
                    }
                }
            }
        }
    }

    @Test
    fun `switching from active PTT hold to TOGGLE maintains transmission latched ON`() {
        val harness = ModeTransitionHarness()
        harness.handlePttPress(isPressed = true)
        assertEquals(TransmissionState.TRANSMITTING, harness.transmissionState)

        harness.switchMode(IntercomMode.TOGGLE)
        assertEquals(TransmissionState.TRANSMITTING, harness.transmissionState)
        assertEquals(true, harness.isToggleLatched)
    }

    @Test
    fun `switching from idle PTT to TOGGLE maintains idle state without open mic leak`() {
        val harness = ModeTransitionHarness()
        assertEquals(TransmissionState.IDLE, harness.transmissionState)

        harness.switchMode(IntercomMode.TOGGLE)
        assertEquals(TransmissionState.IDLE, harness.transmissionState)
        assertEquals(false, harness.isToggleLatched)
    }

    @Test
    fun `switching from active TOGGLE to ALWAYS_ON maintains transmission`() {
        val harness = ModeTransitionHarness()
        harness.switchMode(IntercomMode.TOGGLE)
        harness.handlePttPress(isPressed = true) // Toggles latch ON
        harness.handlePttPress(isPressed = false) // Release tap
        assertEquals(TransmissionState.TRANSMITTING, harness.transmissionState)
        assertEquals(true, harness.isToggleLatched)

        harness.switchMode(IntercomMode.ALWAYS_ON)
        assertEquals(TransmissionState.TRANSMITTING, harness.transmissionState)
    }

    @Test
    fun `switching down from ALWAYS_ON to TOGGLE resets transmission to IDLE`() {
        val harness = ModeTransitionHarness()
        harness.switchMode(IntercomMode.ALWAYS_ON)
        assertEquals(TransmissionState.TRANSMITTING, harness.transmissionState)

        harness.switchMode(IntercomMode.TOGGLE)
        assertEquals(TransmissionState.IDLE, harness.transmissionState)
        assertEquals(false, harness.isToggleLatched)
    }

    @Test
    fun `switching down from ALWAYS_ON to PUSH_TO_TALK resets transmission to IDLE`() {
        val harness = ModeTransitionHarness()
        harness.switchMode(IntercomMode.ALWAYS_ON)
        assertEquals(TransmissionState.TRANSMITTING, harness.transmissionState)

        harness.switchMode(IntercomMode.PUSH_TO_TALK)
        assertEquals(TransmissionState.IDLE, harness.transmissionState)
    }

    @Test
    fun `switching down from active TOGGLE to PUSH_TO_TALK resets transmission to IDLE`() {
        val harness = ModeTransitionHarness()
        harness.switchMode(IntercomMode.TOGGLE)
        harness.handlePttPress(isPressed = true) // Toggles latch ON
        harness.handlePttPress(isPressed = false) // Release tap
        assertEquals(TransmissionState.TRANSMITTING, harness.transmissionState)
        assertEquals(true, harness.isToggleLatched)

        harness.switchMode(IntercomMode.PUSH_TO_TALK)
        assertEquals(TransmissionState.IDLE, harness.transmissionState)
    }
}
