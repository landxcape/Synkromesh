package com.landxcape.synkromesh.presentation.intercom.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.presentation.theme.AccentAmber
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.AccentGreen
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.TextMuted
import kotlin.math.cos
import kotlin.math.sin

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

                // Tactical Mesh Topology Link Vector:
                // Solid Cyan line for direct 1-hop link, Dashed Amber line for 2+ hop relayed link
                val pathEffect = if (rider.hopCount > 1) {
                    PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                } else null

                drawLine(
                    color = if (rider.hopCount > 1) AccentAmber.copy(alpha = 0.5f) else AccentCyan.copy(alpha = 0.35f),
                    start = center,
                    end = peerPos,
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = pathEffect
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
