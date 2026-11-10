package com.meshride.presentation.intercom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshride.domain.model.AudioRoutingState
import com.meshride.domain.model.IntercomMode
import com.meshride.domain.model.Rider
import com.meshride.domain.model.TransmissionState
import com.meshride.domain.repository.BluetoothScoRepository
import com.meshride.domain.repository.IntercomRepository
import com.meshride.domain.usecase.HandlePttPressUseCase
import com.meshride.domain.usecase.SetTransmissionModeUseCase
import com.meshride.domain.usecase.StartIntercomSessionUseCase
import com.meshride.domain.usecase.StopIntercomSessionUseCase
import com.meshride.domain.usecase.ToggleMicrophoneMuteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntercomViewModel @Inject constructor(
    private val intercomRepository: IntercomRepository,
    private val bluetoothScoRepository: BluetoothScoRepository,
    private val setTransmissionModeUseCase: SetTransmissionModeUseCase,
    private val handlePttPressUseCase: HandlePttPressUseCase,
    private val toggleMicrophoneMuteUseCase: ToggleMicrophoneMuteUseCase,
    private val startIntercomSessionUseCase: StartIntercomSessionUseCase,
    private val stopIntercomSessionUseCase: StopIntercomSessionUseCase
) : ViewModel() {

    private val _errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<IntercomUiState> = combine(
        intercomRepository.observeIsSessionActive(),
        intercomRepository.observeIntercomMode(),
        intercomRepository.observeTransmissionState(),
        intercomRepository.observeIsMuted(),
        bluetoothScoRepository.observeAudioRouting(),
        intercomRepository.observeConnectedRiders(),
        _errorFlow
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        IntercomUiState(
            isSessionActive = args[0] as Boolean,
            selectedMode = args[1] as IntercomMode,
            transmissionState = args[2] as TransmissionState,
            isMuted = args[3] as Boolean,
            audioRouting = args[4] as AudioRoutingState,
            riders = args[5] as List<Rider>,
            errorMessage = args[6] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IntercomUiState()
    )

    fun onEvent(event: IntercomUiEvent) {
        viewModelScope.launch {
            when (event) {
                is IntercomUiEvent.ModeChanged -> onModeChanged(event.mode)
                is IntercomUiEvent.PttPressed -> onPttPressed(event.isPressed)
                is IntercomUiEvent.ToggleMuteClicked -> onToggleMute()
                is IntercomUiEvent.ToggleSessionClicked -> onToggleSession()
                is IntercomUiEvent.ToggleBluetoothScoClicked -> onToggleSco()
                is IntercomUiEvent.ClearError -> _errorFlow.value = null
            }
        }
    }

    private suspend fun onModeChanged(mode: IntercomMode) {
        setTransmissionModeUseCase(mode).onFailure { err ->
            _errorFlow.value = "Failed to switch mode: ${err.message}"
        }
    }

    private suspend fun onPttPressed(isPressed: Boolean) {
        handlePttPressUseCase(isPressed).onFailure { err ->
            _errorFlow.value = "PTT error: ${err.message}"
        }
    }

    private suspend fun onToggleMute() {
        toggleMicrophoneMuteUseCase().onFailure { err ->
            _errorFlow.value = "Failed to toggle mute: ${err.message}"
        }
    }

    private suspend fun onToggleSession() {
        val currentState = uiState.value.isSessionActive
        val result = if (currentState) {
            stopIntercomSessionUseCase()
        } else {
            startIntercomSessionUseCase()
        }
        result.onFailure { err ->
            _errorFlow.value = "Session failure: ${err.message}"
        }
    }

    private suspend fun onToggleSco() {
        val isScoConnected = uiState.value.audioRouting.isScoConnected
        val result = if (isScoConnected) {
            bluetoothScoRepository.stopBluetoothSco()
        } else {
            bluetoothScoRepository.startBluetoothSco()
        }
        result.onFailure { err ->
            _errorFlow.value = "Bluetooth SCO failure: ${err.message}"
        }
    }
}
