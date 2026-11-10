package com.landxcape.synkromesh.presentation.intercom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.Rider
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
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSetupSheet(
    isSessionActive: Boolean,
    meshState: MeshState,
    rideChannel: Int,
    riders: List<Rider>,
    isHotspotActive: Boolean,
    hotspotSsid: String?,
    hotspotPassword: String?,
    onSelectChannel: (Int) -> Unit,
    onToggleSession: () -> Unit,
    onToggleHotspot: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ride Mesh & Radar Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    Text(
                        text = "Real-time P2P rider discovery & distance meter",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel Quick Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT RIDE CHANNEL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = "Channel $rideChannel Active",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf(1, 2, 3, 4, 5, 6, 7, 8)) { ch ->
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
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Interactive Rider Radar View
            RiderRadarCanvas(
                riders = riders,
                isScanning = isSessionActive && meshState == MeshState.SEARCHING
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Rider Distance Status Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISCOVERED RIDERS (${riders.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = meshState.statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (meshState) {
                                MeshState.CONNECTED -> AccentGreen
                                MeshState.SEARCHING -> AccentAmber
                                MeshState.DISCONNECTED -> TextMuted
                            }
                        )
                    }
                    if (riders.isEmpty()) {
                        Text(
                            text = if (isSessionActive) "Scanning for riders on Channel $rideChannel..." else "Mesh is idle. Tap 'Start Ride' below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else {
                        riders.forEach { rider ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AccentGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = rider.callsign,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${rider.bearingArrow} ${rider.formattedDistance} • ${rider.relativePosition ?: "Direct"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan
                                    )
                                    Text(
                                        text = "🏎️ ${rider.formattedSpeed}  ⛰️ ${rider.formattedAltitude}  🔋 ${rider.formattedBattery}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lead Rider Hotspot Card (Guaranteed Zero-Data Road LAN)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = if (isHotspotActive) AccentGreen else AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Lead Rider: Host Hotspot",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Creates an offline Wi-Fi LAN on the road",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Button(
                            onClick = onToggleHotspot,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHotspotActive) AccentGreen else AccentCyan,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isHotspotActive) "ACTIVE" else "HOST",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isHotspotActive && hotspotSsid != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkBackground
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Hotspot SSID: $hotspotSsid",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen,
                                    fontSize = 12.sp
                                )
                                hotspotPassword?.let { pwd ->
                                    Text(
                                        text = "Password: $pwd",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "Other riders: Connect your phone to this Wi-Fi network for <15ms audio latency.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Action Button
            Button(
                onClick = onToggleSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSessionActive) AccentRed else AccentGreen,
                    contentColor = DarkBackground
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSessionActive) "LEAVE MESH SESSION" else "START RIDE / CONNECT MESH",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RiderRadarCanvas(
    riders: List<Rider>,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(DarkBackground)
            .border(2.dp, DarkSurface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width / 2

            // Concentric Rings: 15m, 30m, 60m, 100m
            val ringFractions = listOf(0.25f, 0.50f, 0.75f, 1.0f)
            ringFractions.forEach { frac ->
                drawCircle(
                    color = AccentCyan.copy(alpha = 0.15f),
                    radius = maxRadius * frac,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Crosshairs
            drawLine(
                color = AccentCyan.copy(alpha = 0.10f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = AccentCyan.copy(alpha = 0.10f),
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Radar Scanning Sweep
            if (isScanning) {
                val rad = Math.toRadians(sweepAngle.toDouble())
                val sweepEnd = Offset(
                    (center.x + maxRadius * cos(rad)).toFloat(),
                    (center.y + maxRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = AccentCyan.copy(alpha = 0.6f),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Center Point (You / Local Rider)
            drawCircle(
                color = AccentCyan,
                radius = 6.dp.toPx(),
                center = center
            )

            // Plot Discovered Riders
            riders.forEachIndexed { index, rider ->
                val dist = rider.estimatedDistanceMeters ?: 30f
                val clampedDistFraction = (dist / 100f).coerceIn(0.15f, 0.92f)
                val peerRadius = maxRadius * clampedDistFraction

                // Use relative bearing or distribute around circle
                val angleDeg = rider.relativeBearingDegrees ?: (index * (360f / riders.size.coerceAtLeast(1)))
                val canvasAngleRad = Math.toRadians((angleDeg - 90f).toDouble())

                val peerPos = Offset(
                    (center.x + peerRadius * cos(canvasAngleRad)).toFloat(),
                    (center.y + peerRadius * sin(canvasAngleRad)).toFloat()
                )

                // Draw Rider Blip
                drawCircle(
                    color = if (rider.isTransmitting) AccentGreen else AccentAmber,
                    radius = 5.dp.toPx(),
                    center = peerPos
                )
            }
        }

        // Concentric Distance Labels
        Text(
            text = "15m",
            fontSize = 9.sp,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 66.dp)
        )
        Text(
            text = "30m",
            fontSize = 9.sp,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp)
        )
        Text(
            text = "60m+",
            fontSize = 9.sp,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
        )
    }
}
