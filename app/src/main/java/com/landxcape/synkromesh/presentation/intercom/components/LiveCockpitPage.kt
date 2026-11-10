package com.landxcape.synkromesh.presentation.intercom.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.rotate
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
import com.landxcape.synkromesh.domain.model.Rider
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

/**
 * Phase 2: Live Cockpit HUD (In-Ride Context)
 * 100% distraction-free heads-up display combining 240dp Hero Voice Disc, inline closest-rider telemetry,
 * and collapsible 2D radar overlay with 0 page swiping required.
 */
@Composable
fun LiveCockpitPage(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Glance Status Ribbon
            CockpitStatusRibbon(
                channel = uiState.rideChannel,
                meshState = uiState.meshState,
                riderCount = uiState.riders.size,
                isScoConnected = uiState.audioRouting.isScoConnected,
                headsetName = uiState.audioRouting.connectedDeviceName
            )

            // 2. Active Speaker Banner
            ActiveSpeakerRibbon(
                transmissionState = uiState.transmissionState,
                riders = uiState.riders,
                isMuted = uiState.isMuted
            )

            // 3. Center Hero Voice Target Disc
            CockpitHeroVoiceDisc(
                mode = uiState.selectedMode,
                transmissionState = uiState.transmissionState,
                isMuted = uiState.isMuted,
                micAudioLevel = uiState.micAudioLevel,
                onPttChange = { pressed -> onEvent(IntercomUiEvent.PttPressed(pressed)) },
                onToggleClick = { onEvent(IntercomUiEvent.ToggleMuteClicked) }
            )

            // 4. Inline Closest Rider Telemetry Strip
            InlineFormationStrip(
                riders = uiState.riders,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEvent(IntercomUiEvent.ToggleRadarOverlay)
                }
            )

            // 5. Bottom Tactical Control Bar
            TacticalBottomBar(
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

        // Full 2D Tactical Radar Overlay (when tapped)
        AnimatedVisibility(
            visible = uiState.isRadarOverlayActive,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
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
private fun CockpitStatusRibbon(
    channel: Int,
    meshState: MeshState,
    riderCount: Int,
    isScoConnected: Boolean,
    headsetName: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Channel Pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = AccentCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "CH $channel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACTIVE MESH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }

            // Riders & Audio status
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connected Count Pill
                Surface(
                    color = when (meshState) {
                        MeshState.CONNECTED -> AccentGreen.copy(alpha = 0.15f)
                        MeshState.SEARCHING -> AccentAmber.copy(alpha = 0.15f)
                        MeshState.DISCONNECTED -> TextMuted.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            text = "$riderCount RIDERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = when (meshState) {
                                MeshState.CONNECTED -> AccentGreen
                                MeshState.SEARCHING -> AccentAmber
                                MeshState.DISCONNECTED -> TextMuted
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Headset SCO Icon
                Icon(
                    imageVector = Icons.Default.Headset,
                    contentDescription = headsetName ?: "Headset",
                    tint = if (isScoConnected) AccentGreen else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveSpeakerRibbon(
    transmissionState: TransmissionState,
    riders: List<Rider>,
    isMuted: Boolean
) {
    val activeSpeaker = riders.firstOrNull { it.isTransmitting && !it.isLocalUser }

    val (bannerText, bannerColor) = when {
        isMuted -> "MIC MUTED (TAP TO UNMUTE)" to AccentRed
        transmissionState == TransmissionState.TRANSMITTING -> "YOU ARE TRANSMITTING" to AccentGreen
        activeSpeaker != null -> "${activeSpeaker.callsign.uppercase()} TRANSMITTING" to AccentCyan
        else -> "CHANNEL READY • VOICE IDLE" to TextSecondary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bannerColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, bannerColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when {
                    isMuted -> Icons.Default.MicOff
                    transmissionState == TransmissionState.TRANSMITTING || activeSpeaker != null -> Icons.Default.GraphicEq
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = bannerColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = bannerText,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                color = bannerColor
            )
        }
    }
}

@Composable
private fun CockpitHeroVoiceDisc(
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

    val discColor by animateColorAsState(
        targetValue = when {
            isMuted -> AccentRed
            isTransmitting -> AccentGreen
            isReceiving -> AccentCyan
            else -> DarkSurfaceElevated
        },
        animationSpec = tween(150),
        label = "discColor"
    )

    val discBorderColor by animateColorAsState(
        targetValue = when {
            isMuted -> AccentRed.copy(alpha = 0.8f)
            isTransmitting -> AccentGreen.copy(alpha = 0.9f)
            isReceiving -> AccentCyan.copy(alpha = 0.9f)
            else -> AccentCyan.copy(alpha = 0.3f)
        },
        label = "discBorderColor"
    )

    // Breathing pulse animation when transmitting
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isTransmitting) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val audioRippleScale = 1.0f + (micAudioLevel * 0.25f).coerceIn(0f, 0.3f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .scale(if (isTransmitting) pulseScale else 1.0f)
    ) {
        // Outer glow ripple
        if (isTransmitting || isReceiving) {
            Box(
                modifier = Modifier
                    .size(240.dp * audioRippleScale)
                    .clip(CircleShape)
                    .background(discColor.copy(alpha = 0.15f))
            )
        }

        // Main Tactile Disc
        Surface(
            modifier = Modifier
                .size(210.dp)
                .shadow(
                    elevation = if (isTransmitting) 24.dp else 8.dp,
                    shape = CircleShape,
                    spotColor = discColor
                )
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
            color = discColor,
            shape = CircleShape,
            border = BorderStroke(4.dp, discBorderColor)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isMuted -> Icons.Default.MicOff
                        isTransmitting -> Icons.Default.Mic
                        isReceiving -> Icons.Default.GraphicEq
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = if (isTransmitting || isReceiving || isMuted) DarkBackground else AccentCyan,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isMuted -> "MUTED"
                        isTransmitting -> "LIVE TALK"
                        isReceiving -> "LISTENING"
                        mode == IntercomMode.PUSH_TO_TALK -> "HOLD TO TALK"
                        else -> "TAP TO TALK"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp,
                    color = if (isTransmitting || isReceiving || isMuted) DarkBackground else TextPrimary
                )

                Text(
                    text = mode.name.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isTransmitting || isReceiving || isMuted) DarkBackground.copy(alpha = 0.7f) else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun InlineFormationStrip(
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
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, AccentCyan.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FORMATION TELEMETRY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "TAP TO EXPAND 2D RADAR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentCyan
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (nearest.isEmpty()) {
                Text(
                    text = if (remoteRiders.isEmpty()) "No other riders in range" else "Calculating GPS formation...",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (rider in nearest) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = DarkSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DarkSurfaceElevated)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = rider.callsign,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = rider.formattedDistance,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = AccentCyan
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
}

@Composable
private fun TacticalBottomBar(
    isMuted: Boolean,
    isSpeakerphoneOn: Boolean,
    isScoConnected: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onStandby: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Mute Mic (Tactical Action)
        TacticalPillButton(
            text = if (isMuted) "UNMUTE" else "MUTE",
            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            accentColor = if (isMuted) AccentRed else DarkSurfaceElevated,
            textColor = if (isMuted) DarkBackground else TextPrimary,
            iconTint = if (isMuted) DarkBackground else AccentRed,
            borderColor = AccentRed.copy(alpha = 0.6f),
            onClick = onToggleMute,
            modifier = Modifier.weight(1f)
        )

        // 2. Audio Speaker/Headset Route
        TacticalPillButton(
            text = if (isScoConnected) "HEADSET" else if (isSpeakerphoneOn) "SPEAKER" else "EARPIECE",
            icon = if (isScoConnected) Icons.Default.Headset else Icons.Default.Speaker,
            accentColor = DarkSurfaceElevated,
            textColor = TextPrimary,
            iconTint = if (isScoConnected) AccentGreen else AccentCyan,
            borderColor = AccentCyan.copy(alpha = 0.4f),
            onClick = onToggleSpeaker,
            modifier = Modifier.weight(1f)
        )

        // 3. Standby / Leave to Lobby
        TacticalPillButton(
            text = "STANDBY",
            icon = Icons.Default.PowerSettingsNew,
            accentColor = DarkSurfaceElevated,
            textColor = TextSecondary,
            iconTint = AccentAmber,
            borderColor = AccentAmber.copy(alpha = 0.4f),
            onClick = onStandby,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TacticalPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    textColor: Color,
    iconTint: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = accentColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = textColor
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TACTICAL 2D FORMATION RADAR",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real-time P2P Mesh Range & Bearing",
                        fontSize = 11.sp,
                        color = AccentCyan
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, TextMuted)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Radar",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2D Compass Radar View
            RadarCanvasWidget(
                riders = riders,
                isScanning = meshState == MeshState.SEARCHING
            )

            Spacer(modifier = Modifier.height(16.dp))

            // List of connected riders
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(riders, key = { it.id }) { rider ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkSurface)
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
                                    text = rider.callsign,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Distance: ${rider.formattedDistance} ${rider.relativePosition ?: ""}",
                                    fontSize = 11.sp,
                                    color = AccentCyan
                                )
                            }

                            if (rider.relativeBearingDegrees != null) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Bearing",
                                    tint = AccentCyan,
                                    modifier = Modifier
                                        .size(18.dp)
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
