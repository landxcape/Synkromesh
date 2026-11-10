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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
        viewModel.onEvent(IntercomUiEvent.RefreshPreflightAndDevices)
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
        requiredPermissions = viewModel.getRequiredPermissions(),
        onEvent = viewModel::onEvent,
        onRequestBatteryExemption = onRequestBatteryExemption,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Pragmatic Layout: Orchestrates stateless child components.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntercomScreen(
    uiState: IntercomUiState,
    requiredPermissions: List<String>,
    onEvent: (IntercomUiEvent) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val preflightSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connectionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.isCallsignDialogOpen) {
        CallsignEditDialog(
            currentCallsign = uiState.callsign,
            onDismiss = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(false)) },
            onConfirm = { onEvent(IntercomUiEvent.SaveCallsign(it)) }
        )
    }

    if (uiState.isPermissionSheetVisible) {
        PermissionPreflightSheet(
            status = uiState.preflightStatus,
            requiredPermissions = requiredPermissions,
            onDismiss = { onEvent(IntercomUiEvent.SetPermissionSheetVisible(false)) },
            onRefresh = { onEvent(IntercomUiEvent.RefreshPreflightAndDevices) },
            sheetState = preflightSheetState
        )
    }

    if (uiState.isSettingsOpen) {
        SettingsSheet(
            callsign = uiState.callsign,
            rideChannel = uiState.rideChannel,
            vadThreshold = uiState.vadThreshold,
            opusBitrate = uiState.opusBitrate,
            isLoopbackActive = uiState.isLoopbackActive,
            isSpeakerphoneOn = uiState.isSpeakerphoneOn,
            audioRouting = uiState.audioRouting,
            pairedHeadsets = uiState.pairedHeadsets,
            preflightStatus = uiState.preflightStatus,
            onOpenCallsignDialog = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(true)) },
            onSelectChannel = { onEvent(IntercomUiEvent.SaveRideChannel(it)) },
            onVadThresholdChanged = { onEvent(IntercomUiEvent.SaveVadThreshold(it)) },
            onOpusBitrateChanged = { onEvent(IntercomUiEvent.SaveOpusBitrate(it)) },
            onToggleLoopback = { onEvent(IntercomUiEvent.ToggleLoopbackClicked) },
            onToggleSpeakerphone = { onEvent(IntercomUiEvent.ToggleSpeakerphoneClicked) },
            onToggleSco = { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) },
            onOpenPermissions = {
                onEvent(IntercomUiEvent.SetSettingsVisible(false))
                onEvent(IntercomUiEvent.SetPermissionSheetVisible(true))
            },
            onDismiss = { onEvent(IntercomUiEvent.SetSettingsVisible(false)) },
            sheetState = settingsSheetState
        )
    }

    if (uiState.isConnectionSetupOpen) {
        ConnectionSetupSheet(
            isSessionActive = uiState.isSessionActive,
            meshState = uiState.meshState,
            rideChannel = uiState.rideChannel,
            riders = uiState.riders,
            onSelectChannel = { onEvent(IntercomUiEvent.SaveRideChannel(it)) },
            onToggleSession = { onEvent(IntercomUiEvent.ToggleSessionClicked) },
            onDismiss = { onEvent(IntercomUiEvent.SetConnectionSetupVisible(false)) },
            sheetState = connectionSheetState
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            IntercomTopBar(
                callsign = uiState.callsign,
                rideChannel = uiState.rideChannel,
                isSessionActive = uiState.isSessionActive,
                audioRouting = uiState.audioRouting,
                isMuted = uiState.isMuted,
                isLoopbackActive = uiState.isLoopbackActive,
                isSpeakerphoneOn = uiState.isSpeakerphoneOn,
                onOpenCallsignDialog = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(true)) },
                onToggleSession = { onEvent(IntercomUiEvent.ToggleSessionClicked) },
                onToggleSco = { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) },
                onToggleMute = { onEvent(IntercomUiEvent.ToggleMuteClicked) },
                onToggleLoopback = { onEvent(IntercomUiEvent.ToggleLoopbackClicked) },
                onToggleSpeakerphone = { onEvent(IntercomUiEvent.ToggleSpeakerphoneClicked) },
                onOpenRadar = { onEvent(IntercomUiEvent.SetConnectionSetupVisible(true)) },
                onOpenSettings = { onEvent(IntercomUiEvent.SetSettingsVisible(true)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                TransmissionBanner(
                    state = uiState.transmissionState,
                    isLoopbackActive = uiState.isLoopbackActive
                )

                // Critical Preflight Check Alert Banner
                if (!uiState.preflightStatus.isReadyForIntercom) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PreflightAlertCard(
                        onFix = { onEvent(IntercomUiEvent.SetPermissionSheetVisible(true)) }
                    )
                }

                if (!uiState.isBatteryOptimizationIgnored) {
                    Spacer(modifier = Modifier.height(6.dp))
                    BatteryOptimizationWarningCard(onRequestExemption = onRequestBatteryExemption)
                }
                Spacer(modifier = Modifier.height(8.dp))
                AudioRoutingBadge(
                    audioRouting = uiState.audioRouting,
                    isSpeakerphoneOn = uiState.isSpeakerphoneOn
                )
                Spacer(modifier = Modifier.height(8.dp))
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

            // Connected Riders Mesh with Live Distance Meters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MESH RIDERS (${uiState.riders.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• CH ${uiState.rideChannel}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
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
    rideChannel: Int,
    isSessionActive: Boolean,
    audioRouting: AudioRoutingState,
    isMuted: Boolean,
    isLoopbackActive: Boolean,
    isSpeakerphoneOn: Boolean,
    onOpenCallsignDialog: () -> Unit,
    onToggleSession: () -> Unit,
    onToggleSco: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLoopback: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
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
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                        .clickable { onOpenCallsignDialog() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
                        text = "$callsign (CH $rideChannel)",
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
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isLoopbackActive -> AccentAmber
                    isSessionActive -> AccentGreen
                    else -> TextMuted
                }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Radar & Connection Setup Button
            IconButton(
                onClick = onOpenRadar,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = "Radar & Connection Setup",
                    tint = AccentCyan
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            // Settings Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            // Speakerphone Toggle
            IconButton(
                onClick = onToggleSpeakerphone,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSpeakerphoneOn) AccentCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Speakerphone",
                    tint = if (isSpeakerphoneOn) AccentCyan else TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

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
            Spacer(modifier = Modifier.width(4.dp))

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
private fun PreflightAlertCard(onFix: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFix() }
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.18f)),
        border = BorderStroke(1.dp, AccentRed)
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
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Permissions or Radios Required",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Mic or Bluetooth is offline. Tap to resolve and start talking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontSize = 10.sp
                    )
                }
            }
            Button(
                onClick = onFix,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(text = "SETUP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                Text(text = "SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCEL", color = TextSecondary)
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
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isLoopbackActive -> AccentAmber.copy(alpha = 0.2f)
            state == TransmissionState.TRANSMITTING -> AccentGreen.copy(alpha = 0.2f)
            else -> DarkSurfaceElevated
        },
        label = "banner_background"
    )
    val borderColor = when {
        isLoopbackActive -> AccentAmber
        state == TransmissionState.TRANSMITTING -> AccentGreen
        else -> DarkSurface
    }
    val textColor = when {
        isLoopbackActive -> AccentAmber
        state == TransmissionState.TRANSMITTING -> AccentGreen
        else -> TextSecondary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isLoopbackActive -> "LOOPBACK SELF-TEST (LOCAL ECHO)"
                state == TransmissionState.TRANSMITTING -> "● TRANSMITTING AUDIO"
                else -> "INTERCOM READY"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun AudioRoutingBadge(
    audioRouting: AudioRoutingState,
    isSpeakerphoneOn: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarkSurfaceElevated
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    audioRouting.isScoConnected -> Icons.Default.BluetoothConnected
                    isSpeakerphoneOn -> Icons.AutoMirrored.Filled.VolumeUp
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = if (audioRouting.isScoConnected || isSpeakerphoneOn) AccentCyan else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    audioRouting.isScoConnected -> audioRouting.deviceDescription.uppercase()
                    isSpeakerphoneOn -> "LOUDSPEAKER (PHONE SPEAKERS)"
                    else -> "EARPIECE / RECEIVER"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (audioRouting.isScoConnected || isSpeakerphoneOn) AccentCyan else TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
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
            .background(DarkSurfaceElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IntercomMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onModeSelected(mode) },
                color = if (isSelected) AccentCyan else Color.Transparent
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.displayName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Black else TextSecondary
                    )
                }
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
    val isTransmitting = transmissionState == TransmissionState.TRANSMITTING

    val targetSize by animateDpAsState(
        targetValue = if (isTransmitting) 220.dp else 200.dp,
        label = "ptt_size"
    )
    val glowScale by animateFloatAsState(
        targetValue = if (isTransmitting) 1.0f + (micAudioLevel * 0.25f) else 1.0f,
        label = "ptt_glow"
    )
    val buttonColor by animateColorAsState(
        targetValue = when {
            !isSessionActive -> DarkSurfaceElevated
            isTransmitting -> AccentGreen
            mode == IntercomMode.ALWAYS_ON -> AccentGreen.copy(alpha = 0.8f)
            else -> AccentCyan
        },
        label = "ptt_color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
    ) {
        // Voice Activity Radial Halo
        if (isTransmitting && micAudioLevel > 0.05f) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(glowScale)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(alpha = (micAudioLevel * 0.4f).coerceIn(0.1f, 0.4f)))
            )
        }

        // Main Glove-Friendly Touch Button
        Box(
            modifier = Modifier
                .size(targetSize)
                .clip(CircleShape)
                .background(buttonColor)
                .border(
                    width = if (isTransmitting) 4.dp else 2.dp,
                    color = if (isTransmitting) Color.White else AccentCyan.copy(alpha = 0.5f),
                    shape = CircleShape
                )
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
                        IntercomMode.TOGGLE -> {
                            detectTapGestures(
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleClick()
                                }
                            )
                        }
                        IntercomMode.ALWAYS_ON -> {
                            // In Always-on mode, mic is controlled by VAD gate
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = when {
                        !isSessionActive -> Icons.Default.PowerSettingsNew
                        isTransmitting -> Icons.Default.Mic
                        else -> Icons.Default.MicOff
                    },
                    contentDescription = null,
                    tint = if (isTransmitting) Color.Black else TextPrimary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        !isSessionActive -> "SESSION OFF"
                        isTransmitting -> "LIVE"
                        mode == IntercomMode.PUSH_TO_TALK -> "HOLD TO TALK"
                        mode == IntercomMode.TOGGLE -> "TAP TO TALK"
                        mode == IntercomMode.ALWAYS_ON -> "ALWAYS-ON MESH"
                        else -> "STANDBY"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isTransmitting) Color.Black else TextPrimary
                )
            }
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (meshState) {
                        MeshState.SEARCHING -> "Scanning for nearby motorcycle riders..."
                        MeshState.CONNECTED -> "Waiting for riders to link..."
                        MeshState.DISCONNECTED -> "No riders connected. Tap Setup or Power to begin."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
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
                        // Real-time Distance & Bearing
                        Text(
                            text = "${rider.formattedDistance} • ${rider.relativePosition ?: "Direct"} (${rider.signalStrengthDbm} dBm)",
                            color = AccentCyan,
                            fontWeight = FontWeight.Medium,
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
            .padding(vertical = 2.dp),
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
                modifier = Modifier.height(28.dp)
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

@Composable
private fun SignalStrengthBars(dbm: Int) {
    val bars = when {
        dbm >= -60 -> 4
        dbm >= -75 -> 3
        dbm >= -85 -> 2
        else -> 1
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(10.dp)
    ) {
        for (i in 1..4) {
            val isActive = i <= bars
            val heightFraction = i * 0.25f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((10 * heightFraction).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isActive) AccentCyan else DarkSurfaceElevated)
            )
        }
    }
}
