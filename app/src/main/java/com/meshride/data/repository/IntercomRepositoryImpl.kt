package com.meshride.data.repository

import com.meshride.data.datasource.local.PreferencesDataSource
import com.meshride.domain.model.IntercomMode
import com.meshride.domain.model.Rider
import com.meshride.domain.model.TransmissionState
import com.meshride.domain.repository.IntercomRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntercomRepositoryImpl @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource
) : IntercomRepository {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _transmissionState = MutableStateFlow(TransmissionState.IDLE)
    private val _intercomMode = MutableStateFlow(IntercomMode.PUSH_TO_TALK)
    private val _isMuted = MutableStateFlow(false)
    private val _isSessionActive = MutableStateFlow(false)
    private val _connectedRiders = MutableStateFlow<List<Rider>>(
        listOf(
            Rider(id = "local", callsign = "Rider 1", isLocalUser = true),
            Rider(id = "peer_1", callsign = "Apex Lead", isTransmitting = false, signalStrengthDbm = -55),
            Rider(id = "peer_2", callsign = "Sweeper", isTransmitting = false, signalStrengthDbm = -68)
        )
    )

    init {
        scope.launch {
            preferencesDataSource.defaultModeFlow.collect { savedMode ->
                _intercomMode.value = savedMode
            }
        }
    }

    override fun observeTransmissionState(): Flow<TransmissionState> = _transmissionState.asStateFlow()
    override fun observeIntercomMode(): Flow<IntercomMode> = _intercomMode.asStateFlow()
    override fun observeConnectedRiders(): Flow<List<Rider>> = _connectedRiders.asStateFlow()
    override fun observeIsMuted(): Flow<Boolean> = _isMuted.asStateFlow()
    override fun observeIsSessionActive(): Flow<Boolean> = _isSessionActive.asStateFlow()

    override suspend fun setIntercomMode(mode: IntercomMode): Result<Unit> = runCatching {
        _intercomMode.value = mode
        preferencesDataSource.saveDefaultMode(mode)
        updateTransmissionState()
    }

    override suspend fun setMicrophoneMute(muted: Boolean): Result<Unit> = runCatching {
        _isMuted.value = muted
        updateTransmissionState()
    }

    override suspend fun toggleMicrophoneMute(): Result<Boolean> = runCatching {
        val next = !_isMuted.value
        _isMuted.value = next
        updateTransmissionState()
        next
    }

    override suspend fun handlePttPress(isPressed: Boolean): Result<Unit> = runCatching {
        if (_intercomMode.value == IntercomMode.PUSH_TO_TALK) {
            _transmissionState.value = if (isPressed) TransmissionState.TRANSMITTING else TransmissionState.IDLE
        }
    }

    override suspend fun startIntercomSession(): Result<Unit> = runCatching {
        _isSessionActive.value = true
        updateTransmissionState()
    }

    override suspend fun stopIntercomSession(): Result<Unit> = runCatching {
        _isSessionActive.value = false
        _transmissionState.value = TransmissionState.IDLE
    }

    private fun updateTransmissionState() {
        if (!_isSessionActive.value) {
            _transmissionState.value = TransmissionState.IDLE
            return
        }
        when (_intercomMode.value) {
            IntercomMode.ALWAYS_ON -> {
                _transmissionState.value = if (_isMuted.value) TransmissionState.IDLE else TransmissionState.TRANSMITTING
            }
            IntercomMode.TOGGLE -> {
                _transmissionState.value = if (_isMuted.value) TransmissionState.IDLE else TransmissionState.TRANSMITTING
            }
            IntercomMode.PUSH_TO_TALK -> {
                // Driven directly by handlePttPress
            }
        }
    }
}
