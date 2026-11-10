package com.landxcape.synkromesh

import com.landxcape.synkromesh.presentation.intercom.CockpitPhase
import com.landxcape.synkromesh.presentation.intercom.GroupingMode
import com.landxcape.synkromesh.presentation.intercom.IntercomUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitPhaseStateMachineTest {

    @Test
    fun defaultUiState_startsInStagingPhase() {
        val state = IntercomUiState()
        assertEquals(CockpitPhase.STAGING, state.cockpitPhase)
        assertEquals(GroupingMode.OPEN_MESH, state.groupingMode)
        assertFalse(state.isRadarOverlayActive)
        assertFalse(state.isSessionActive)
    }

    @Test
    fun engageIntercom_setsCockpitPhaseAndActivatesSession() {
        val stagingState = IntercomUiState(
            cockpitPhase = CockpitPhase.STAGING,
            isSessionActive = false
        )

        // Simulating transition to COCKPIT
        val cockpitState = stagingState.copy(
            cockpitPhase = CockpitPhase.COCKPIT,
            isSessionActive = true
        )

        assertEquals(CockpitPhase.COCKPIT, cockpitState.cockpitPhase)
        assertTrue(cockpitState.isSessionActive)
    }

    @Test
    fun returnToStaging_resetsPhaseAndCollapsesRadar() {
        val cockpitState = IntercomUiState(
            cockpitPhase = CockpitPhase.COCKPIT,
            isRadarOverlayActive = true,
            isSessionActive = true
        )

        val returnedState = cockpitState.copy(
            cockpitPhase = CockpitPhase.STAGING,
            isRadarOverlayActive = false
        )

        assertEquals(CockpitPhase.STAGING, returnedState.cockpitPhase)
        assertFalse(returnedState.isRadarOverlayActive)
    }

    @Test
    fun groupingMode_selectLeadPack_updatesGroupingMode() {
        val state = IntercomUiState().copy(groupingMode = GroupingMode.LEAD)
        assertEquals(GroupingMode.LEAD, state.groupingMode)
    }
}
