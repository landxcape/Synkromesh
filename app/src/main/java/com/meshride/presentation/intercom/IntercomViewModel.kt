package com.meshride.presentation.intercom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshride.data.datasource.audio.AudioToneFeedbackGenerator
import com.meshride.data.datasource.audio.FeedbackTone
import com.meshride.data.datasource.local.PreferencesDataSource
import com.meshride.domain.model.AudioLevel
import com.meshride.domain.model.AudioRoutingState
import com.meshride.domain.model.IntercomMode
import com.meshride.domain.model.MeshPeer
import com.meshride.domain.model.MeshState
import com.meshride.domain.model.Rider
import com.meshride.domain.model.TransmissionState
import com.meshride.domain.repository.AudioEngineRepository
import com.meshride.domain.repository.BluetoothScoRepository
import com.meshride.domain.repository.IntercomRepository
import com.meshride.domain.repository.MeshNetworkRepository
import com.meshride.domain.usecase.HandlePttPressUseCase
import com.meshride.domain.usecase.ObserveAudioLevelUseCase
import com.meshride.domain.usecase.ObservePeersUseCase
import com.meshride.domain.usecase.SetTransmissionModeUseCase
import com.meshride.domain.usecase.StartIntercomSessionUseCase
import com.meshride.domain.usecase.StopIntercomSessionUseCase
import com.meshride.domain.usecase.ToggleLoopbackUseCase
import com.meshride.domain.usecase.ToggleMicrophoneMuteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntercomViewModel @Inject constructor(
    private val intercomRepository: IntercomRepository,
    private val bluetoothScoRepository: BluetoothScoRepository,
    private val audioEngineRepository: AudioEngineRepository,
    private val meshNetworkRepository: MeshNetworkRepository,
    private val observePeersUseCase: ObservePeersUseCase,
    private val preferencesDataSource: PreferencesDataSource,
    private val setTransmissionModeUseCase: SetTransmissionModeUseCase,
    private val handlePttPressUseCase: HandlePttPressUseCase,
    private val toggleMicrophoneMuteUseCase: ToggleMicrophoneMuteUseCase,
    private val startIntercomSessionUseCase: StartIntercomSessionUseCase,
    private val stopIntercomSessionUseCase: StopIntercomSessionUseCase,
    private val observeAudioLevelUseCase: ObserveAudioLevelUseCase,
    private val toggleLoopbackUseCase: ToggleLoopbackUseCase,
    private val audioToneFeedbackGenerator: AudioToneFeedbackGenerator
) : ViewModel() {

    private val _errorFlow = MutableStateFlow<String?>(null)
    private val _isCallsignDialogOpen = MutableStateFlow(false)
    private val _mutedPeerIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<IntercomUiState> = combine(
        intercomRepository.observeIsSessionActive(),
        intercomRepository.observeIntercomMode(),
        intercomRepository.observeTransmissionState(),
        intercomRepository.observeIsMuted(),
        bluetoothScoRepository.observeAudioRouting(),
        meshNetworkRepository.observeMeshState(),
        observePeersUseCase(),
        audioEngineRepository.observeIsLoopbackActive(),
        observeAudioLevelUseCase(),
        preferencesDataSource.callsignFlow,
        _isCallsignDialogOpen,
        _mutedPeerIds,
        _errorFlow
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val level = args[8] as AudioLevel
        @Suppress("UNCHECKED_CAST")
        val peers = args[6] as List<MeshPeer>
        @Suppress("UNCHECKED_CAST")
        val mutedPeers = args[11] as Set<String>
        val mappedRiders = peers.map { peer ->
            Rider(
                id = peer.id,
                callsign = peer.callsign,
                isLocalUser = peer.isLocal,
                isTransmitting = peer.isTransmitting,
                signalStrengthDbm = peer.signalDbm,
                latencyMs = 15L
            )
        }
        IntercomUiState(
            isSessionActive = args[0] as Boolean,
            selectedMode = args[1] as IntercomMode,
            transmissionState = args[2] as TransmissionState,
            isMuted = args[3] as Boolean,
            audioRouting = args[4] as AudioRoutingState,
            meshState = args[5] as MeshState,
            peers = peers,
            riders = mappedRiders,
            isLoopbackActive = args[7] as Boolean,
            micAudioLevel = level.normalizedLevel,
            callsign = args[9] as String,
            isCallsignDialogOpen = args[10] as Boolean,
            mutedPeerIds = mutedPeers,
            errorMessage = args[12] as String?
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
                is IntercomUiEvent.ToggleLoopbackClicked -> onToggleLoopback()
                is IntercomUiEvent.SetCallsignDialogVisible -> _isCallsignDialogOpen.value = event.isVisible
                is IntercomUiEvent.SaveCallsign -> onSaveCallsign(event.callsign)
                is IntercomUiEvent.TogglePeerMute -> onTogglePeerMute(event.peerId)
                is IntercomUiEvent.ClearError -> _errorFlow.value = null
            }
        }
    }

    private suspend fun onSaveCallsign(newCallsign: String) {
        val trimmed = newCallsign.trim()
        if (trimmed.isNotBlank()) {
            preferencesDataSource.saveCallsign(trimmed).onFailure { err ->
                _errorFlow.value = "Failed to save callsign: ${err.message}"
            }
        }
        _isCallsignDialogOpen.value = false
    }

    private fun onTogglePeerMute(peerId: String) {
        val current = _mutedPeerIds.value
        _mutedPeerIds.value = if (current.contains(peerId)) {
            current - peerId
        } else {
            current + peerId
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
        toggleMicrophoneMuteUseCase().onSuccess { isMuted ->
            val tone = if (isMuted) FeedbackTone.MIC_MUTED else FeedbackTone.MIC_OPEN
            audioToneFeedbackGenerator.playFeedbackTone(tone)
        }.onFailure { err ->
            _errorFlow.value = "Failed to toggle mute: ${err.message}"
        }
    }

    private suspend fun onToggleLoopback() {
        toggleLoopbackUseCase().onFailure { err ->
            _errorFlow.value = "Loopback toggle failed: ${err.message}"
        }
    }

    private suspend fun onToggleSession() {
        val currentState = uiState.value.isSessionActive
        val result = if (currentState) {
            meshNetworkRepository.stopMeshSession()
            audioEngineRepository.stopAudioEngine()
            stopIntercomSessionUseCase()
        } else {
            val callsign = preferencesDataSource.callsignFlow.first()
            startIntercomSessionUseCase().onSuccess {
                audioEngineRepository.startAudioEngine()
                meshNetworkRepository.startMeshSession(callsign)
            }
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
