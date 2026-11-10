package com.landxcape.synkromesh.presentation.intercom.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.presentation.intercom.GroupingMode
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
import com.landxcape.synkromesh.util.RideQrData
import com.landxcape.synkromesh.util.RideQrManager

/**
 * Phase 1: Ride Staging Lobby
 * Modern, tool-grade automotive aesthetic (Linear / Nothing OS / Industrial HMI).
 * Monochromatic discipline, 1px hairlines, segmented controls, zero loud gradients.
 */
@Composable
fun RideStagingPage(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Technical Header Bar
            TechnicalHeaderBar(
                callsign = uiState.callsign,
                isScoConnected = uiState.audioRouting.isScoConnected,
                headsetName = uiState.audioRouting.connectedDeviceName,
                onEditCallsign = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(true)) },
                onToggleSco = { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Linear-Style Segmented Mode Switcher
            SegmentedModeSwitcher(
                selectedMode = uiState.groupingMode,
                onSelectMode = { mode ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEvent(IntercomUiEvent.SelectGroupingMode(mode))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Dynamic Mode Detail Card (Smooth Morphing Content)
            AnimatedContent(
                targetState = uiState.groupingMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "modeCardTransition"
            ) { mode ->
                when (mode) {
                    GroupingMode.LEAD -> LeadPackContent(
                        channel = uiState.rideChannel,
                        callsign = uiState.callsign,
                        hotspotSsid = uiState.hotspotSsid,
                        hotspotPassword = uiState.hotspotPassword,
                        isHotspotActive = uiState.isHotspotActive,
                        riderCount = uiState.riders.size,
                        onToggleHotspot = { onEvent(IntercomUiEvent.ToggleHotspotClicked) }
                    )
                    GroupingMode.JOIN -> JoinPackContent(
                        rideChannel = uiState.rideChannel,
                        meshState = uiState.meshState,
                        onScanQr = onScanQr
                    )
                    GroupingMode.OPEN_MESH -> OpenMeshContent(
                        selectedChannel = uiState.rideChannel,
                        onSelectChannel = { ch ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEvent(IntercomUiEvent.SaveRideChannel(ch))
                        }
                    )
                }
            }

            // Bottom clearance for pinned action bar
            Spacer(modifier = Modifier.height(100.dp))
        }

        // 4. Bottom-Pinned Hero Action Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = DarkBackground.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                PrecisionActionButton(
                    text = "START INTERCOM",
                    subtext = when (uiState.groupingMode) {
                        GroupingMode.LEAD -> "BROADCASTING LEAD PACK // CH 0${uiState.rideChannel}"
                        GroupingMode.JOIN -> "AUTO-SYNC WITH LEAD // CH 0${uiState.rideChannel}"
                        GroupingMode.OPEN_MESH -> "ENGAGE OPEN MESH // CH 0${uiState.rideChannel}"
                    },
                    isOnline = uiState.groupingMode == GroupingMode.LEAD && uiState.isHotspotActive,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEvent(IntercomUiEvent.EngageIntercom)
                    }
                )
            }
        }
    }
}

@Composable
private fun TechnicalHeaderBar(
    callsign: String,
    isScoConnected: Boolean,
    headsetName: String?,
    onEditCallsign: () -> Unit,
    onToggleSco: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Branding & Callsign
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onEditCallsign() }
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SYNKMESH",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = callsign.uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color = TextPrimary
            )
        }

        // Right: Hardware SCO Audio Pill
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggleSco() },
            color = DarkSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (isScoConnected) BorderFocus else BorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isScoConnected) AccentGreen else AccentAmber)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScoConnected) {
                        (headsetName ?: "BT HEADSET").take(14).uppercase()
                    } else {
                        "PHONE MIC"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SegmentedModeSwitcher(
    selectedMode: GroupingMode,
    onSelectMode: (GroupingMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeSegmentTab(
                title = "LEAD PACK",
                isSelected = selectedMode == GroupingMode.LEAD,
                onClick = { onSelectMode(GroupingMode.LEAD) },
                modifier = Modifier.weight(1f)
            )
            ModeSegmentTab(
                title = "JOIN",
                isSelected = selectedMode == GroupingMode.JOIN,
                onClick = { onSelectMode(GroupingMode.JOIN) },
                modifier = Modifier.weight(1f)
            )
            ModeSegmentTab(
                title = "OPEN MESH",
                isSelected = selectedMode == GroupingMode.OPEN_MESH,
                onClick = { onSelectMode(GroupingMode.OPEN_MESH) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeSegmentTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) DarkSurfaceElevated else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "segmentBg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) TextPrimary else TextMuted,
        label = "segmentText"
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(1.dp, BorderFocus) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = textColor
            )
        }
    }
}

@Composable
private fun LeadPackContent(
    channel: Int,
    callsign: String,
    hotspotSsid: String?,
    hotspotPassword: String?,
    isHotspotActive: Boolean,
    riderCount: Int,
    onToggleHotspot: () -> Unit
) {
    val qrData = remember(channel, callsign, hotspotSsid, hotspotPassword) {
        RideQrData(
            channel = channel,
            callsign = callsign,
            ssid = hotspotSsid,
            password = hotspotPassword
        )
    }

    val qrBitmap = remember(qrData) {
        RideQrManager.generateQrBitmap(qrData, sizePx = 360)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Hotspot Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isHotspotActive) AccentGreen else AccentAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHotspotActive) "HOTSPOT ACTIVE" else "HOTSPOT STANDBY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHotspotActive) AccentGreen else AccentAmber
                        )
                    }
                    Text(
                        text = hotspotSsid ?: "Synkromesh-Lead",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Switch(
                    checked = isHotspotActive,
                    onCheckedChange = { onToggleHotspot() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Precision QR Frame (Quiet, clean canvas with 1px border)
            Surface(
                modifier = Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Pairing Matrix",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    } else {
                        Text(
                            text = "INITIALIZING MATRIX",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Riders tap JOIN and scan this code to link mesh & audio",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Technical Telemetry Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CHANNEL 0$channel",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = "$riderCount RIDERS IN PACK",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (riderCount > 0) AccentGreen else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun JoinPackContent(
    rideChannel: Int,
    meshState: MeshState,
    onScanQr: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Big Technical Scanner Trigger
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onScanQr() },
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderFocus)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "SCAN LEAD QR",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Instant Zero-Permission Setup",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Technical Telemetry Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SYNCED CHANNEL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "CHANNEL 0$rideChannel",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = when (meshState) {
                        MeshState.CONNECTED -> "CONNECTED"
                        MeshState.SEARCHING -> "SEARCHING..."
                        MeshState.DISCONNECTED -> "STANDBY"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (meshState) {
                        MeshState.CONNECTED -> AccentGreen
                        MeshState.SEARCHING -> AccentAmber
                        MeshState.DISCONNECTED -> TextMuted
                    }
                )
            }
        }
    }
}

@Composable
private fun OpenMeshContent(
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "PUBLIC FREQUENCIES",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (ch in 1..16) {
                    val isSelected = ch == selectedChannel
                    Surface(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectChannel(ch) },
                        color = if (isSelected) TextPrimary else DarkSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isSelected) TextPrimary else BorderSubtle)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (ch < 10) "0$ch" else "$ch",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = if (isSelected) DarkBackground else TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Ad-hoc open mesh without host authentication",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PrecisionActionButton(
    text: String,
    subtext: String,
    isOnline: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "btnScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = TextPrimary,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = DarkBackground
                    )
                }
                Text(
                    text = subtext,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
            }

            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = DarkBackground
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
