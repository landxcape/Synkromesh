package com.landxcape.synkromesh.data.repository

import com.landxcape.synkromesh.data.datasource.local.PreferencesDataSource
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.domain.repository.IntercomRepository
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

    private var isToggleLatched = false

    override fun observeTransmissionState(): Flow<TransmissionState> = _transmissionState.asStateFlow()
    override fun observeIntercomMode(): Flow<IntercomMode> = _intercomMode.asStateFlow()
    override fun observeConnectedRiders(): Flow<List<Rider>> = _connectedRiders.asStateFlow()
    override fun observeIsMuted(): Flow<Boolean> = _isMuted.asStateFlow()
    override fun observeIsSessionActive(): Flow<Boolean> = _isSessionActive.asStateFlow()

    override suspend fun setIntercomMode(mode: IntercomMode): Result<Unit> = runCatching {
        val wasTransmitting = _transmissionState.value == TransmissionState.TRANSMITTING
        _intercomMode.value = mode
        preferencesDataSource.saveDefaultMode(mode)

        if (!_isSessionActive.value) {
            _transmissionState.value = TransmissionState.IDLE
            return@runCatching
        }

        when (mode) {
            IntercomMode.ALWAYS_ON -> {
                _transmissionState.value = if (_isMuted.value) TransmissionState.IDLE else TransmissionState.TRANSMITTING
            }
            IntercomMode.TOGGLE -> {
                // Preserves active transmission: if user was talking (PTT hold or open), latch it ON.
                // If user was idle, keep it IDLE to avoid uncommanded mic leaks.
                if (wasTransmitting && !_isMuted.value) {
                    isToggleLatched = true
                    _transmissionState.value = TransmissionState.TRANSMITTING
                } else {
                    isToggleLatched = false
                    _transmissionState.value = TransmissionState.IDLE
                }
            }
            IntercomMode.PUSH_TO_TALK -> {
                // If already transmitting when switching to PTT, preserve transmission continuity
                if (wasTransmitting && !_isMuted.value) {
                    _transmissionState.value = TransmissionState.TRANSMITTING
                } else {
                    _transmissionState.value = TransmissionState.IDLE
                }
            }
        }
    }

    override suspend fun setMicrophoneMute(muted: Boolean): Result<Unit> = runCatching {
        _isMuted.value = muted
        updateTransmissionState()
    }

    override suspend fun toggleMicrophoneMute(): Result<Boolean> = runCatching {
        if (_intercomMode.value == IntercomMode.TOGGLE) {
            isToggleLatched = !isToggleLatched
            _transmissionState.value = if (isToggleLatched && !_isMuted.value) TransmissionState.TRANSMITTING else TransmissionState.IDLE
            !isToggleLatched
        } else {
            val next = !_isMuted.value
            _isMuted.value = next
            updateTransmissionState()
            next
        }
    }

    override suspend fun handlePttPress(isPressed: Boolean): Result<Unit> = runCatching {
        when (_intercomMode.value) {
            IntercomMode.PUSH_TO_TALK -> {
                _transmissionState.value = if (isPressed && !_isMuted.value) TransmissionState.TRANSMITTING else TransmissionState.IDLE
            }
            IntercomMode.TOGGLE -> {
                if (isPressed) {
                    isToggleLatched = !isToggleLatched
                    _transmissionState.value = if (isToggleLatched && !_isMuted.value) TransmissionState.TRANSMITTING else TransmissionState.IDLE
                }
            }
            IntercomMode.ALWAYS_ON -> {
                // Always-on remains active unless muted
            }
        }
    }

    override suspend fun startIntercomSession(): Result<Unit> = runCatching {
        _isSessionActive.value = true
        updateTransmissionState()
    }

    override suspend fun stopIntercomSession(): Result<Unit> = runCatching {
        _isSessionActive.value = false
        isToggleLatched = false
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
                _transmissionState.value = if (isToggleLatched && !_isMuted.value) TransmissionState.TRANSMITTING else TransmissionState.IDLE
            }
            IntercomMode.PUSH_TO_TALK -> {
                // Driven directly by handlePttPress
            }
        }
    }
}
