package com.landxcape.synkromesh.presentation.intercom.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.DarkSurfaceElevated
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary

enum class IntercomPage(val index: Int, val title: String, val icon: ImageVector) {
    INTERCOM(0, "Intercom", Icons.Default.Mic),
    RADAR(1, "Radar", Icons.Default.Sensors),
    DEVICE(2, "Device", Icons.Default.Tune)
}

@Composable
fun FloatingBottomDock(
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .height(58.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(29.dp)),
        shape = RoundedCornerShape(29.dp),
        color = DarkSurfaceElevated.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IntercomPage.values().forEach { page ->
                val isSelected = page.index == currentPage
                val interactionSource = remember { MutableInteractionSource() }

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan else TextMuted,
                    animationSpec = tween(durationMillis = 200),
                    label = "dock_content_color"
                )

                val pillBgColor by animateColorAsState(
                    targetValue = if (isSelected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "dock_pill_bg"
                )

                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(pillBgColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectPage(page.index)
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = page.title,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = page.title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
