package com.landxcape.synkromesh.presentation.intercom.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.domain.model.HelmetAcousticProfile
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.presentation.intercom.IntercomUiEvent
import com.landxcape.synkromesh.presentation.intercom.IntercomUiState
import com.landxcape.synkromesh.presentation.theme.AccentAmber
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.AccentGreen
import com.landxcape.synkromesh.presentation.theme.AccentRed
import com.landxcape.synkromesh.presentation.theme.BorderFocus
import com.landxcape.synkromesh.presentation.theme.BorderSubtle
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.DarkSurface
import com.landxcape.synkromesh.presentation.theme.DarkSurfaceElevated
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary
import com.landxcape.synkromesh.presentation.theme.TextSecondary

/**
 * Phase 2: Live Cockpit HUD
 * Minimalist, tool-grade automotive instrument.
 * Zero colorful gradients, zero cartoon bubbles. Precision audio reticle and live telemetry tape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCockpitPage(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isTacticalSettingsOpen by remember { mutableStateOf(false) }

    // Intercept back gesture for tactical settings sheet first
    BackHandler(enabled = isTacticalSettingsOpen) {
        isTacticalSettingsOpen = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Technical Status Strip with 1-tap Settings
            CockpitStatusStrip(
                channel = uiState.rideChannel,
                meshState = uiState.meshState,
                riderCount = uiState.riders.size,
                isScoConnected = uiState.audioRouting.isScoConnected,
                headsetName = uiState.audioRouting.connectedDeviceName,
                isLowLatencyPower = uiState.isLowLatencyPowerMode,
                remoteRiders = uiState.riders.filter { !it.isLocalUser },
                onToggleSco = { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) },
                onOpenSettings = { isTacticalSettingsOpen = true }
            )

            // 2. Minimalist Speaker State Banner
            SpeakerStateBanner(
                transmissionState = uiState.transmissionState,
                riders = uiState.riders,
                isMuted = uiState.isMuted
            )

            // 3. Precision Voice Reticle (Hero Instrument - Pure Tactile Actuator, Zero Gesture Collision)
            VoiceReticleInstrument(
                mode = uiState.selectedMode,
                transmissionState = uiState.transmissionState,
                isMuted = uiState.isMuted,
                micAudioLevel = uiState.micAudioLevel,
                onPttChange = { pressed -> onEvent(IntercomUiEvent.PttPressed(pressed)) },
                onToggleClick = { onEvent(IntercomUiEvent.ToggleMuteClicked) }
            )

            // 4. Low-Profile Telemetry Tape
            TelemetryTape(
                riders = uiState.riders,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEvent(IntercomUiEvent.ToggleRadarOverlay)
                }
            )

            // 5. Unified Bottom Tactical Console: Docked Mode Selector + Action Keys
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Docked 3-Way Mode Micro-Selector
                CockpitModeSelector(
                    selectedMode = uiState.selectedMode,
                    onSelectMode = { mode ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEvent(IntercomUiEvent.ModeChanged(mode))
                    }
                )

                // Tactical Action Keys
                CockpitControlKeys(
                    isMuted = uiState.isMuted,
                    isSpeakerphoneOn = uiState.isSpeakerphoneOn,
                    isScoConnected = uiState.audioRouting.isScoConnected,
                    onToggleMute = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEvent(IntercomUiEvent.ToggleMuteClicked)
                    },
                    onToggleSpeaker = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEvent(IntercomUiEvent.ToggleSpeakerphoneClicked)
                    },
                    onStandby = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEvent(IntercomUiEvent.ReturnToStaging)
                    }
                )
            }
        }

        // Tactical In-Ride Settings Bottom Sheet
        if (isTacticalSettingsOpen) {
            ModalBottomSheet(
                onDismissRequest = { isTacticalSettingsOpen = false },
                containerColor = DarkSurfaceElevated,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BorderFocus)
                    )
                }
            ) {
                TacticalQuickSettingsSheet(
                    uiState = uiState,
                    onEvent = onEvent,
                    onClose = { isTacticalSettingsOpen = false }
                )
            }
        }

        // Full 2D Tactical Radar Overlay (when tapped)
        AnimatedVisibility(
            visible = uiState.isRadarOverlayActive,
            enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 3 })
        ) {
            CockpitRadarOverlay(
                riders = uiState.riders,
                meshState = uiState.meshState,
                onClose = { onEvent(IntercomUiEvent.ToggleRadarOverlay) }
            )
        }
    }
}

@Composable
private fun CockpitStatusStrip(
    channel: Int,
    meshState: MeshState,
    riderCount: Int,
    isScoConnected: Boolean,
    headsetName: String?,
    isLowLatencyPower: Boolean,
    remoteRiders: List<Rider>,
    onToggleSco: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Channel and Mesh Node count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CH 0$channel",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = " // ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = BorderFocus
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when (meshState) {
                                MeshState.CONNECTED -> AccentGreen
                                MeshState.SEARCHING -> AccentAmber
                                MeshState.DISCONNECTED -> TextMuted
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$riderCount NODES",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }

            // Center: Live Latency HUD & Power State
            if (remoteRiders.isNotEmpty()) {
                val minRtt = remoteRiders.map { it.latencyMs }.minOrNull() ?: 15L
                val maxLoss = remoteRiders.map { it.packetLossPercent }.maxOrNull() ?: 0f
                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (maxLoss > 10f) AccentAmber.copy(alpha = 0.5f) else BorderSubtle)
                ) {
                    Text(
                        text = "${minRtt}ms • ${maxLoss.toInt()}%" + if (!isLowLatencyPower) " • ECO" else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (maxLoss > 10f) AccentAmber else AccentGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else if (!isLowLatencyPower) {
                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "ECO POWER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Right: Bluetooth SCO indicator + Quick Settings Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleSco() },
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isScoConnected) BorderFocus else BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = headsetName ?: "Headset",
                            tint = if (isScoConnected) AccentGreen else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isScoConnected) "SCO" else "SPK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isScoConnected) AccentGreen else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenSettings() },
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Tactical Settings",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakerStateBanner(
    transmissionState: TransmissionState,
    riders: List<Rider>,
    isMuted: Boolean
) {
    val activeSpeaker = riders.firstOrNull { it.isTransmitting && !it.isLocalUser }

    val (statusText, statusDotColor) = when {
        isMuted -> "MIC MUTED" to AccentRed
        transmissionState == TransmissionState.TRANSMITTING -> "HOT MIC // TRANSMITTING" to AccentGreen
        activeSpeaker != null -> "${activeSpeaker.callsign.uppercase()} // TRANSMITTING" to AccentCyan
        else -> "STANDBY // READY" to TextMuted
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = if (statusDotColor == TextMuted) TextSecondary else TextPrimary
            )
        }
    }
}

@Composable
private fun VoiceReticleInstrument(
    mode: IntercomMode,
    transmissionState: TransmissionState,
    isMuted: Boolean,
    micAudioLevel: Float,
    onPttChange: (Boolean) -> Unit,
    onToggleClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressedLocally by remember { mutableStateOf(false) }

    val isTransmitting = transmissionState == TransmissionState.TRANSMITTING || isPressedLocally
    val isReceiving = transmissionState == TransmissionState.RECEIVING

    val activeColor by animateColorAsState(
        targetValue = when {
            isMuted -> AccentRed
            isTransmitting -> AccentGreen
            isReceiving -> AccentCyan
            else -> BorderFocus
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reticleColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressedLocally) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reticleScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(230.dp)
            .scale(scale)
    ) {
        // Technical Concentric Reticle Rings (Canvas)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2

            // Outer Hairline
            drawCircle(
                color = BorderSubtle,
                radius = radius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Middle Active Ring
            drawCircle(
                color = if (isTransmitting || isReceiving || isMuted) activeColor.copy(alpha = 0.5f) else BorderSubtle.copy(alpha = 0.5f),
                radius = radius - 16.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Dynamic Audio Wave Ring
            if (isTransmitting) {
                val waveRadius = radius - 30.dp.toPx() + (micAudioLevel * 20.dp.toPx())
                drawCircle(
                    color = AccentGreen.copy(alpha = 0.2f),
                    radius = waveRadius.coerceAtLeast(radius - 30.dp.toPx()),
                    center = center
                )
            }
        }

        // Tactile Center Disc (Interactive Core - Pure Single-Purpose Actuator)
        Surface(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .pointerInput(mode) {
                    when (mode) {
                        IntercomMode.PUSH_TO_TALK -> {
                            detectTapGestures(
                                onPress = {
                                    isPressedLocally = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPttChange(true)
                                    tryAwaitRelease()
                                    isPressedLocally = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPttChange(false)
                                }
                            )
                        }
                        IntercomMode.TOGGLE, IntercomMode.ALWAYS_ON -> {
                            detectTapGestures(
                                onTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleClick()
                                }
                            )
                        }
                    }
                },
            color = DarkSurface,
            shape = CircleShape,
            border = BorderStroke(
                width = if (isTransmitting || isReceiving || isMuted) 2.dp else 1.dp,
                color = activeColor
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isMuted -> Icons.Default.MicOff
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when {
                        isMuted -> "MUTED"
                        isTransmitting -> "LIVE"
                        isReceiving -> "INCOMING"
                        mode == IntercomMode.PUSH_TO_TALK -> "HOLD TALK"
                        mode == IntercomMode.TOGGLE -> "TAP TALK"
                        mode == IntercomMode.ALWAYS_ON -> "OPEN MIC"
                        else -> "TALK"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )

                Text(
                    text = mode.name.replace("_", " "),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun TacticalQuickSettingsSheet(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TACTICAL AUDIO & HARDWARE",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Live In-Ride Routing // Channel 0${uiState.rideChannel}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onClose() },
                color = DarkBackground,
                shape = CircleShape,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 1. Audio Routing Switches
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bluetooth SCO Card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) },
                color = if (uiState.audioRouting.isScoConnected) DarkSurface else DarkBackground,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (uiState.audioRouting.isScoConnected) AccentGreen else BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = null,
                            tint = if (uiState.audioRouting.isScoConnected) AccentGreen else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BT SCO",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.audioRouting.isScoConnected) AccentGreen else TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.audioRouting.isScoConnected) {
                            (uiState.audioRouting.connectedDeviceName ?: "HEADSET").take(12)
                        } else {
                            "CONNECT"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Speakerphone Card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEvent(IntercomUiEvent.ToggleSpeakerphoneClicked) },
                color = if (uiState.isSpeakerphoneOn) DarkSurface else DarkBackground,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (uiState.isSpeakerphoneOn) AccentCyan else BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = null,
                            tint = if (uiState.isSpeakerphoneOn) AccentCyan else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SPEAKER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isSpeakerphoneOn) AccentCyan else TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.isSpeakerphoneOn) "ACTIVE" else "OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // 2. Live Mic Input Level & Noise Gate (VAD) Slider
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MIC INPUT LEVEL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "${(uiState.micAudioLevel * 100).toInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.micAudioLevel > 0.05f) AccentGreen else TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Progress Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = DarkBackground,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = uiState.micAudioLevel.coerceIn(0f, 1f))
                                .background(if (uiState.micAudioLevel > 0.6f) AccentAmber else AccentGreen)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VAD NOISE GATE SENSITIVITY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "${(uiState.vadThreshold * 100).toInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                Slider(
                    value = uiState.vadThreshold,
                    onValueChange = { onEvent(IntercomUiEvent.SaveVadThreshold(it)) },
                    valueRange = 0.01f..0.20f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = DarkBackground
                    )
                )
            }
        }

        // 3. Helmet Acoustic Profile & Dynamic Speed Gain Boost (Nepali Calibrated)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HELMET ACOUSTIC PROFILE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format("%.1fx GAIN BOOST", uiState.dynamicGain),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.dynamicGain > 1.05f) AccentAmber else AccentCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HelmetAcousticProfile.entries.forEach { profile ->
                        val isSelected = profile == uiState.helmetProfile
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEvent(IntercomUiEvent.SelectHelmetProfile(profile)) },
                            color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else DarkBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderSubtle)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = when (profile) {
                                        HelmetAcousticProfile.FULL_FACE_QUIET -> "FULL-FACE"
                                        HelmetAcousticProfile.MODULAR_URBAN -> "MODULAR"
                                        HelmetAcousticProfile.OPEN_FACE_CRUISER -> "OPEN-WIND"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AccentCyan else TextPrimary
                                )
                                Text(
                                    text = "${profile.opusBitrateKbps} kbps",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Virtual Convoy Ride Simulator
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onEvent(IntercomUiEvent.ToggleVirtualSimulation) },
            color = if (uiState.isSimulating) AccentAmber.copy(alpha = 0.15f) else DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (uiState.isSimulating) AccentAmber else BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isSimulating) AccentAmber else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CONVOY SIMULATION",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (uiState.isSimulating) "Apex, Mid Relay & Sweeper Active" else "Simulate 3 nodes & 2-hop relay",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                Text(
                    text = if (uiState.isSimulating) "STOP" else "START",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (uiState.isSimulating) AccentAmber else AccentCyan
                )
            }
        }

        // 4. Audio DSP Engine & Peer Diagnostics Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DSP: WEBRTC AEC + NS + AGC // OPUS 48kHz",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = TextMuted
            )
            Text(
                text = "${uiState.riders.size} PEERS",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGreen
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CockpitModeSelector(
    selectedMode: IntercomMode,
    onSelectMode: (IntercomMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modes = listOf(
                "PTT" to IntercomMode.PUSH_TO_TALK,
                "TOGGLE" to IntercomMode.TOGGLE,
                "OPEN MIC" to IntercomMode.ALWAYS_ON
            )

            modes.forEach { (label, mode) ->
                val isSelected = selectedMode == mode
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) DarkSurfaceElevated else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "cockpitModeBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextMuted,
                    label = "cockpitModeText"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectMode(mode) },
                    color = bgColor,
                    shape = RoundedCornerShape(6.dp),
                    border = if (isSelected) BorderStroke(1.dp, BorderFocus) else null
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTape(
    riders: List<Rider>,
    onClick: () -> Unit
) {
    val remoteRiders = riders.filter { !it.isLocalUser }
    val nearest = remoteRiders.filter { it.estimatedDistanceMeters != null }
        .sortedBy { it.estimatedDistanceMeters ?: Float.MAX_VALUE }
        .take(2)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (nearest.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (remoteRiders.isEmpty()) "NO OTHER NODES IN RANGE" else "CALCULATING GPS FORMATION...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (rider in nearest) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rider.callsign.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = rider.formattedDistance,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                            if (rider.relativeBearingDegrees != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .rotate(rider.relativeBearingDegrees)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "RADAR ↗",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun CockpitControlKeys(
    isMuted: Boolean,
    isSpeakerphoneOn: Boolean,
    isScoConnected: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onStandby: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Key 1: Mute
        TacticalKey(
            title = if (isMuted) "MIC MUTED" else "MIC ON",
            indicatorColor = if (isMuted) AccentRed else AccentGreen,
            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            onClick = onToggleMute,
            modifier = Modifier.weight(1f)
        )

        // Key 2: Audio Routing
        TacticalKey(
            title = if (isScoConnected) "HEADSET" else if (isSpeakerphoneOn) "SPEAKER" else "EARPIECE",
            indicatorColor = if (isScoConnected) AccentGreen else AccentCyan,
            icon = if (isScoConnected) Icons.Default.Headset else Icons.Default.Speaker,
            onClick = onToggleSpeaker,
            modifier = Modifier.weight(1f)
        )

        // Key 3: Disengage / Standby
        TacticalKey(
            title = "STANDBY",
            indicatorColor = AccentAmber,
            icon = Icons.Default.PowerSettingsNew,
            onClick = onStandby,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TacticalKey(
    title: String,
    indicatorColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "keyScale"
    )

    Surface(
        modifier = modifier
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = indicatorColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun CockpitRadarOverlay(
    riders: List<Rider>,
    meshState: MeshState,
    onClose: () -> Unit
) {
    var overlayMode by remember { mutableStateOf("RADAR") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Segmented Toggle & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (overlayMode == "RADAR") "TACTICAL RADAR" else "TACTICAL MAP",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (overlayMode == "RADAR") "P2P Range & Direction Vector" else "OpenStreetMap • Offline Cached",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // RADAR / MAP Segmented Switcher
                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(modifier = Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { overlayMode = "RADAR" },
                                color = if (overlayMode == "RADAR") AccentCyan else DarkSurface
                            ) {
                                Text(
                                    text = "RADAR",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (overlayMode == "RADAR") DarkBackground else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { overlayMode = "MAP" },
                                color = if (overlayMode == "MAP") AccentCyan else DarkSurface
                            ) {
                                Text(
                                    text = "MAP",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (overlayMode == "MAP") DarkBackground else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onClose() },
                        color = DarkSurface,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Viewport: 2D Compass Radar View OR OpenStreetMap
            if (overlayMode == "RADAR") {
                RadarCanvasWidget(
                    riders = riders,
                    isScanning = meshState == MeshState.SEARCHING
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                ) {
                    TacticalOsmMapView(
                        riders = riders
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of connected nodes
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(riders, key = { it.id }) { rider ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = rider.callsign.uppercase(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                val hopStr = if (rider.hopCount > 1) " • ${rider.hopCount} HOPS" else " • 1 HOP"
                                val diagStr = if (!rider.isLocalUser) " • ${rider.latencyMs}ms • LOSS ${rider.packetLossPercent.toInt()}%$hopStr" else ""
                                Text(
                                    text = "RANGE: ${rider.formattedDistance} ${rider.relativePosition ?: ""}$diagStr",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }

                            if (rider.relativeBearingDegrees != null) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Bearing",
                                    tint = AccentCyan,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(rider.relativeBearingDegrees)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
