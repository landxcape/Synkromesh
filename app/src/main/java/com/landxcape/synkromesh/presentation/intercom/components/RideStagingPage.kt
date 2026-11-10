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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.DarkSurface
import com.landxcape.synkromesh.presentation.theme.DarkSurfaceElevated
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary
import com.landxcape.synkromesh.presentation.theme.TextSecondary
import com.landxcape.synkromesh.util.RideQrData
import com.landxcape.synkromesh.util.RideQrManager

/**
 * Phase 1: Ride Staging Lobby (Pre-Ride Stationary Context)
 * Provides high-contrast, glove-friendly grouping setup and master "ENGAGE INTERCOM" action.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Identity & Audio Routing Pill
        RiderIdentityPill(
            callsign = uiState.callsign,
            isScoConnected = uiState.audioRouting.isScoConnected,
            headsetName = uiState.audioRouting.connectedDeviceName,
            onEditCallsign = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(true)) },
            onToggleSco = { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Section Header: Grouping Mode
        Text(
            text = "RIDE GROUPING SETUP",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = TextSecondary,
            letterSpacing = 1.5.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Option A: LEAD A PACK (Host / Hotspot Router)
        GroupingCard(
            title = "LEAD A PACK",
            subtitle = "Host Wi-Fi Hotspot & Share Pairing QR",
            badgeText = "PACK LEADER",
            badgeColor = AccentAmber,
            icon = Icons.Default.CellTower,
            isSelected = uiState.groupingMode == GroupingMode.LEAD,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEvent(IntercomUiEvent.SelectGroupingMode(GroupingMode.LEAD))
            }
        ) {
            LeadPackDetails(
                channel = uiState.rideChannel,
                callsign = uiState.callsign,
                hotspotSsid = uiState.hotspotSsid,
                hotspotPassword = uiState.hotspotPassword,
                isHotspotActive = uiState.isHotspotActive,
                riderCount = uiState.riders.size,
                onToggleHotspot = { onEvent(IntercomUiEvent.ToggleHotspotClicked) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Option B: JOIN A PACK (Follower / QR Scanner)
        GroupingCard(
            title = "JOIN A PACK",
            subtitle = "Scan Lead Rider's QR Code Instantly",
            badgeText = "WINGMAN",
            badgeColor = AccentCyan,
            icon = Icons.Default.QrCodeScanner,
            isSelected = uiState.groupingMode == GroupingMode.JOIN,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEvent(IntercomUiEvent.SelectGroupingMode(GroupingMode.JOIN))
            }
        ) {
            JoinPackDetails(
                rideChannel = uiState.rideChannel,
                meshState = uiState.meshState,
                onScanQr = onScanQr
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Option C: OPEN MESH (Free Roam Ad-hoc Channels 1–16)
        GroupingCard(
            title = "OPEN MESH",
            subtitle = "Public Frequency Channels 1–16",
            badgeText = "FREE ROAM",
            badgeColor = AccentGreen,
            icon = Icons.Default.Radio,
            isSelected = uiState.groupingMode == GroupingMode.OPEN_MESH,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEvent(IntercomUiEvent.SelectGroupingMode(GroupingMode.OPEN_MESH))
            }
        ) {
            OpenMeshChannelSelector(
                selectedChannel = uiState.rideChannel,
                onSelectChannel = { ch ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onEvent(IntercomUiEvent.SaveRideChannel(ch))
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 6. Master Engage Action: Giant Glowing Pill
        EngageIntercomButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEvent(IntercomUiEvent.EngageIntercom)
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun RiderIdentityPill(
    callsign: String,
    isScoConnected: Boolean,
    headsetName: String?,
    onEditCallsign: () -> Unit,
    onToggleSco: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Callsign
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEditCallsign() }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callsign.take(1).uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = AccentCyan
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "CALLSIGN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = callsign,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Callsign",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Right: Headset SCO status
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleSco() },
                color = if (isScoConnected) AccentGreen.copy(alpha = 0.15f) else DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isScoConnected) AccentGreen.copy(alpha = 0.5f) else TextMuted.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isScoConnected) Icons.Default.Headset else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (isScoConnected) AccentGreen else AccentAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScoConnected) {
                            (headsetName ?: "BT HEADSET").take(12)
                        } else {
                            "PHONE MIC"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isScoConnected) AccentGreen else AccentAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupingCard(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) badgeColor else DarkSurface,
        label = "borderColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = subtitle,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LeadPackDetails(
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
        RideQrManager.generateQrBitmap(qrData, sizePx = 400)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hotspot Status Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            shape = RoundedCornerShape(14.dp)
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
                        text = if (isHotspotActive) "HOTSPOT ACTIVE" else "HOTSPOT OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isHotspotActive) AccentGreen else AccentAmber
                    )
                    Text(
                        text = hotspotSsid ?: "Synkromesh-Lead",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Switch(
                    checked = isHotspotActive,
                    onCheckedChange = { onToggleHotspot() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentGreen,
                        checkedTrackColor = AccentGreen.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Embedded QR Code Matrix
        Surface(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, AccentAmber)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Lead Rider Pairing QR",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                } else {
                    Text(
                        text = "Generating QR...",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tell riders to tap 'JOIN A PACK' and scan this QR code",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Live Joined Counter
        Surface(
            color = if (riderCount > 0) AccentGreen.copy(alpha = 0.15f) else DarkSurface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (riderCount > 0) AccentGreen.copy(alpha = 0.5f) else TextMuted.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (riderCount > 0) AccentGreen else AccentAmber)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$riderCount RIDERS JOINED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (riderCount > 0) AccentGreen else AccentAmber
                )
            }
        }
    }
}

@Composable
private fun JoinPackDetails(
    rideChannel: Int,
    meshState: MeshState,
    onScanQr: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onScanQr,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan,
                contentColor = DarkBackground
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SCAN LEAD RIDER QR",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "CURRENT CHANNEL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "Channel $rideChannel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }

                Surface(
                    color = when (meshState) {
                        MeshState.CONNECTED -> AccentGreen.copy(alpha = 0.15f)
                        MeshState.SEARCHING -> AccentAmber.copy(alpha = 0.15f)
                        MeshState.DISCONNECTED -> TextMuted.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (meshState) {
                            MeshState.CONNECTED -> "CONNECTED"
                            MeshState.SEARCHING -> "SEARCHING"
                            MeshState.DISCONNECTED -> "STANDBY"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = when (meshState) {
                            MeshState.CONNECTED -> AccentGreen
                            MeshState.SEARCHING -> AccentAmber
                            MeshState.DISCONNECTED -> TextMuted
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenMeshChannelSelector(
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "SELECT PUBLIC FREQUENCY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

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
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectChannel(ch) },
                    color = if (isSelected) AccentGreen else DarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (isSelected) AccentGreen else DarkSurfaceElevated
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$ch",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = if (isSelected) DarkBackground else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngageIntercomButton(
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = AccentGreen)
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() },
        color = Color.Transparent,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.dp, AccentGreen.copy(alpha = glowAlpha))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AccentGreen.copy(alpha = 0.9f),
                            AccentCyan.copy(alpha = 0.85f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ENGAGE INTERCOM",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = DarkBackground
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
