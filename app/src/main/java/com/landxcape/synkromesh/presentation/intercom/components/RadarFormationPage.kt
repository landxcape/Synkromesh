package com.landxcape.synkromesh.presentation.intercom.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

@Composable
fun RadarFormationPage(
    riders: List<Rider>,
    meshState: MeshState,
    mutedPeerIds: Set<String>,
    onToggleMutePeer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Formation Header Summary
        FormationHeader(riders = riders, meshState = meshState)

        Spacer(modifier = Modifier.height(10.dp))

        // 2. 2D Compass Radar View
        RadarCanvasWidget(
            riders = riders,
            isScanning = meshState == MeshState.SEARCHING
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Scrollable Rider Telemetry Cards
        Text(
            text = "CONNECTED RIDERS (${riders.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )

        if (riders.isEmpty()) {
            EmptyRidersCard(meshState = meshState)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(riders, key = { it.id }) { rider ->
                    RiderTelemetryCard(
                        rider = rider,
                        isMuted = mutedPeerIds.contains(rider.id),
                        onToggleMute = { onToggleMutePeer(rider.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(70.dp)) // Floating dock padding
                }
            }
        }
    }
}

@Composable
private fun FormationHeader(
    riders: List<Rider>,
    meshState: MeshState
) {
    val nearest = riders.filter { !it.isLocalUser && it.estimatedDistanceMeters != null }
        .minByOrNull { it.estimatedDistanceMeters ?: Float.MAX_VALUE }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "GROUP RADAR",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
            Text(
                text = if (nearest != null) {
                    "Nearest: ${nearest.callsign} (${nearest.formattedDistance} ${nearest.relativePosition ?: ""})"
                } else {
                    "Live 360° P2P Compass Formation"
                },
                fontSize = 11.sp,
                color = AccentCyan
            )
        }

        Surface(
            color = when (meshState) {
                MeshState.CONNECTED -> AccentGreen.copy(alpha = 0.15f)
                MeshState.SEARCHING -> AccentAmber.copy(alpha = 0.15f)
                MeshState.DISCONNECTED -> DarkSurface
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "${riders.size} IN MESH",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

@Composable
internal fun RadarCanvasWidget(
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
            .size(230.dp)
            .clip(CircleShape)
            .background(DarkBackground)
            .border(2.dp, AccentCyan.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width / 2

            // Concentric Range Rings: 15m, 30m, 60m, 100m+
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

            // Scanning Beam Sweep
            if (isScanning) {
                val rad = Math.toRadians(sweepAngle.toDouble())
                val sweepEnd = Offset(
                    (center.x + maxRadius * cos(rad)).toFloat(),
                    (center.y + maxRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = AccentCyan.copy(alpha = 0.5f),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Center Point: Local User (You)
            drawCircle(
                color = AccentCyan,
                radius = 6.dp.toPx(),
                center = center
            )

            // Plot Discovered Peer Blips
            riders.forEachIndexed { index, rider ->
                val dist = rider.estimatedDistanceMeters ?: 30f
                val clampedDistFraction = (dist / 100f).coerceIn(0.15f, 0.92f)
                val peerRadius = maxRadius * clampedDistFraction

                val angleDeg = rider.relativeBearingDegrees ?: (index * (360f / riders.size.coerceAtLeast(1)))
                val canvasAngleRad = Math.toRadians((angleDeg - 90f).toDouble())

                val peerPos = Offset(
                    (center.x + peerRadius * cos(canvasAngleRad)).toFloat(),
                    (center.y + peerRadius * sin(canvasAngleRad)).toFloat()
                )

                // Blip Outer Glow
                if (rider.isTransmitting) {
                    drawCircle(
                        color = AccentGreen.copy(alpha = 0.4f),
                        radius = 11.dp.toPx(),
                        center = peerPos
                    )
                }

                // Inner Solid Blip
                drawCircle(
                    color = if (rider.isTransmitting) AccentGreen else AccentAmber,
                    radius = 5.dp.toPx(),
                    center = peerPos
                )
            }
        }

        // Distance Ring Labels
        Text(
            text = "15m",
            fontSize = 8.sp,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 62.dp)
        )
        Text(
            text = "30m",
            fontSize = 8.sp,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 34.dp)
        )
        Text(
            text = "60m+",
            fontSize = 8.sp,
            color = TextMuted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
        )
    }
}

@Composable
private fun RiderTelemetryCard(
    rider: Rider,
    isMuted: Boolean,
    onToggleMute: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Callsign, Distance/Arrow & Mute button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isMuted -> TextMuted
                                    rider.isTransmitting -> AccentGreen
                                    else -> AccentCyan
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (rider.isLocalUser) "${rider.callsign} (You)" else rider.callsign,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    if (isMuted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MUTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Direction Arrow & Distance
                    Text(
                        text = "${rider.bearingArrow} ${rider.formattedDistance} • ${rider.relativePosition ?: "Direct"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AccentCyan
                    )

                    if (!rider.isLocalUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleMute()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) AccentRed else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics Chips Row: Speed, Altitude, Battery, Signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricChip(label = "🏎️ ${rider.formattedSpeed}")
                MetricChip(label = "⛰️ ${rider.formattedAltitude}")
                MetricChip(
                    label = "🔋 ${rider.formattedBattery}",
                    highlightColor = if ((rider.batteryPercent ?: 100) <= 20) AccentRed else null
                )
                MetricChip(label = "📶 ${rider.signalStrengthDbm} dBm")
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    highlightColor: Color? = null
) {
    Surface(
        color = DarkBackground,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, highlightColor?.copy(alpha = 0.5f) ?: Color.Transparent)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = highlightColor ?: TextSecondary
        )
    }
}

@Composable
private fun EmptyRidersCard(meshState: MeshState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (meshState) {
                    MeshState.SEARCHING -> "Scanning for nearby riders..."
                    MeshState.CONNECTED -> "Waiting for riders to appear in range..."
                    MeshState.DISCONNECTED -> "Mesh is standby. Switch to Intercom to start."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
