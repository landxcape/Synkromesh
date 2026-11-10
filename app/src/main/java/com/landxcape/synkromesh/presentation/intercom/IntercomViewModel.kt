package com.landxcape.synkromesh.presentation.intercom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.landxcape.synkromesh.data.datasource.audio.AudioToneFeedbackGenerator
import com.landxcape.synkromesh.data.datasource.audio.FeedbackTone
import com.landxcape.synkromesh.data.datasource.bluetooth.BluetoothHeadsetDeviceManager
import com.landxcape.synkromesh.data.datasource.bluetooth.PairedHeadset
import com.landxcape.synkromesh.data.datasource.local.PreferencesDataSource
import com.landxcape.synkromesh.data.datasource.mesh.LocationTelemetryDataSource
import com.landxcape.synkromesh.domain.model.AudioLevel
import com.landxcape.synkromesh.domain.model.AudioRoutingState
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.MeshPeer
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import com.landxcape.synkromesh.domain.repository.BluetoothScoRepository
import com.landxcape.synkromesh.domain.repository.IntercomRepository
import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import com.landxcape.synkromesh.domain.usecase.HandlePttPressUseCase
import com.landxcape.synkromesh.domain.usecase.ObserveAudioLevelUseCase
import com.landxcape.synkromesh.domain.usecase.ObservePeersUseCase
import com.landxcape.synkromesh.domain.usecase.SetTransmissionModeUseCase
import com.landxcape.synkromesh.domain.usecase.StartIntercomSessionUseCase
import com.landxcape.synkromesh.domain.usecase.StopIntercomSessionUseCase
import com.landxcape.synkromesh.domain.usecase.ToggleLoopbackUseCase
import com.landxcape.synkromesh.domain.usecase.ToggleMicrophoneMuteUseCase
import com.landxcape.synkromesh.util.BatteryOptimizationManager
import com.landxcape.synkromesh.util.HardwarePreflightManager
import com.landxcape.synkromesh.util.PreflightStatus
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
    private val batteryOptimizationManager: BatteryOptimizationManager,
    private val preflightManager: HardwarePreflightManager,
    private val bluetoothDeviceManager: BluetoothHeadsetDeviceManager,
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
    private val _cockpitPhase = MutableStateFlow(CockpitPhase.STAGING)
    private val _groupingMode = MutableStateFlow(GroupingMode.OPEN_MESH)
    private val _isRadarOverlayActive = MutableStateFlow(false)
    private val _isCallsignDialogOpen = MutableStateFlow(false)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _isConnectionSetupOpen = MutableStateFlow(false)
    private val _isPermissionSheetVisible = MutableStateFlow(false)
    private val _mutedPeerIds = MutableStateFlow<Set<String>>(emptySet())
    private val _packToken = MutableStateFlow<String?>(null)

    init {
        refreshPreflightAndDevices()
    }

    private val baseAudioMeshFlow = combine(
        intercomRepository.observeIsSessionActive(),
        intercomRepository.observeIntercomMode(),
        intercomRepository.observeTransmissionState(),
        intercomRepository.observeIsMuted(),
        bluetoothScoRepository.observeAudioRouting(),
        meshNetworkRepository.observeMeshState(),
        observePeersUseCase(),
        audioEngineRepository.observeIsLoopbackActive(),
        observeAudioLevelUseCase(),
        audioEngineRepository.observeIsSpeakerphoneOn()
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        BaseAudioMeshState(
            isSessionActive = args[0] as Boolean,
            selectedMode = args[1] as IntercomMode,
            transmissionState = args[2] as TransmissionState,
            isMuted = args[3] as Boolean,
            audioRouting = args[4] as AudioRoutingState,
            meshState = args[5] as MeshState,
            peers = args[6] as List<MeshPeer>,
            isLoopbackActive = args[7] as Boolean,
            micAudioLevel = (args[8] as AudioLevel).normalizedLevel,
            isSpeakerphoneOn = args[9] as Boolean
        )
    }

    private val preferencesFlow = combine(
        preferencesDataSource.callsignFlow,
        preferencesDataSource.rideChannelFlow,
        preferencesDataSource.vadThresholdFlow,
        preferencesDataSource.opusBitrateFlow
    ) { callsign, channel, vad, bitrate ->
        UserPreferencesState(callsign, channel, vad, bitrate)
    }

    private val hotspotFlow = combine(
        meshNetworkRepository.observeIsHotspotActive(),
        meshNetworkRepository.observeHotspotSsid(),
        meshNetworkRepository.observeHotspotPassword()
    ) { active, ssid, pass ->
        Triple(active, ssid, pass)
    }

    private val dialogsAndPreflightFlow = combine(
        _isCallsignDialogOpen,
        _isSettingsOpen,
        _isConnectionSetupOpen,
        _isPermissionSheetVisible,
        _mutedPeerIds,
        _errorFlow,
        preflightManager.statusFlow,
        bluetoothDeviceManager.devicesFlow,
        _cockpitPhase,
        _groupingMode,
        _isRadarOverlayActive,
        _packToken
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        DialogsAndPreflightState(
            isCallsignDialogOpen = args[0] as Boolean,
            isSettingsOpen = args[1] as Boolean,
            isConnectionSetupOpen = args[2] as Boolean,
            isPermissionSheetVisible = args[3] as Boolean,
            mutedPeerIds = args[4] as Set<String>,
            errorMessage = args[5] as String?,
            preflightStatus = args[6] as PreflightStatus,
            pairedHeadsets = args[7] as List<PairedHeadset>,
            cockpitPhase = args[8] as CockpitPhase,
            groupingMode = args[9] as GroupingMode,
            isRadarOverlayActive = args[10] as Boolean,
            packToken = args[11] as String?
        )
    }

    val uiState: StateFlow<IntercomUiState> = combine(
        baseAudioMeshFlow,
        preferencesFlow,
        dialogsAndPreflightFlow,
        hotspotFlow
    ) { audioMesh, prefs, dialogs, hotspot ->
        val mappedRiders = audioMesh.peers.map { peer ->
            val relLabel = peer.relativeBearingDegrees?.let {
                LocationTelemetryDataSource.getRelativePositionLabel(it)
            } ?: if (peer.latitude != null) "GPS Locked" else "Direct Mesh"

            Rider(
                id = peer.id,
                callsign = peer.callsign,
                isLocalUser = peer.isLocal,
                isTransmitting = peer.isTransmitting,
                signalStrengthDbm = peer.signalDbm,
                latencyMs = 15L,
                estimatedDistanceMeters = peer.estimatedDistanceMeters,
                relativePosition = relLabel,
                relativeBearingDegrees = peer.relativeBearingDegrees,
                altitudeMeters = peer.altitudeMeters,
                speedKmh = peer.speedKmh,
                batteryPercent = peer.batteryPercent
            )
        }

        IntercomUiState(
            cockpitPhase = dialogs.cockpitPhase,
            groupingMode = dialogs.groupingMode,
            isRadarOverlayActive = dialogs.isRadarOverlayActive,
            isSessionActive = audioMesh.isSessionActive,
            selectedMode = audioMesh.selectedMode,
            transmissionState = audioMesh.transmissionState,
            isMuted = audioMesh.isMuted,
            isLoopbackActive = audioMesh.isLoopbackActive,
            isSpeakerphoneOn = audioMesh.isSpeakerphoneOn,
            isHotspotActive = hotspot.first,
            hotspotSsid = hotspot.second,
            hotspotPassword = hotspot.third,
            micAudioLevel = audioMesh.micAudioLevel,
            audioRouting = audioMesh.audioRouting,
            meshState = audioMesh.meshState,
            callsign = prefs.callsign,
            rideChannel = prefs.rideChannel,
            vadThreshold = prefs.vadThreshold,
            opusBitrate = prefs.opusBitrate,
            isCallsignDialogOpen = dialogs.isCallsignDialogOpen,
            isSettingsOpen = dialogs.isSettingsOpen,
            isConnectionSetupOpen = dialogs.isConnectionSetupOpen,
            isPermissionSheetVisible = dialogs.isPermissionSheetVisible,
            isBatteryOptimizationIgnored = dialogs.preflightStatus.isBatteryOptimizationIgnored,
            preflightStatus = dialogs.preflightStatus,
            pairedHeadsets = dialogs.pairedHeadsets,
            mutedPeerIds = dialogs.mutedPeerIds,
            peers = audioMesh.peers,
            riders = mappedRiders,
            packToken = dialogs.packToken,
            errorMessage = dialogs.errorMessage
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
                is IntercomUiEvent.ToggleSpeakerphoneClicked -> onToggleSpeakerphone()
                is IntercomUiEvent.ToggleHotspotClicked -> onToggleHotspot()
                is IntercomUiEvent.SetCallsignDialogVisible -> _isCallsignDialogOpen.value = event.isVisible
                is IntercomUiEvent.SaveCallsign -> onSaveCallsign(event.callsign)
                is IntercomUiEvent.SetSettingsVisible -> _isSettingsOpen.value = event.isVisible
                is IntercomUiEvent.SetConnectionSetupVisible -> _isConnectionSetupOpen.value = event.isVisible
                is IntercomUiEvent.SetPermissionSheetVisible -> _isPermissionSheetVisible.value = event.isVisible
                is IntercomUiEvent.SaveRideChannel -> onSaveChannel(event.channel)
                is IntercomUiEvent.SaveVadThreshold -> onSaveVadThreshold(event.threshold)
                is IntercomUiEvent.SaveOpusBitrate -> onSaveOpusBitrate(event.bitrate)
                is IntercomUiEvent.TogglePeerMute -> onTogglePeerMute(event.peerId)
                is IntercomUiEvent.RefreshBatteryStatus -> refreshPreflightAndDevices()
                is IntercomUiEvent.RefreshPreflightAndDevices -> refreshPreflightAndDevices()
                is IntercomUiEvent.SelectGroupingMode -> onSelectGroupingMode(event.mode)
                is IntercomUiEvent.SetPackToken -> onSetPackToken(event.token)
                is IntercomUiEvent.EngageIntercom -> onEngageIntercom()
                is IntercomUiEvent.ReturnToStaging -> onReturnToStaging()
                is IntercomUiEvent.ToggleRadarOverlay -> _isRadarOverlayActive.value = !_isRadarOverlayActive.value
                is IntercomUiEvent.ClearError -> _errorFlow.value = null
            }
        }
    }

    fun getRequiredPermissions(): List<String> = preflightManager.getRequiredPermissions()

    private fun refreshPreflightAndDevices() {
        preflightManager.refresh()
        bluetoothDeviceManager.refreshPairedDevices()
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

    private suspend fun onSaveChannel(channel: Int) {
        preferencesDataSource.saveRideChannel(channel)
        if (uiState.value.isSessionActive) {
            val callsign = preferencesDataSource.callsignFlow.first()
            meshNetworkRepository.stopMeshSession()
            meshNetworkRepository.startMeshSession(callsign, channel)
        }
    }

    private suspend fun onSaveVadThreshold(threshold: Float) {
        preferencesDataSource.saveVadThreshold(threshold)
    }

    private suspend fun onSaveOpusBitrate(bitrate: Int) {
        preferencesDataSource.saveOpusBitrate(bitrate)
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

    private suspend fun onToggleSpeakerphone() {
        audioEngineRepository.toggleSpeakerphone().onFailure { err ->
            _errorFlow.value = "Loudspeaker toggle failed: ${err.message}"
        }
    }

    private suspend fun onToggleHotspot() {
        meshNetworkRepository.toggleLocalHotspot().onFailure { err ->
            _errorFlow.value = "Hotspot error: ${err.message}"
        }
    }

    private suspend fun onSelectGroupingMode(mode: GroupingMode) {
        _groupingMode.value = mode
        if (mode == GroupingMode.LEAD) {
            val isHotspotActive = meshNetworkRepository.observeIsHotspotActive().first()
            if (!isHotspotActive) {
                meshNetworkRepository.toggleLocalHotspot().onFailure { err ->
                    _errorFlow.value = "Failed to start Lead Hotspot: ${err.message}"
                }
            }
            if (_packToken.value == null) {
                _packToken.value = java.util.UUID.randomUUID().toString().take(12)
            }
            meshNetworkRepository.setPackToken(_packToken.value)
        } else if (mode == GroupingMode.OPEN_MESH) {
            meshNetworkRepository.setPackToken(null)
        }
    }

    private fun onSetPackToken(token: String?) {
        _packToken.value = token
        meshNetworkRepository.setPackToken(token)
    }

    private suspend fun onEngageIntercom() {
        _cockpitPhase.value = CockpitPhase.COCKPIT
        val isSessionActive = intercomRepository.observeIsSessionActive().first()
        if (!isSessionActive) {
            preflightManager.refresh()
            val status = preflightManager.statusFlow.value
            if (!status.areCriticalPermissionsGranted || !status.areRadiosEnabled) {
                _isPermissionSheetVisible.value = true
                return
            }

            val callsign = preferencesDataSource.callsignFlow.first()
            val channel = preferencesDataSource.rideChannelFlow.first()
            startIntercomSessionUseCase().onSuccess {
                audioEngineRepository.startAudioEngine()
                meshNetworkRepository.startMeshSession(callsign, channel)
            }.onFailure { err ->
                _errorFlow.value = "Failed to engage intercom: ${err.message}"
            }
        }
        audioToneFeedbackGenerator.playFeedbackTone(FeedbackTone.MIC_OPEN)
    }

    private fun onReturnToStaging() {
        _cockpitPhase.value = CockpitPhase.STAGING
        _isRadarOverlayActive.value = false
    }

    private suspend fun onToggleSession() {
        val currentState = uiState.value.isSessionActive
        if (currentState) {
            meshNetworkRepository.stopMeshSession()
            audioEngineRepository.stopAudioEngine()
            stopIntercomSessionUseCase()
        } else {
            preflightManager.refresh()
            val status = preflightManager.statusFlow.value
            if (!status.areCriticalPermissionsGranted || !status.areRadiosEnabled) {
                _isPermissionSheetVisible.value = true
                return
            }

            val callsign = preferencesDataSource.callsignFlow.first()
            val channel = preferencesDataSource.rideChannelFlow.first()
            startIntercomSessionUseCase().onSuccess {
                audioEngineRepository.startAudioEngine()
                meshNetworkRepository.startMeshSession(callsign, channel)
            }.onFailure { err ->
                _errorFlow.value = "Session failure: ${err.message}"
            }
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
            _errorFlow.value = if (err.message?.contains("NO_BLUETOOTH_HEADSET_CONNECTED") == true) {
                "NO_BT_HEADSET: No Bluetooth helmet headset detected. Pair in Android Settings."
            } else {
                "Bluetooth SCO: ${err.message}"
            }
        }
    }

    private data class BaseAudioMeshState(
        val isSessionActive: Boolean,
        val selectedMode: IntercomMode,
        val transmissionState: TransmissionState,
        val isMuted: Boolean,
        val audioRouting: AudioRoutingState,
        val meshState: MeshState,
        val peers: List<MeshPeer>,
        val isLoopbackActive: Boolean,
        val micAudioLevel: Float,
        val isSpeakerphoneOn: Boolean
    )

    private data class UserPreferencesState(
        val callsign: String,
        val rideChannel: Int,
        val vadThreshold: Float,
        val opusBitrate: Int
    )

    private data class DialogsAndPreflightState(
        val isCallsignDialogOpen: Boolean,
        val isSettingsOpen: Boolean,
        val isConnectionSetupOpen: Boolean,
        val isPermissionSheetVisible: Boolean,
        val mutedPeerIds: Set<String>,
        val errorMessage: String?,
        val preflightStatus: PreflightStatus,
        val pairedHeadsets: List<PairedHeadset>,
        val cockpitPhase: CockpitPhase,
        val groupingMode: GroupingMode,
        val isRadarOverlayActive: Boolean,
        val packToken: String?
    )
}
