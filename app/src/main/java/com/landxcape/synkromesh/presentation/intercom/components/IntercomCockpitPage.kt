package com.landxcape.synkromesh.presentation.intercom.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.presentation.intercom.IntercomUiEvent
import com.landxcape.synkromesh.presentation.intercom.IntercomUiState
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

@Composable
fun IntercomCockpitPage(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit,
    onOpenChannelPicker: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Status Header (Channel selector & Mesh status)
        CockpitTopBar(
            channel = uiState.rideChannel,
            meshState = uiState.meshState,
            riderCount = uiState.riders.size,
            isSessionActive = uiState.isSessionActive,
            onOpenChannelPicker = onOpenChannelPicker,
            onShowQr = onShowQr,
            onScanQr = onScanQr,
            onToggleSession = { onEvent(IntercomUiEvent.ToggleSessionClicked) }
        )

        // 2. Active Speaker Bar
        ActiveSpeakerBanner(
            transmissionState = uiState.transmissionState,
            riders = uiState.riders,
            isSessionActive = uiState.isSessionActive,
            isSessionEngaging = uiState.isSessionEngaging
        )

        // 3. Hero PTT / Voice Target Disc
        HeroVoiceDisc(
            mode = uiState.selectedMode,
            transmissionState = uiState.transmissionState,
            isMuted = uiState.isMuted,
            isSessionActive = uiState.isSessionActive,
            isSessionEngaging = uiState.isSessionEngaging,
            micAudioLevel = uiState.micAudioLevel,
            onPttChange = { pressed -> onEvent(IntercomUiEvent.PttPressed(pressed)) },
            onToggleClick = { onEvent(IntercomUiEvent.ToggleMuteClicked) }
        )

        // 4. Quick Action Toggles (Mode & Audio Output)
        QuickActionControls(
            selectedMode = uiState.selectedMode,
            isSpeakerphoneOn = uiState.isSpeakerphoneOn,
            audioRoutingDescription = uiState.audioRouting.deviceDescription,
            onModeCycle = {
                val nextMode = when (uiState.selectedMode) {
                    IntercomMode.PUSH_TO_TALK -> IntercomMode.TOGGLE
                    IntercomMode.TOGGLE -> IntercomMode.ALWAYS_ON
                    IntercomMode.ALWAYS_ON -> IntercomMode.PUSH_TO_TALK
                }
                onEvent(IntercomUiEvent.ModeChanged(nextMode))
            },
            onToggleSpeakerphone = { onEvent(IntercomUiEvent.ToggleSpeakerphoneClicked) }
        )

        Spacer(modifier = Modifier.height(60.dp)) // Padding for floating bottom dock
    }
}

@Composable
private fun CockpitTopBar(
    channel: Int,
    meshState: MeshState,
    riderCount: Int,
    isSessionActive: Boolean,
    onOpenChannelPicker: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onToggleSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel Selector Pill
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onOpenChannelPicker() },
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CH $channel",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = AccentCyan
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Channel",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Action Buttons: Share QR, Scan QR & Power Toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Share QR Button
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { onShowQr() },
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "Show Pairing QR",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Scan QR Button
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { onScanQr() },
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan Pairing QR",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Quick Power / Session Toggle Button
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { onToggleSession() },
                color = if (isSessionActive) AccentGreen.copy(alpha = 0.2f) else DarkSurfaceElevated,
                border = BorderStroke(1.dp, if (isSessionActive) AccentGreen else TextMuted),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Toggle Session",
                        tint = if (isSessionActive) AccentGreen else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSpeakerBanner(
    transmissionState: TransmissionState,
    riders: List<com.landxcape.synkromesh.domain.model.Rider>,
    isSessionActive: Boolean,
    isSessionEngaging: Boolean = false
) {
    val activeSpeaker = riders.firstOrNull { it.isTransmitting && !it.isLocalUser }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when {
                isSessionEngaging -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AccentAmber,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "CONNECTING TO MESH & AUDIO...",
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber,
                        fontSize = 12.sp
                    )
                }
                !isSessionActive -> {
                    Text(
                        text = "Mesh Standby • Tap power button to connect",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                transmissionState == TransmissionState.TRANSMITTING -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "YOU ARE TRANSMITTING (BROADCASTING)",
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        fontSize = 12.sp
                    )
                }
                activeSpeaker != null -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${activeSpeaker.callsign.uppercase()} IS SPEAKING",
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontSize = 12.sp
                    )
                }
                else -> {
                    Text(
                        text = "Channel Clear • All riders listening",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroVoiceDisc(
    mode: IntercomMode,
    transmissionState: TransmissionState,
    isMuted: Boolean,
    isSessionActive: Boolean,
    isSessionEngaging: Boolean = false,
    micAudioLevel: Float,
    onPttChange: (Boolean) -> Unit,
    onToggleClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressedLocally by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "halo_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val isTransmitting = transmissionState == TransmissionState.TRANSMITTING
    val isReceiving = transmissionState == TransmissionState.RECEIVING

    val discColor by animateColorAsState(
        targetValue = when {
            !isSessionActive -> DarkSurface
            isTransmitting -> AccentGreen
            isReceiving -> AccentCyan
            else -> DarkSurfaceElevated
        },
        animationSpec = tween(150),
        label = "disc_color"
    )

    val haloColor = when {
        isTransmitting -> AccentGreen.copy(alpha = 0.35f)
        isReceiving -> AccentCyan.copy(alpha = 0.35f)
        else -> AccentCyan.copy(alpha = 0.08f)
    }

    Box(
        modifier = Modifier
            .size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Glowing Halo
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(if (isTransmitting || isReceiving) pulseScale else 1.0f)
                .clip(CircleShape)
                .background(haloColor)
        )

        // Middle Border Ring
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            discColor.copy(alpha = 0.85f),
                            discColor.copy(alpha = 0.45f)
                        )
                    )
                )
        )

        // Inner Touch Target Disc
        Box(
            modifier = Modifier
                .size(185.dp)
                .shadow(elevation = 12.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    if (isTransmitting) AccentGreen
                    else DarkBackground
                )
                .pointerInput(mode, isSessionActive) {
                    if (!isSessionActive) return@pointerInput

                    when (mode) {
                        IntercomMode.PUSH_TO_TALK -> {
                            detectTapGestures(
                                onPress = {
                                    isPressedLocally = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPttChange(true)
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isPressedLocally = false
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onPttChange(false)
                                    }
                                }
                            )
                        }
                        IntercomMode.TOGGLE,
                        IntercomMode.ALWAYS_ON -> {
                            detectTapGestures(
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleClick()
                                }
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isTransmitting -> Icons.Default.Mic
                        isMuted -> Icons.Default.MicOff
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = if (isTransmitting) DarkBackground else AccentCyan,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isSessionEngaging -> "CONNECTING..."
                        !isSessionActive -> "SESSION STANDBY"
                        isTransmitting -> "TRANSMITTING"
                        isMuted -> "MUTED"
                        mode == IntercomMode.PUSH_TO_TALK -> "HOLD TO TALK"
                        mode == IntercomMode.TOGGLE -> "TAP TO TALK"
                        else -> "OPEN MIC ACTIVE"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (isTransmitting) DarkBackground else TextPrimary,
                    textAlign = TextAlign.Center
                )

                if (isTransmitting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(micAudioLevel * 100).toInt()}% VOICE RMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = DarkBackground.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionControls(
    selectedMode: IntercomMode,
    isSpeakerphoneOn: Boolean,
    audioRoutingDescription: String,
    onModeCycle: () -> Unit,
    onToggleSpeakerphone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode Selector Pill
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onModeCycle()
                },
            color = DarkSurfaceElevated,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MODE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = when (selectedMode) {
                            IntercomMode.PUSH_TO_TALK -> "PUSH TO TALK"
                            IntercomMode.TOGGLE -> "TOGGLE LATCH"
                            IntercomMode.ALWAYS_ON -> "ALWAYS ON"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCyan
                    )
                }
            }
        }

        // Speakerphone / Helmet Audio Route Pill
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleSpeakerphone()
                },
            color = DarkSurfaceElevated,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (isSpeakerphoneOn) AccentGreen.copy(alpha = 0.4f) else AccentCyan.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isSpeakerphoneOn) Icons.Default.Speaker else Icons.Default.Headset,
                    contentDescription = null,
                    tint = if (isSpeakerphoneOn) AccentGreen else AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AUDIO OUT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = if (isSpeakerphoneOn) "LOUDSPEAKER" else "HELMET SCO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSpeakerphoneOn) AccentGreen else TextPrimary
                    )
                }
            }
        }
    }
}
