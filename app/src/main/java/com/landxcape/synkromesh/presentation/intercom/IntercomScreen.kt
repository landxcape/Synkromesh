package com.landxcape.synkromesh.presentation.intercom

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.landxcape.synkromesh.presentation.intercom.components.DeviceSettingsPage
import com.landxcape.synkromesh.presentation.intercom.components.FloatingBottomDock
import com.landxcape.synkromesh.presentation.intercom.components.IntercomCockpitPage
import com.landxcape.synkromesh.presentation.intercom.components.RadarFormationPage
import com.landxcape.synkromesh.presentation.intercom.components.RideQrDialog
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.DarkSurface
import com.landxcape.synkromesh.presentation.theme.DarkSurfaceElevated
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary
import com.landxcape.synkromesh.presentation.theme.TextSecondary
import com.landxcape.synkromesh.service.IntercomForegroundService
import com.landxcape.synkromesh.util.RideQrData
import kotlinx.coroutines.launch

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
 * Pragmatic Layout: Orchestrates stateless child components with Pager, QR scanner, and Floating Dock.
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
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val preflightSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isChannelPickerOpen by remember { mutableStateOf(false) }
    var isQrDialogOpen by remember { mutableStateOf(false) }

    val launchQrScanner: () -> Unit = {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue ?: return@addOnSuccessListener
                    val data = RideQrData.fromUriString(rawValue)
                    if (data != null) {
                        onEvent(IntercomUiEvent.SaveRideChannel(data.channel))
                        if (!uiState.isSessionActive) {
                            onEvent(IntercomUiEvent.ToggleSessionClicked)
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Joined Channel ${data.channel} (Host: ${data.callsign})")
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid Synkromesh QR Code")
                        }
                    }
                }
                .addOnFailureListener {
                    // Scanner dismissed or canceled
                }
        } catch (e: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("QR Scanner unavailable: ${e.message}")
            }
        }
        Unit
    }

    // Bridge legacy sheet triggers to smooth page scroll
    LaunchedEffect(uiState.isSettingsOpen) {
        if (uiState.isSettingsOpen) {
            pagerState.animateScrollToPage(2)
            onEvent(IntercomUiEvent.SetSettingsVisible(false))
        }
    }

    LaunchedEffect(uiState.isConnectionSetupOpen) {
        if (uiState.isConnectionSetupOpen) {
            pagerState.animateScrollToPage(1)
            onEvent(IntercomUiEvent.SetConnectionSetupVisible(false))
        }
    }

    // Callsign Dialog
    if (uiState.isCallsignDialogOpen) {
        CallsignEditDialog(
            currentCallsign = uiState.callsign,
            onDismiss = { onEvent(IntercomUiEvent.SetCallsignDialogVisible(false)) },
            onConfirm = { onEvent(IntercomUiEvent.SaveCallsign(it)) }
        )
    }

    // Pairing QR Dialog
    if (isQrDialogOpen) {
        RideQrDialog(
            channel = uiState.rideChannel,
            callsign = uiState.callsign,
            hotspotSsid = uiState.hotspotSsid,
            hotspotPassword = uiState.hotspotPassword,
            onDismiss = { isQrDialogOpen = false }
        )
    }

    // Permission Preflight Bottom Sheet
    if (uiState.isPermissionSheetVisible) {
        PermissionPreflightSheet(
            status = uiState.preflightStatus,
            requiredPermissions = requiredPermissions,
            onDismiss = { onEvent(IntercomUiEvent.SetPermissionSheetVisible(false)) },
            onRefresh = { onEvent(IntercomUiEvent.RefreshPreflightAndDevices) },
            sheetState = preflightSheetState
        )
    }

    // Ride Channel Picker Bottom Sheet
    if (isChannelPickerOpen) {
        ChannelPickerSheet(
            currentChannel = uiState.rideChannel,
            onSelectChannel = { channel ->
                onEvent(IntercomUiEvent.SaveRideChannel(channel))
            },
            onShowQr = {
                isChannelPickerOpen = false
                isQrDialogOpen = true
            },
            onScanQr = {
                isChannelPickerOpen = false
                launchQrScanner()
            },
            onDismiss = { isChannelPickerOpen = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            FloatingBottomDock(
                currentPage = pagerState.currentPage,
                onSelectPage = { pageIndex ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pageIndex)
                    }
                },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> IntercomCockpitPage(
                        uiState = uiState,
                        onEvent = onEvent,
                        onOpenChannelPicker = { isChannelPickerOpen = true },
                        onShowQr = { isQrDialogOpen = true },
                        onScanQr = launchQrScanner
                    )
                    1 -> RadarFormationPage(
                        riders = uiState.riders,
                        meshState = uiState.meshState,
                        mutedPeerIds = uiState.mutedPeerIds,
                        onToggleMutePeer = { onEvent(IntercomUiEvent.TogglePeerMute(it)) }
                    )
                    2 -> DeviceSettingsPage(
                        uiState = uiState,
                        onEvent = onEvent,
                        onRequestBatteryExemption = onRequestBatteryExemption,
                        onShowQr = { isQrDialogOpen = true }
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelPickerSheet(
    currentChannel: Int,
    onSelectChannel: (Int) -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SELECT RIDE CHANNEL",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = "Only riders on the same channel hear each other",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            // QR Fast Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onShowQr,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBackground,
                        contentColor = AccentCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SHARE QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onScanQr,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SCAN QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channels 1 to 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (1..4).forEach { ch ->
                    ChannelPickerButton(
                        channel = ch,
                        isSelected = ch == currentChannel,
                        onClick = {
                            onSelectChannel(ch)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Channels 5 to 8
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (5..8).forEach { ch ->
                    ChannelPickerButton(
                        channel = ch,
                        isSelected = ch == currentChannel,
                        onClick = {
                            onSelectChannel(ch)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ChannelPickerButton(
    channel: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) AccentCyan else DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) AccentCyan else TextMuted.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "CH $channel",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) DarkBackground else TextPrimary
            )
        }
    }
}
