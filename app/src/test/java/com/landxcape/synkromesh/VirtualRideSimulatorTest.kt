package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.mesh.simulator.VirtualRideSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualRideSimulatorTest {

    @Test
    fun testStartAndStopSimulation() {
        val simulator = VirtualRideSimulator()
        assertFalse(simulator.isSimulating.value)
        assertEquals(0, simulator.simulatedRiders.value.size)

        simulator.startSimulation()
        assertTrue(simulator.isSimulating.value)

        // Wait up to 2 seconds for initial emission on Default dispatcher
        var riders = simulator.simulatedRiders.value
        val startTime = System.currentTimeMillis()
        while (riders.isEmpty() && System.currentTimeMillis() - startTime < 2000) {
            Thread.sleep(50)
            riders = simulator.simulatedRiders.value
        }

        assertEquals(3, riders.size)

        val apex = riders.first { it.id == "sim_apex_lead" }
        val mid = riders.first { it.id == "sim_mid_relay" }
        val sweeper = riders.first { it.id == "sim_sweeper_tail" }

        assertEquals(1, apex.hopCount)
        assertEquals(1, mid.hopCount)
        assertEquals(2, sweeper.hopCount)
        assertTrue((apex.estimatedDistanceMeters ?: 0f) in 40f..60f)
        assertTrue((sweeper.estimatedDistanceMeters ?: 0f) > 300f)

        simulator.stopSimulation()
        assertFalse(simulator.isSimulating.value)
        assertEquals(0, simulator.simulatedRiders.value.size)
    }

    @Test
    fun testToggleSimulation() {
        val simulator = VirtualRideSimulator()
        assertFalse(simulator.isSimulating.value)

        simulator.toggleSimulation()
        assertTrue(simulator.isSimulating.value)

        simulator.toggleSimulation()
        assertFalse(simulator.isSimulating.value)
    }
}
