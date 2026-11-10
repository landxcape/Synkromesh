package com.meshride.presentation.intercom

import com.meshride.domain.model.AudioRoutingState
import com.meshride.domain.model.IntercomMode
import com.meshride.domain.model.MeshPeer
import com.meshride.domain.model.MeshState
import com.meshride.domain.model.Rider
import com.meshride.domain.model.TransmissionState

data class IntercomUiState(
    val isSessionActive: Boolean = false,
    val selectedMode: IntercomMode = IntercomMode.PUSH_TO_TALK,
    val transmissionState: TransmissionState = TransmissionState.IDLE,
    val isMuted: Boolean = false,
    val isLoopbackActive: Boolean = false,
    val micAudioLevel: Float = 0.0f,
    val audioRouting: AudioRoutingState = AudioRoutingState(),
    val meshState: MeshState = MeshState.DISCONNECTED,
    val callsign: String = "Rider 1",
    val isCallsignDialogOpen: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = true,
    val mutedPeerIds: Set<String> = emptySet(),
    val peers: List<MeshPeer> = emptyList(),
    val riders: List<Rider> = emptyList(),
    val errorMessage: String? = null
)

sealed interface IntercomUiEvent {
    data class ModeChanged(val mode: IntercomMode) : IntercomUiEvent
    data class PttPressed(val isPressed: Boolean) : IntercomUiEvent
    data object ToggleMuteClicked : IntercomUiEvent
    data object ToggleSessionClicked : IntercomUiEvent
    data object ToggleBluetoothScoClicked : IntercomUiEvent
    data object ToggleLoopbackClicked : IntercomUiEvent
    data class SetCallsignDialogVisible(val isVisible: Boolean) : IntercomUiEvent
    data class SaveCallsign(val callsign: String) : IntercomUiEvent
    data class TogglePeerMute(val peerId: String) : IntercomUiEvent
    data object RefreshBatteryStatus : IntercomUiEvent
    data object ClearError : IntercomUiEvent
}
