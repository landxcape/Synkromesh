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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.landxcape.synkromesh.domain.model.HelmetAcousticProfile
import com.landxcape.synkromesh.domain.model.HotspotClientState
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

    // Map GroupingMode to Page Index: 0 -> AUDIO, 1 -> OPEN_MESH, 2 -> LEAD, 3 -> JOIN
    val initialPage = when (uiState.groupingMode) {
        GroupingMode.OPEN_MESH -> 0 // Open on Audio & Preflight by default for hardware readiness
        GroupingMode.LEAD -> 2
        GroupingMode.JOIN -> 3
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 4 })

    // Synchronize pager swipe with ViewModel groupingMode ONLY when page settles smoothly
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val targetMode = when (page) {
                0, 1 -> GroupingMode.OPEN_MESH
                2 -> GroupingMode.LEAD
                3 -> GroupingMode.JOIN
                else -> GroupingMode.OPEN_MESH
            }
            if (targetMode != uiState.groupingMode) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onEvent(IntercomUiEvent.SelectGroupingMode(targetMode))
            }
        }
    }

    // Keep pager synced if external state changes groupingMode (e.g. from QR scan)
    LaunchedEffect(uiState.groupingMode) {
        val expectedPage = when (uiState.groupingMode) {
            GroupingMode.OPEN_MESH -> if (pagerState.currentPage in 0..1) pagerState.currentPage else 1
            GroupingMode.LEAD -> 2
            GroupingMode.JOIN -> 3
        }
        if (pagerState.settledPage != expectedPage && pagerState.targetPage != expectedPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(expectedPage)
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
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Technical Header Bar
                TechnicalHeaderBar(
                    callsign = uiState.callsign,
                    isScoConnected = uiState.audioRouting.isScoConnected,
                    headsetName = uiState.audioRouting.connectedDeviceName,
                    onEditCallsign = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(true)) },
                    onToggleSco = { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) },
                    onOpenPreflight = { onEvent(IntercomUiEvent.SetPermissionSheetVisible(true)) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Perfectly Aligned 4-Segment Tabs
                BalancedSegmentedTabs(
                    selectedPage = pagerState.targetPage,
                    onSelectPage = { pageIndex ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pageIndex)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Swappable Horizontal Pager (Full-bleed viewport; pages own their horizontal padding)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> AudioHardwareContent(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                    1 -> OpenMeshContent(
                        selectedChannel = uiState.rideChannel,
                        onSelectChannel = { ch ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onEvent(IntercomUiEvent.SaveRideChannel(ch))
                        }
                    )
                    2 -> LeadPackContent(
                        channel = uiState.rideChannel,
                        callsign = uiState.callsign,
                        hotspotSsid = uiState.hotspotSsid,
                        hotspotPassword = uiState.hotspotPassword,
                        packToken = uiState.packToken,
                        isHotspotActive = uiState.isHotspotActive,
                        isHotspotStarting = uiState.isHotspotStarting,
                        riderCount = uiState.riders.size,
                        onToggleHotspot = { onEvent(IntercomUiEvent.ToggleHotspotClicked) }
                    )
                    3 -> JoinPackContent(
                        rideChannel = uiState.rideChannel,
                        meshState = uiState.meshState,
                        clientState = uiState.hotspotClientState,
                        connectedSsid = uiState.connectedHotspotSsid,
                        onScanQr = onScanQr,
                        onDisconnect = { onEvent(IntercomUiEvent.DisconnectHotspotClicked) }
                    )
                }
            }

            // Clearance for bottom pinned launch action button
            Spacer(modifier = Modifier.height(78.dp))
        }

        // 4. Bottom-Pinned Hero Action Console
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = DarkBackground.copy(alpha = 0.98f),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                PrecisionActionButton(
                    text = "START INTERCOM",
                    subtext = when (pagerState.targetPage) {
                        0 -> "ENGAGE MESH // AUDIO PREFLIGHT READY"
                        1 -> "ENGAGE OPEN MESH // CH 0${uiState.rideChannel}"
                        2 -> "BROADCASTING LEAD PACK // CH 0${uiState.rideChannel}"
                        3 -> "AUTO-SYNC WITH LEAD // CH 0${uiState.rideChannel}"
                        else -> "ENGAGE MESH"
                    },
                    isOnline = pagerState.targetPage == 2 && uiState.isHotspotActive,
                    isLoading = uiState.isSessionEngaging,
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
            val tabs = listOf("AUDIO", "OPEN MESH", "LEAD PACK", "JOIN PACK")
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
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            letterSpacing = 0.2.sp,
                            color = textColor
                        )
                    }
                }
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
    onToggleSco: () -> Unit,
    onOpenPreflight: () -> Unit
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

        // Right: Hardware SCO Audio Pill + Preflight Settings Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleSco() },
                color = DarkSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isScoConnected) BorderFocus else BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                            (headsetName ?: "BT HEADSET").take(12).uppercase()
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

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenPreflight() },
                color = DarkSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Preflight Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioHardwareContent(
    uiState: IntercomUiState,
    onEvent: (IntercomUiEvent) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            // Title & Preflight Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HARDWARE PREFLIGHT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onEvent(IntercomUiEvent.SetPermissionSheetVisible(true)) },
                    color = if (uiState.preflightStatus.isReadyForIntercom) AccentGreen.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (uiState.preflightStatus.isReadyForIntercom) AccentGreen else AccentAmber)
                ) {
                    Text(
                        text = if (uiState.preflightStatus.isReadyForIntercom) "SYSTEM READY" else "PERMISSIONS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.preflightStatus.isReadyForIntercom) AccentGreen else AccentAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 1. Live VU Mic Audio Level Meter
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE MIC INPUT LEVEL",
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
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
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
            }

            // 2. Audio Routing Cards: Bluetooth SCO & Speakerphone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bluetooth SCO Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEvent(IntercomUiEvent.ToggleBluetoothScoClicked) },
                    color = if (uiState.audioRouting.isScoConnected) DarkSurfaceElevated else DarkBackground,
                    shape = RoundedCornerShape(10.dp),
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
                                text = "BLUETOOTH",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.audioRouting.isScoConnected) AccentGreen else TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.audioRouting.isScoConnected) {
                                (uiState.audioRouting.connectedDeviceName ?: "HELMET HEADSET").take(12)
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
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEvent(IntercomUiEvent.ToggleSpeakerphoneClicked) },
                    color = if (uiState.isSpeakerphoneOn) DarkSurfaceElevated else DarkBackground,
                    shape = RoundedCornerShape(10.dp),
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

            // 3. Helmet Acoustic Profile & Dynamic Speed Gain Boost Card (Nepali Calibrated)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)),
                color = DarkBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            text = String.format("%.1fx SPEED GAIN", uiState.dynamicGain),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.dynamicGain > 1.05f) AccentAmber else AccentCyan
                        )
                    }

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
                                color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else DarkSurface,
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${profile.opusBitrateKbps}k",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "⚡ Nepali Road Curve: 0..20 km/h: 1.0x (0 dB) • 20..45 km/h: +3 dB • 45..70+ km/h: +6 dB max",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
            }

            // 4. Audio Loopback Self-Test Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEvent(IntercomUiEvent.ToggleLoopbackClicked) },
                color = if (uiState.isLoopbackActive) AccentGreen.copy(alpha = 0.15f) else DarkBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (uiState.isLoopbackActive) AccentGreen else BorderSubtle)
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
                                .background(if (uiState.isLoopbackActive) AccentGreen else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "LOOPBACK SELF-TEST",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (uiState.isLoopbackActive) "Echoing mic to headset in real-time" else "Tap to verify audio pipeline & latency",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Text(
                        text = if (uiState.isLoopbackActive) "STOP" else "TEST",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (uiState.isLoopbackActive) AccentGreen else TextPrimary
                    )
                }
            }

            // 4. DSP Status Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AEC // NS // AGC : ACTIVE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = AccentGreen
                )
                Text(
                    text = "OPUS 48kHz CVBR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            // 5. Network & Power Diagnostics Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)),
                color = DarkBackground,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NETWORK & POWER DIAGNOSTICS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )

                        Text(
                            text = if (uiState.isLowLatencyPowerMode) "LOW LATENCY // PERFORMANCE" else "ECO POWER // 45s IDLE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isLowLatencyPowerMode) AccentGreen else AccentAmber
                        )
                    }

                    val remoteRiders = uiState.riders.filter { !it.isLocalUser }
                    if (remoteRiders.isEmpty()) {
                        Text(
                            text = "No active peers // Standing by for mesh traffic",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (rider in remoteRiders) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rider.callsign.uppercase(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "RTT ${rider.latencyMs}ms",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGreen
                                        )
                                        Text(
                                            text = "LOSS ${rider.packetLossPercent.toInt()}%",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rider.packetLossPercent > 10f) AccentAmber else TextSecondary
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
private fun OpenMeshContent(
    selectedChannel: Int,
    onSelectChannel: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            text = if (selectedChannel < 10) "Channel 0$selectedChannel Active" else "Channel $selectedChannel Active",
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

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SELECT FREQUENCY",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Ergonomic 4x4 Grid - Zero horizontal scroll, glove-friendly touch targets
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (rowIndex in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (colIndex in 0 until 4) {
                                val ch = rowIndex * 4 + colIndex + 1
                                val isSelected = ch == selectedChannel
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
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
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = "All riders on Channel ${if (selectedChannel < 10) "0$selectedChannel" else "$selectedChannel"} talk and listen simultaneously via self-healing peer-to-peer mesh.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
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
    packToken: String?,
    isHotspotActive: Boolean,
    isHotspotStarting: Boolean,
    riderCount: Int,
    onToggleHotspot: () -> Unit
) {
    val scrollState = rememberScrollState()
    val qrData = remember(channel, callsign, hotspotSsid, hotspotPassword, packToken, isHotspotActive) {
        if (!isHotspotActive || hotspotSsid.isNullOrBlank()) null
        else RideQrData(
            channel = channel,
            callsign = callsign,
            ssid = hotspotSsid,
            password = hotspotPassword,
            packToken = packToken
        )
    }

    val qrBitmap = remember(qrData) {
        qrData?.let { RideQrManager.generateQrBitmap(it, sizePx = 720) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                .background(
                                    when {
                                        isHotspotStarting -> AccentAmber
                                        isHotspotActive -> AccentGreen
                                        else -> TextMuted
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isHotspotStarting -> "HOTSPOT INITIALIZING..."
                                isHotspotActive -> "HOTSPOT BROADCASTING"
                                else -> "HOTSPOT OFF"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isHotspotStarting -> AccentAmber
                                isHotspotActive -> AccentGreen
                                else -> TextMuted
                            }
                        )
                    }
                    Text(
                        text = when {
                            isHotspotStarting -> "Configuring SoftAP..."
                            isHotspotActive -> (hotspotSsid ?: "Broadcasting...")
                            else -> "Standby (Tap to Start)"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Switch(
                    checked = isHotspotActive || isHotspotStarting,
                    enabled = !isHotspotStarting,
                    onCheckedChange = { onToggleHotspot() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = if (isHotspotStarting) AccentAmber else AccentGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isHotspotStarting) {
                // Initializing / Loading Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AccentAmber)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = AccentAmber,
                            strokeWidth = 3.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "INITIALIZING LEAD HOTSPOT...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Allocating local Wi-Fi SoftAP and generating pack pairing matrix. Please wait...",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (!isHotspotActive) {
                // Inactive Standby Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleHotspot() },
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderFocus)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "START LEAD HOTSPOT",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Broadcast private Wi-Fi hotspot to generate your pack's pairing matrix and invite riders.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Tactile Launch Button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = AccentGreen,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ACTIVATE HOTSPOT",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            } else {
                // Precision QR Frame - Full Width Adaptive Aspect Ratio
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
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
                                fontSize = 11.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                    text = if (channel < 10) "CH 0$channel" else "CH $channel",
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

@Composable
private fun JoinPackContent(
    rideChannel: Int,
    meshState: MeshState,
    clientState: HotspotClientState,
    connectedSsid: String?,
    onScanQr: () -> Unit,
    onDisconnect: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

            when (clientState) {
                HotspotClientState.CONNECTING -> {
                    // Connecting / Associating Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AccentAmber)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                color = AccentAmber,
                                strokeWidth = 3.dp
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "ASSOCIATING WITH LEAD PACK...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Connecting to ${connectedSsid ?: "Lead Hotspot"}...\nNegotiating Wi-Fi handshake and directed subnet routing.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                HotspotClientState.CONNECTED -> {
                    // Connected Status Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AccentGreen)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "CONNECTED TO LEAD PACK",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = AccentGreen
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Active Wi-Fi: ${connectedSsid ?: "Lead Hotspot"}\nPress START INTERCOM below to engage mesh audio.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onDisconnect() },
                                    color = DarkSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "DISCONNECT",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onScanQr() },
                                    color = DarkSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderFocus)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "SCAN ANOTHER",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HotspotClientState.FAILED -> {
                    // Failed Status Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AccentAmber)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = AccentAmber,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "CONNECTION TIMED OUT",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Could not associate with ${connectedSsid ?: "Lead Hotspot"}. Verify Leader hotspot is running.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onScanQr() },
                                color = AccentAmber,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "RETRY SCAN",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
                HotspotClientState.IDLE -> {
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
                    text = when (clientState) {
                        HotspotClientState.CONNECTING -> "ASSOCIATING..."
                        HotspotClientState.CONNECTED -> "WI-FI LINKED"
                        HotspotClientState.FAILED -> "LINK TIMEOUT"
                        HotspotClientState.IDLE -> when (meshState) {
                            MeshState.CONNECTED -> "CONNECTED"
                            MeshState.SEARCHING -> "SEARCHING..."
                            MeshState.DISCONNECTED -> "AWAITING SCAN"
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (clientState) {
                        HotspotClientState.CONNECTING -> AccentAmber
                        HotspotClientState.CONNECTED -> AccentGreen
                        HotspotClientState.FAILED -> AccentAmber
                        HotspotClientState.IDLE -> when (meshState) {
                            MeshState.CONNECTED -> AccentGreen
                            MeshState.SEARCHING -> AccentAmber
                            MeshState.DISCONNECTED -> TextMuted
                        }
                    }
                )
            }
        }
}

@Composable
private fun PrecisionActionButton(
    text: String,
    subtext: String,
    isOnline: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isLoading) 0.97f else 1.0f,
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
                enabled = !isLoading,
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
                    if (isOnline && !isLoading) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isLoading) "CONNECTING..." else text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = DarkBackground
                    )
                }
                Text(
                    text = if (isLoading) "ESTABLISHING AUDIO & MESH UPLINK" else subtext,
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
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TextPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
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
}
