package com.landxcape.synkromesh.presentation.intercom.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.presentation.intercom.GroupingMode
import com.landxcape.synkromesh.presentation.intercom.IntercomUiEvent
import com.landxcape.synkromesh.presentation.intercom.IntercomUiState
import com.landxcape.synkromesh.presentation.theme.AccentAmber
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.AccentGreen
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
import kotlinx.coroutines.launch

/**
 * Phase 1: Ride Staging Lobby
 * Swappable PageView.builder-style HorizontalPager with synchronized top tabs.
 * Tab Order: [ OPEN MESH ] [ LEAD PACK ] [ JOIN PACK ].
 * Includes Transmission Mode Selector (PTT / TOGGLE / ALWAYS ON) and bottom-pinned hero CTA.
 */
@Composable
fun RideStagingPage(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Map GroupingMode to Page Index: 0 -> OPEN_MESH, 1 -> LEAD, 2 -> JOIN
    val initialPage = when (uiState.groupingMode) {
        GroupingMode.OPEN_MESH -> 0
        GroupingMode.LEAD -> 1
        GroupingMode.JOIN -> 2
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })

    // Synchronize pager swipe with ViewModel groupingMode
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val targetMode = when (page) {
                0 -> GroupingMode.OPEN_MESH
                1 -> GroupingMode.LEAD
                2 -> GroupingMode.JOIN
                else -> GroupingMode.OPEN_MESH
            }
            if (targetMode != uiState.groupingMode) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onEvent(IntercomUiEvent.SelectGroupingMode(targetMode))
            }
        }
    }

    // Keep pager synced if external state changes groupingMode
    LaunchedEffect(uiState.groupingMode) {
        val targetIndex = when (uiState.groupingMode) {
            GroupingMode.OPEN_MESH -> 0
            GroupingMode.LEAD -> 1
            GroupingMode.JOIN -> 2
        }
        if (pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
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

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Perfectly Aligned Segmented Tabs (OPEN MESH is first)
            BalancedSegmentedTabs(
                selectedPage = pagerState.currentPage,
                onSelectPage = { pageIndex ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pageIndex)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Swappable Horizontal Pager (Gesture-Driven PageView)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(370.dp)
            ) { page ->
                when (page) {
                    0 -> OpenMeshContent(
                        selectedChannel = uiState.rideChannel,
                        onSelectChannel = { ch ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEvent(IntercomUiEvent.SaveRideChannel(ch))
                        }
                    )
                    1 -> LeadPackContent(
                        channel = uiState.rideChannel,
                        callsign = uiState.callsign,
                        hotspotSsid = uiState.hotspotSsid,
                        hotspotPassword = uiState.hotspotPassword,
                        isHotspotActive = uiState.isHotspotActive,
                        riderCount = uiState.riders.size,
                        onToggleHotspot = { onEvent(IntercomUiEvent.ToggleHotspotClicked) }
                    )
                    2 -> JoinPackContent(
                        rideChannel = uiState.rideChannel,
                        meshState = uiState.meshState,
                        onScanQr = onScanQr
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Transmission Mode Selector (PTT / TOGGLE / ALWAYS ON)
            TransmissionModeSection(
                selectedMode = uiState.selectedMode,
                onSelectMode = { mode ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEvent(IntercomUiEvent.ModeChanged(mode))
                }
            )

            // Clearance for bottom pinned button
            Spacer(modifier = Modifier.height(96.dp))
        }

        // 5. Bottom-Pinned Hero Action Bar
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
                    subtext = when (pagerState.currentPage) {
                        0 -> "ENGAGE OPEN MESH // CH 0${uiState.rideChannel}"
                        1 -> "BROADCASTING LEAD PACK // CH 0${uiState.rideChannel}"
                        2 -> "AUTO-SYNC WITH LEAD // CH 0${uiState.rideChannel}"
                        else -> "ENGAGE MESH"
                    },
                    isOnline = pagerState.currentPage == 1 && uiState.isHotspotActive,
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
private fun BalancedSegmentedTabs(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("OPEN MESH", "LEAD PACK", "JOIN PACK")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedPage == index
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) DarkSurfaceElevated else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) TextPrimary else TextMuted,
                    label = "tabText"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelectPage(index) },
                    color = bgColor,
                    shape = RoundedCornerShape(9.dp),
                    border = if (isSelected) BorderStroke(1.dp, BorderFocus) else null
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransmissionModeSection(
    selectedMode: IntercomMode,
    onSelectMode: (IntercomMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "VOICE TRIGGER",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            Text(
                text = when (selectedMode) {
                    IntercomMode.PUSH_TO_TALK -> "HOLD BUTTON TO TALK"
                    IntercomMode.TOGGLE -> "TAP TO LATCH / UNMUTE"
                    IntercomMode.ALWAYS_ON -> "CONTINUOUS OPEN INTERCOM"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            color = DarkSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VoiceModePill(
                    label = "PTT",
                    icon = Icons.Default.TouchApp,
                    isSelected = selectedMode == IntercomMode.PUSH_TO_TALK,
                    onClick = { onSelectMode(IntercomMode.PUSH_TO_TALK) },
                    modifier = Modifier.weight(1f)
                )
                VoiceModePill(
                    label = "TOGGLE",
                    icon = Icons.Default.Mic,
                    isSelected = selectedMode == IntercomMode.TOGGLE,
                    onClick = { onSelectMode(IntercomMode.TOGGLE) },
                    modifier = Modifier.weight(1f)
                )
                VoiceModePill(
                    label = "ALWAYS ON",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    isSelected = selectedMode == IntercomMode.ALWAYS_ON,
                    onClick = { onSelectMode(IntercomMode.ALWAYS_ON) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VoiceModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) TextPrimary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "modeBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) DarkBackground else TextMuted,
        label = "modeColor"
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) null else BorderStroke(1.dp, Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = contentColor
            )
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
                .padding(vertical = 2.dp)
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
private fun OpenMeshContent(
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "OPEN MESH CHANNELS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Channel 0$selectedChannel Active",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }

                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderFocus)
                    ) {
                        Text(
                            text = "NO PAIRING REQ.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "SELECT FREQUENCY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))

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
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = "All riders on Channel 0$selectedChannel talk and listen simultaneously via self-healing peer-to-peer mesh.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Hotspot State
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
                        fontSize = 13.sp,
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

            // Precision QR Frame
            Surface(
                modifier = Modifier
                    .size(175.dp)
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
                                .padding(8.dp)
                        )
                    } else {
                        Text(
                            text = "INITIALIZING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            // Live Telemetry Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CH 0$channel",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = "$riderCount RIDERS JOINED",
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
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "JOIN PACK INTERCOM",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scan the Lead Rider's pairing matrix to instantly join their private Wi-Fi mesh and sync channel.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Big Technical Scanner Trigger
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
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
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "SCAN LEAD QR",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Zero-Permission Instant Scan",
                                fontSize = 11.sp,
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

            // Telemetry Footer
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
                        text = "LAST SYNCED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "CHANNEL 0$rideChannel",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
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
