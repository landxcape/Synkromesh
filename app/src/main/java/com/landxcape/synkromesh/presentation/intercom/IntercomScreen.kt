package com.landxcape.synkromesh.presentation.intercom

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.landxcape.synkromesh.domain.model.AudioRoutingState
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.presentation.theme.AccentAmber
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.AccentGreen
import com.landxcape.synkromesh.presentation.theme.AccentRed
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.DarkSurface
import com.landxcape.synkromesh.presentation.theme.DarkSurfaceElevated
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary
import com.landxcape.synkromesh.presentation.theme.TextSecondary
import com.landxcape.synkromesh.service.IntercomForegroundService

/**
 * Smart Parent Composable: Owns state collection and propagates data/callbacks downward.
 */
@Composable
fun IntercomRoute(
    modifier: Modifier = Modifier,
    viewModel: IntercomViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(IntercomUiEvent.RefreshBatteryStatus)
    }

    LaunchedEffect(uiState.isSessionActive) {
        val serviceIntent = Intent(context, IntercomForegroundService::class.java).apply {
            action = if (uiState.isSessionActive) {
                IntercomForegroundService.ACTION_START
            } else {
                IntercomForegroundService.ACTION_STOP
            }
        }
        if (uiState.isSessionActive) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(IntercomUiEvent.ClearError)
        }
    }

    val onRequestBatteryExemption = {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    IntercomScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onRequestBatteryExemption = onRequestBatteryExemption,
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
    onRequestBatteryExemption: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    if (uiState.isCallsignDialogOpen) {
        CallsignEditDialog(
            currentCallsign = uiState.callsign,
            onDismiss = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(false)) },
            onConfirm = { onEvent(IntercomUiEvent.SaveCallsign(it)) }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            IntercomTopBar(
                callsign = uiState.callsign,
                isSessionActive = uiState.isSessionActive,
                audioRouting = uiState.audioRouting,
                isMuted = uiState.isMuted,
                isLoopbackActive = uiState.isLoopbackActive,
                onOpenCallsignDialog = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(true)) },
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
                if (!uiState.isBatteryOptimizationIgnored) {
                    Spacer(modifier = Modifier.height(8.dp))
                    BatteryOptimizationWarningCard(onRequestExemption = onRequestBatteryExemption)
                }
                Spacer(modifier = Modifier.height(10.dp))
                AudioRoutingBadge(audioRouting = uiState.audioRouting)
                Spacer(modifier = Modifier.height(10.dp))
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MESH RIDERS (${uiState.riders.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Text(
                        text = uiState.meshState.statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (uiState.meshState) {
                            MeshState.CONNECTED -> AccentGreen
                            MeshState.SEARCHING -> AccentAmber
                            MeshState.DISCONNECTED -> TextMuted
                        }
                    )
                }
                RiderMeshList(
                    riders = uiState.riders,
                    meshState = uiState.meshState,
                    mutedPeerIds = uiState.mutedPeerIds,
                    onToggleMutePeer = { onEvent(IntercomUiEvent.TogglePeerMute(it)) }
                )
            }
        }
    }
}

// ---------------- Stateless Child Components ----------------

@Composable
private fun IntercomTopBar(
    callsign: String,
    isSessionActive: Boolean,
    audioRouting: AudioRoutingState,
    isMuted: Boolean,
    isLoopbackActive: Boolean,
    onOpenCallsignDialog: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Synkromesh",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                        .clickable { onOpenCallsignDialog() }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Callsign",
                        tint = AccentCyan,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = callsign.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
            }
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
private fun CallsignEditDialog(
    currentCallsign: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentCallsign) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rider Callsign",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "This handle is broadcast to nearby motorcycle riders during mesh intercom discovery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 16) text = it },
                    label = { Text("Callsign") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
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
private fun AudioRoutingBadge(audioRouting: AudioRoutingState) {
    val isSco = audioRouting.isScoConnected
    val deviceName = audioRouting.connectedDeviceName ?: "Phone Internal Audio"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isSco) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = "Routing",
                tint = if (isSco) AccentCyan else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = deviceName,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 13.sp,
                color = TextPrimary
            )
        }

        Text(
            text = if (isSco) "HELMET SCO" else "SPEAKER",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (isSco) AccentCyan else TextMuted,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ModeSelectorGroup(
    selectedMode: IntercomMode,
    onModeSelected: (IntercomMode) -> Unit
) {
    val haptic = LocalHapticFeedback.current
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
                        detectTapGestures {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onModeSelected(mode)
                        }
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
    val haptic = LocalHapticFeedback.current
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
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPttChange(true)
                                tryAwaitRelease()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPttChange(false)
                            }
                        )
                    }
                    IntercomMode.TOGGLE,
                    IntercomMode.ALWAYS_ON -> {
                        detectTapGestures {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
private fun SignalStrengthBars(
    dbm: Int,
    modifier: Modifier = Modifier
) {
    val activeBars = when {
        dbm >= -65 -> 4
        dbm >= -75 -> 3
        dbm >= -85 -> 2
        else -> 1
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val barHeight = (4 + i * 2.5f).dp
            val isFilled = i <= activeBars
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isFilled) AccentCyan else DarkSurfaceElevated)
            )
        }
    }
}

@Composable
private fun RiderMeshList(
    riders: List<Rider>,
    meshState: MeshState,
    mutedPeerIds: Set<String>,
    onToggleMutePeer: (String) -> Unit
) {
    if (riders.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (meshState) {
                        MeshState.CONNECTED -> "No other riders active yet"
                        MeshState.SEARCHING -> "Discovering nearby helmet intercoms..."
                        MeshState.DISCONNECTED -> "Intercom session standby"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (meshState) {
                        MeshState.CONNECTED -> "Waiting for audio datagrams"
                        MeshState.SEARCHING -> "BLE & Wi-Fi Direct zero-config pairing active"
                        MeshState.DISCONNECTED -> "Tap power icon in top bar to start session"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(riders, key = { it.id }) { rider ->
                RiderItemRow(
                    rider = rider,
                    isMuted = mutedPeerIds.contains(rider.id),
                    onToggleMute = onToggleMutePeer
                )
            }
        }
    }
}

@Composable
private fun RiderItemRow(
    rider: Rider,
    isMuted: Boolean,
    onToggleMute: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
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
                        .background(
                            when {
                                isMuted -> TextMuted
                                rider.isTransmitting -> AccentGreen
                                else -> AccentCyan
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (rider.isLocalUser) "${rider.callsign} (You)" else rider.callsign,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMuted) TextMuted else TextPrimary,
                            fontSize = 14.sp
                        )
                        if (isMuted) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MUTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalStrengthBars(dbm = rider.signalStrengthDbm)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${rider.signalStrengthDbm} dBm • ${rider.latencyMs}ms",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rider.isTransmitting && !isMuted) {
                    Text(
                        text = "SPEAKING",
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                if (!rider.isLocalUser) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleMute(rider.id)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Rider" else "Mute Rider",
                            tint = if (isMuted) AccentRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationWarningCard(
    onRequestExemption: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, AccentAmber)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Battery Warning",
                    tint = AccentAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Battery Optimization Active",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Exempt app to prevent Android from killing audio when screen is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestExemption,
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "EXEMPT",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
