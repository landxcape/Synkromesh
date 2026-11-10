package com.meshride.presentation.intercom

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshride.domain.model.AudioRoutingState
import com.meshride.domain.model.IntercomMode
import com.meshride.domain.model.Rider
import com.meshride.domain.model.TransmissionState
import com.meshride.presentation.theme.AccentAmber
import com.meshride.presentation.theme.AccentCyan
import com.meshride.presentation.theme.AccentGreen
import com.meshride.presentation.theme.AccentRed
import com.meshride.presentation.theme.DarkBackground
import com.meshride.presentation.theme.DarkSurface
import com.meshride.presentation.theme.DarkSurfaceElevated
import com.meshride.presentation.theme.TextMuted
import com.meshride.presentation.theme.TextPrimary
import com.meshride.presentation.theme.TextSecondary

/**
 * Smart Parent Composable: Owns state collection and propagates data/callbacks downward.
 */
@Composable
fun IntercomRoute(
    modifier: Modifier = Modifier,
    viewModel: IntercomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(IntercomUiEvent.ClearError)
        }
    }

    IntercomScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Pragmatic Layout: Orchestrates stateless child components.
 */
@Composable
fun IntercomScreen(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            IntercomTopBar(
                isSessionActive = uiState.isSessionActive,
                audioRouting = uiState.audioRouting,
                isMuted = uiState.isMuted,
                isLoopbackActive = uiState.isLoopbackActive,
                onToggleSession = { onEvent(IntercomUiEvent.ToggleSessionClicked) },
                onToggleSco = { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) },
                onToggleMute = { onEvent(IntercomUiEvent.ToggleMuteClicked) },
                onToggleLoopback = { onEvent(IntercomUiEvent.ToggleLoopbackClicked) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                TransmissionBanner(
                    state = uiState.transmissionState,
                    isLoopbackActive = uiState.isLoopbackActive
                )
                Spacer(modifier = Modifier.height(16.dp))
                ModeSelectorGroup(
                    selectedMode = uiState.selectedMode,
                    onModeSelected = { onEvent(IntercomUiEvent.ModeChanged(it)) }
                )
            }

            // Central Touch Target (Glove-Friendly PTT with Voice Activity Meter)
            PttControlTarget(
                mode = uiState.selectedMode,
                transmissionState = uiState.transmissionState,
                isSessionActive = uiState.isSessionActive,
                micAudioLevel = uiState.micAudioLevel,
                onPttChange = { pressed -> onEvent(IntercomUiEvent.PttPressed(pressed)) },
                onToggleClick = { onEvent(IntercomUiEvent.ToggleMuteClicked) }
            )

            // Connected Riders Mesh
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "MESH PEERS (${uiState.riders.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                RiderMeshList(riders = uiState.riders)
            }
        }
    }
}

// ---------------- Stateless Child Components ----------------

@Composable
private fun IntercomTopBar(
    isSessionActive: Boolean,
    audioRouting: AudioRoutingState,
    isMuted: Boolean,
    isLoopbackActive: Boolean,
    onToggleSession: () -> Unit,
    onToggleSco: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLoopback: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "MeshRide",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = when {
                    isLoopbackActive -> "OPUS LOOPBACK ACTIVE"
                    isSessionActive -> "FULL DUPLEX ACTIVE"
                    else -> "STANDBY"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    isLoopbackActive -> AccentAmber
                    isSessionActive -> AccentGreen
                    else -> TextMuted
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Loopback Self-Test Button
            IconButton(
                onClick = onToggleLoopback,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isLoopbackActive) AccentAmber.copy(alpha = 0.2f) else DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = "Loopback Test",
                    tint = if (isLoopbackActive) AccentAmber else TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            // Mute Button
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isMuted) AccentRed.copy(alpha = 0.2f) else DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    tint = if (isMuted) AccentRed else TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            // Bluetooth SCO Toggle
            IconButton(
                onClick = onToggleSco,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (audioRouting.isScoConnected) AccentCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = if (audioRouting.isScoConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth SCO",
                    tint = if (audioRouting.isScoConnected) AccentCyan else TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            // Main Power Toggle
            IconButton(
                onClick = onToggleSession,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSessionActive) AccentGreen.copy(alpha = 0.2f) else DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Session Power",
                    tint = if (isSessionActive) AccentGreen else TextMuted
                )
            }
        }
    }
}

@Composable
private fun TransmissionBanner(
    state: TransmissionState,
    isLoopbackActive: Boolean
) {
    val (bgColor, textColor, label) = when {
        isLoopbackActive -> Triple(AccentAmber, Color.Black, "OPUS AUDIO LOOPBACK (TEST MODE)")
        state == TransmissionState.TRANSMITTING -> Triple(AccentGreen, Color.Black, "TRANSMITTING (TX)")
        state == TransmissionState.RECEIVING -> Triple(AccentCyan, Color.Black, "RECEIVING (RX)")
        state == TransmissionState.FULL_DUPLEX -> Triple(AccentAmber, Color.Black, "FULL DUPLEX ACTIVE")
        else -> Triple(DarkSurfaceElevated, TextSecondary, "CHANNEL CLEAR")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = textColor,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ModeSelectorGroup(
    selectedMode: IntercomMode,
    onModeSelected: (IntercomMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IntercomMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            val bg by animateColorAsState(
                targetValue = if (isSelected) DarkSurfaceElevated else Color.Transparent,
                label = "mode_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) AccentGreen else TextMuted,
                label = "mode_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .pointerInput(mode) {
                        detectTapGestures { onModeSelected(mode) }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (mode) {
                        IntercomMode.PUSH_TO_TALK -> "PTT"
                        IntercomMode.TOGGLE -> "TOGGLE"
                        IntercomMode.ALWAYS_ON -> "ALWAYS-ON"
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun PttControlTarget(
    mode: IntercomMode,
    transmissionState: TransmissionState,
    isSessionActive: Boolean,
    micAudioLevel: Float,
    onPttChange: (Boolean) -> Unit,
    onToggleClick: () -> Unit
) {
    val isTransmitting = transmissionState == TransmissionState.TRANSMITTING ||
            transmissionState == TransmissionState.FULL_DUPLEX

    val outerColor by animateColorAsState(
        targetValue = if (isTransmitting) AccentGreen else DarkSurface,
        label = "ptt_outer"
    )

    // Animated glow pulse based on microphone loudness (0.0 to 1.0)
    val animatedScale by animateFloatAsState(
        targetValue = if (isTransmitting) 1.0f + (micAudioLevel * 0.12f) else 1.0f,
        label = "ptt_scale"
    )

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isTransmitting) 4.dp + (micAudioLevel * 8f).dp else 4.dp,
        label = "ptt_border"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(animatedScale)
            .clip(CircleShape)
            .background(DarkSurfaceElevated)
            .border(animatedBorderWidth, outerColor, CircleShape)
            .pointerInput(mode, isSessionActive) {
                if (!isSessionActive) return@pointerInput

                when (mode) {
                    IntercomMode.PUSH_TO_TALK -> {
                        detectTapGestures(
                            onPress = {
                                onPttChange(true)
                                tryAwaitRelease()
                                onPttChange(false)
                            }
                        )
                    }
                    IntercomMode.TOGGLE,
                    IntercomMode.ALWAYS_ON -> {
                        detectTapGestures {
                            onToggleClick()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "PTT Action",
                tint = if (isTransmitting) AccentGreen else TextSecondary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    !isSessionActive -> "SESSION OFF"
                    mode == IntercomMode.PUSH_TO_TALK -> if (isTransmitting) "TALKING" else "HOLD TO TALK"
                    mode == IntercomMode.TOGGLE -> if (isTransmitting) "MIC OPEN" else "TAP TO OPEN"
                    IntercomMode.ALWAYS_ON == mode -> if (isTransmitting) "OPEN INTERCOM" else "MUTED"
                    else -> "INTERCOM"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isTransmitting) AccentGreen else TextPrimary
            )
        }
    }
}

@Composable
private fun RiderMeshList(riders: List<Rider>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(riders, key = { it.id }) { rider ->
            RiderItemRow(rider = rider)
        }
    }
}

@Composable
private fun RiderItemRow(rider: Rider) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (rider.isTransmitting) AccentGreen else AccentCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (rider.isLocalUser) "${rider.callsign} (You)" else rider.callsign,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${rider.signalStrengthDbm} dBm • ${rider.latencyMs}ms",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (rider.isTransmitting) {
                Text(
                    text = "SPEAKING",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
