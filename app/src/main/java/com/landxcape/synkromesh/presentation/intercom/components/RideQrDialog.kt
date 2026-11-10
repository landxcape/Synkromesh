package com.landxcape.synkromesh.presentation.intercom.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.AccentGreen
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.DarkSurfaceElevated
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary
import com.landxcape.synkromesh.presentation.theme.TextSecondary
import com.landxcape.synkromesh.util.RideQrData
import com.landxcape.synkromesh.util.RideQrManager

@Composable
fun RideQrDialog(
    channel: Int,
    callsign: String,
    hotspotSsid: String?,
    hotspotPassword: String?,
    packToken: String? = null,
    onDismiss: () -> Unit
) {
    val qrBitmap: Bitmap? = remember(channel, callsign, hotspotSsid, hotspotPassword, packToken) {
        val data = RideQrData(
            channel = channel,
            callsign = callsign,
            ssid = hotspotSsid,
            password = hotspotPassword,
            packToken = packToken
        )
        RideQrManager.generateQrBitmap(data, sizePx = 600)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "PAIRING QR CODE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Have other riders tap 'Scan QR' to instantly switch to Channel $channel and link into the mesh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                // High-Contrast White Inset for Clean Camera Scanning
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Ride Pairing QR",
                            modifier = Modifier.size(200.dp)
                        )
                    } else {
                        Text(
                            text = "Error generating QR",
                            color = Color.Black,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Pill
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CHANNEL $channel • HOST: $callsign",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AccentCyan
                        )
                        if (hotspotSsid != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = "Wi-Fi: $hotspotSsid",
                                    fontSize = 11.sp,
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "DONE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
