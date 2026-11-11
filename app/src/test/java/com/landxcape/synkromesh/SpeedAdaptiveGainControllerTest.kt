package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.audio.SpeedAdaptiveGainController
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedAdaptiveGainControllerTest {

    @Test
    fun `test 0 to 20 kmh speed curve provides baseline 1x gain`() {
        assertEquals(1.0f, SpeedAdaptiveGainController.calculateNepaliGain(0f), 0.001f)
        assertEquals(1.0f, SpeedAdaptiveGainController.calculateNepaliGain(10f), 0.001f)
        assertEquals(1.0f, SpeedAdaptiveGainController.calculateNepaliGain(20f), 0.001f)
    }

    @Test
    fun `test 20 to 45 kmh speed curve provides up to 1_4x gain`() {
        val midGain = SpeedAdaptiveGainController.calculateNepaliGain(32.5f)
        assertEquals(1.2f, midGain, 0.01f)

        val topGain = SpeedAdaptiveGainController.calculateNepaliGain(45f)
        assertEquals(1.4f, topGain, 0.001f)
    }

    @Test
    fun `test 45 to 70 kmh speed curve provides up to 2_0x gain`() {
        val midGain = SpeedAdaptiveGainController.calculateNepaliGain(57.5f)
        assertEquals(1.7f, midGain, 0.01f)

        val topGain = SpeedAdaptiveGainController.calculateNepaliGain(70f)
        assertEquals(2.0f, topGain, 0.001f)
    }

    @Test
    fun `test speeds above 70 kmh capped at 2_0x maximum ceiling`() {
        assertEquals(2.0f, SpeedAdaptiveGainController.calculateNepaliGain(85f), 0.001f)
        assertEquals(2.0f, SpeedAdaptiveGainController.calculateNepaliGain(120f), 0.001f)
    }
}
