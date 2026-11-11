package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.audio.SpeedAdaptiveGainController
import com.landxcape.synkromesh.domain.model.HelmetAcousticProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedAdaptiveGainControllerTest {

    @Test
    fun `test 0 to 20 kmh speed curve provides baseline 1x gain`() {
        assertEquals(1.0f, SpeedAdaptiveGainController.calculateSpeedGain(0f), 0.001f)
        assertEquals(1.0f, SpeedAdaptiveGainController.calculateSpeedGain(10f), 0.001f)
        assertEquals(1.0f, SpeedAdaptiveGainController.calculateSpeedGain(20f), 0.001f)
    }

    @Test
    fun `test 20 to 45 kmh speed curve provides up to 1_4x gain`() {
        val midGain = SpeedAdaptiveGainController.calculateSpeedGain(32.5f)
        assertEquals(1.2f, midGain, 0.01f)

        val topGain = SpeedAdaptiveGainController.calculateSpeedGain(45f)
        assertEquals(1.4f, topGain, 0.001f)
    }

    @Test
    fun `test 45 to 70 kmh speed curve provides up to 2_0x gain`() {
        val midGain = SpeedAdaptiveGainController.calculateSpeedGain(57.5f)
        assertEquals(1.7f, midGain, 0.01f)

        val topGain = SpeedAdaptiveGainController.calculateSpeedGain(70f)
        assertEquals(2.0f, topGain, 0.001f)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `test calculateSpeedGain matches calculateNepaliGain`() {
        for (speed in listOf(0f, 15f, 30f, 45f, 60f, 70f, 90f)) {
            assertEquals(
                SpeedAdaptiveGainController.calculateSpeedGain(speed),
                SpeedAdaptiveGainController.calculateNepaliGain(speed),
                0.0001f
            )
        }
    }

    @Test
    fun `test standard flat profile is default and valid`() {
        val flat = HelmetAcousticProfile.STANDARD_FLAT
        assertEquals("Normal / Flat", flat.displayName)
        assertEquals(16, flat.opusBitrateKbps)
        assertTrue(HelmetAcousticProfile.entries.contains(flat))
    }
}
