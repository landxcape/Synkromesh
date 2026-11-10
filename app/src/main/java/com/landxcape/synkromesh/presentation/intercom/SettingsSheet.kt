package com.landxcape.synkromesh.presentation.intercom

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.data.datasource.bluetooth.PairedHeadset
import com.landxcape.synkromesh.domain.model.AudioRoutingState
import com.landxcape.synkromesh.presentation.theme.AccentAmber
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.AccentGreen
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.DarkSurface
import com.landxcape.synkromesh.presentation.theme.DarkSurfaceElevated
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary
import com.landxcape.synkromesh.presentation.theme.TextSecondary
import com.landxcape.synkromesh.util.PreflightStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    callsign: String,
    rideChannel: Int,
    vadThreshold: Float,
    opusBitrate: Int,
    isLoopbackActive: Boolean,
    isSpeakerphoneOn: Boolean,
    audioRouting: AudioRoutingState,
    pairedHeadsets: List<PairedHeadset>,
    preflightStatus: PreflightStatus,
    onOpenCallsignDialog: () -> Unit,
    onSelectChannel: (Int) -> Unit,
    onVadThresholdChanged: (Float) -> Unit,
    onOpusBitrateChanged: (Int) -> Unit,
    onToggleLoopback: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onToggleSco: () -> Unit,
    onOpenPermissions: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceElevated,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intercom Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Rider Profile Card
            SettingsSectionHeader(title = "Rider Profile")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCallsignDialog() },
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Callsign", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(text = callsign, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Callsign", tint = AccentCyan)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Ride Mesh Channel Selector
            SettingsSectionHeader(title = "Ride Mesh Channel")
            Text(
                text = "Only riders on the exact same Channel will hear and speak with each other.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (ch in 1..8) {
                    val isSelected = ch == rideChannel
                    Surface(
                        modifier = Modifier.clickable { onSelectChannel(ch) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentCyan else DarkSurface
                    ) {
                        Text(
                            text = "CH $ch",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (isSelected) DarkBackground else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Audio & Noise Engine
            SettingsSectionHeader(title = "Audio Engine & Voice Filter")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Loudspeaker Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Loud Speakerphone", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(
                                text = "Play through high-volume phone speaker when no helmet is connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = isSpeakerphoneOn,
                            onCheckedChange = { onToggleSpeakerphone() },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "VAD Noise Gate Sensitivity: ${(vadThreshold * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Higher threshold filters out engine exhaust and wind noise in Always-On mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = vadThreshold,
                        onValueChange = onVadThresholdChanged,
                        valueRange = 0.01f..0.30f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = DarkBackground
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Opus Codec Bitrate",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(16 to "16 kbps (Fast)", 24 to "24 kbps (HD)", 32 to "32 kbps (Ultra)").forEach { (rate, label) ->
                            val isSelected = rate == opusBitrate
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onOpusBitrateChanged(rate) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AccentCyan else DarkBackground
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = if (isSelected) DarkBackground else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Mic Loopback Test", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Hear your own mic live to test helmet speakers", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = isLoopbackActive,
                            onCheckedChange = { onToggleLoopback() },
                            colors = SwitchDefaults.colors(checkedThumbColor = AccentCyan, checkedTrackColor = AccentCyan.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Bluetooth Helmet Headsets
            SettingsSectionHeader(title = "Helmet Bluetooth Headsets")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCO Helmet Routing: ${if (audioRouting.isScoConnected) "ACTIVE" else "OFF"}",
                            fontWeight = FontWeight.Bold,
                            color = if (audioRouting.isScoConnected) AccentGreen else AccentAmber
                        )
                        Button(
                            onClick = onToggleSco,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (audioRouting.isScoConnected) AccentAmber else AccentGreen,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (audioRouting.isScoConnected) "Disconnect" else "Connect SCO", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (pairedHeadsets.isEmpty()) {
                        Text(
                            text = "No paired Bluetooth audio devices found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        Text(
                            text = "Paired Audio Devices:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        pairedHeadsets.forEach { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (dev.isAudioDevice) AccentCyan else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pair New Helmet in Bluetooth Settings", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Hardware Diagnostics & Permissions
            SettingsSectionHeader(title = "Hardware & Permissions")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPermissions() },
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "System Status: ${if (preflightStatus.isReadyForIntercom) "READY" else "ACTION REQUIRED"}",
                            fontWeight = FontWeight.Bold,
                            color = if (preflightStatus.isReadyForIntercom) AccentGreen else AccentAmber
                        )
                        Text(
                            text = "Mic, Bluetooth, Wi-Fi, GPS, Notifications",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Icon(imageVector = Icons.Default.Security, contentDescription = "Review Permissions", tint = AccentCyan)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = AccentCyan,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
