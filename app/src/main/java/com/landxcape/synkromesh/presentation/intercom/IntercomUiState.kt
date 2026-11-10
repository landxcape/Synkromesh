package com.landxcape.synkromesh.presentation.intercom

import com.landxcape.synkromesh.data.datasource.bluetooth.PairedHeadset
import com.landxcape.synkromesh.domain.model.AudioRoutingState
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.MeshPeer
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.util.PreflightStatus

enum class CockpitPhase {
    STAGING,
    COCKPIT
}

enum class GroupingMode {
    LEAD,
    JOIN,
    OPEN_MESH
}

data class IntercomUiState(
    val cockpitPhase: CockpitPhase = CockpitPhase.STAGING,
    val groupingMode: GroupingMode = GroupingMode.OPEN_MESH,
    val isRadarOverlayActive: Boolean = false,
    val isSessionActive: Boolean = false,
    val selectedMode: IntercomMode = IntercomMode.PUSH_TO_TALK,
    val transmissionState: TransmissionState = TransmissionState.IDLE,
    val isMuted: Boolean = false,
    val isLoopbackActive: Boolean = false,
    val isSpeakerphoneOn: Boolean = true,
    val isHotspotActive: Boolean = false,
    val hotspotSsid: String? = null,
    val hotspotPassword: String? = null,
    val micAudioLevel: Float = 0.0f,
    val audioRouting: AudioRoutingState = AudioRoutingState(),
    val meshState: MeshState = MeshState.DISCONNECTED,
    val callsign: String = "Rider 1",
    val rideChannel: Int = 1,
    val vadThreshold: Float = 0.05f,
    val opusBitrate: Int = 16,
    val isCallsignDialogOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isConnectionSetupOpen: Boolean = false,
    val isPermissionSheetVisible: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = true,
    val preflightStatus: PreflightStatus = PreflightStatus(),
    val pairedHeadsets: List<PairedHeadset> = emptyList(),
    val mutedPeerIds: Set<String> = emptySet(),
    val peers: List<MeshPeer> = emptyList(),
    val riders: List<Rider> = emptyList(),
    val packToken: String? = null,
    val errorMessage: String? = null
)

sealed interface IntercomUiEvent {
    data class ModeChanged(val mode: IntercomMode) : IntercomUiEvent
    data class PttPressed(val isPressed: Boolean) : IntercomUiEvent
    data object ToggleMuteClicked : IntercomUiEvent
    data object ToggleSessionClicked : IntercomUiEvent
    data object ToggleBluetoothScoClicked : IntercomUiEvent
    data object ToggleLoopbackClicked : IntercomUiEvent
    data object ToggleSpeakerphoneClicked : IntercomUiEvent
    data object ToggleHotspotClicked : IntercomUiEvent
    data class SetCallsignDialogVisible(val isVisible: Boolean) : IntercomUiEvent
    data class SaveCallsign(val callsign: String) : IntercomUiEvent
    data class SetSettingsVisible(val isVisible: Boolean) : IntercomUiEvent
    data class SetConnectionSetupVisible(val isVisible: Boolean) : IntercomUiEvent
    data class SetPermissionSheetVisible(val isVisible: Boolean) : IntercomUiEvent
    data class SaveRideChannel(val channel: Int) : IntercomUiEvent
    data class SaveVadThreshold(val threshold: Float) : IntercomUiEvent
    data class SaveOpusBitrate(val bitrate: Int) : IntercomUiEvent
    data class TogglePeerMute(val peerId: String) : IntercomUiEvent
    data object RefreshBatteryStatus : IntercomUiEvent
    data object RefreshPreflightAndDevices : IntercomUiEvent
    data class SelectGroupingMode(val mode: GroupingMode) : IntercomUiEvent
    data class SetPackToken(val token: String?) : IntercomUiEvent
    data class ConnectLeadHotspot(val ssid: String, val password: String? = null) : IntercomUiEvent
    data object EngageIntercom : IntercomUiEvent
    data object ReturnToStaging : IntercomUiEvent
    data object ToggleRadarOverlay : IntercomUiEvent
    data object ClearError : IntercomUiEvent
}
