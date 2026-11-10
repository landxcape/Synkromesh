package com.meshride.presentation.intercom

import com.meshride.domain.model.AudioRoutingState
import com.meshride.domain.model.IntercomMode
import com.meshride.domain.model.Rider
import com.meshride.domain.model.TransmissionState

data class IntercomUiState(
    val isSessionActive: Boolean = false,
    val selectedMode: IntercomMode = IntercomMode.PUSH_TO_TALK,
    val transmissionState: TransmissionState = TransmissionState.IDLE,
    val isMuted: Boolean = false,
    val audioRouting: AudioRoutingState = AudioRoutingState(),
    val riders: List<Rider> = emptyList(),
    val errorMessage: String? = null
)

sealed interface IntercomUiEvent {
    data class ModeChanged(val mode: IntercomMode) : IntercomUiEvent
    data class PttPressed(val isPressed: Boolean) : IntercomUiEvent
    data object ToggleMuteClicked : IntercomUiEvent
    data object ToggleSessionClicked : IntercomUiEvent
    data object ToggleBluetoothScoClicked : IntercomUiEvent
    data object ClearError : IntercomUiEvent
}
