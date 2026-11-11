package com.landxcape.synkromesh.presentation.intercom.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.landxcape.synkromesh.domain.model.Rider
import com.landxcape.synkromesh.presentation.theme.AccentAmber
import com.landxcape.synkromesh.presentation.theme.AccentCyan
import com.landxcape.synkromesh.presentation.theme.AccentGreen
import com.landxcape.synkromesh.presentation.theme.BorderSubtle
import com.landxcape.synkromesh.presentation.theme.DarkBackground
import com.landxcape.synkromesh.presentation.theme.DarkSurface
import com.landxcape.synkromesh.presentation.theme.TextMuted
import com.landxcape.synkromesh.presentation.theme.TextPrimary
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun TacticalOsmMapView(
    riders: List<Rider>,
    modifier: Modifier = Modifier,
    initialZoom: Double = 16.5,
    showWatermark: Boolean = true,
    showControls: Boolean = true,
    controlsAlignment: Alignment = Alignment.BottomEnd,
    controlsModifier: Modifier = Modifier.padding(16.dp)
) {
    val context = LocalContext.current
    var autoFollow by remember { mutableStateOf(true) }

    val localRider = riders.firstOrNull { it.isLocalUser }
    val firstGpsRider = localRider?.takeIf { it.latitude != null && it.longitude != null }
        ?: riders.firstOrNull { it.latitude != null && it.longitude != null }
    val defaultCenter = GeoPoint(27.7172, 85.3240) // Kathmandu default fallback
    val currentCenter = remember(firstGpsRider?.latitude, firstGpsRider?.longitude) {
        if (firstGpsRider?.latitude != null && firstGpsRider.longitude != null) {
            GeoPoint(firstGpsRider.latitude, firstGpsRider.longitude)
        } else {
            defaultCenter
        }
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                    // Tactical Obsidian Dark Color Filter
                    val darkMatrix = ColorMatrix(floatArrayOf(
                        -0.85f, 0f, 0f, 0f, 220f,
                        0f, -0.85f, 0f, 0f, 220f,
                        0f, 0f, -0.85f, 0f, 235f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))

                    controller.setZoom(initialZoom)
                    controller.setCenter(currentCenter)
                    mapViewRef = this
                }
            },
            update = { mapView ->
                mapViewRef = mapView

                // Clear previous transient markers & polylines
                mapView.overlays.removeAll { it is Marker || it is Polyline }

                val myPos = if (localRider?.latitude != null && localRider.longitude != null) {
                    GeoPoint(localRider.latitude, localRider.longitude)
                } else currentCenter

                if (autoFollow) {
                    mapView.controller.animateTo(myPos)
                }

                // 1. Local Rider Marker (Pulsing Cyan Reticle)
                val localMarker = Marker(mapView).apply {
                    position = myPos
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createRiderPin(context, isLocal = true, label = localRider?.callsign ?: "You")
                    rotation = localRider?.relativeBearingDegrees ?: 0f
                    title = "${localRider?.callsign ?: "Rider"} (You)"
                }
                mapView.overlays.add(localMarker)

                // 2. Remote Peer Markers & Vector Link Lines
                riders.filter { !it.isLocalUser && it.latitude != null && it.longitude != null }.forEach { peer ->
                    val peerPos = GeoPoint(peer.latitude!!, peer.longitude!!)

                    // Convoy Topology Vector Polyline
                    val line = Polyline(mapView).apply {
                        setPoints(listOf(myPos, peerPos))
                        outlinePaint.strokeWidth = if (peer.hopCount > 1) 4f else 5f
                        outlinePaint.color = if (peer.hopCount > 1) {
                            android.graphics.Color.argb(160, 255, 179, 0) // Amber for relay
                        } else {
                            android.graphics.Color.argb(190, 0, 242, 255) // Cyan for 1-hop
                        }
                    }
                    mapView.overlays.add(line)

                    // Peer Marker Pin
                    val peerMarker = Marker(mapView).apply {
                        position = peerPos
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createRiderPin(
                            context = context,
                            isLocal = false,
                            label = peer.callsign,
                            isRelayed = peer.hopCount > 1,
                            isTransmitting = peer.isTransmitting
                        )
                        title = peer.callsign
                        snippet = "${peer.formattedDistance} • ${peer.formattedSpeed} • ${if (peer.hopCount > 1) "${peer.hopCount} HOPS" else "DIRECT"}"
                    }
                    mapView.overlays.add(peerMarker)
                }

                mapView.invalidate()
            }
        )

        // Overlay Map Controls (Auto-Follow & Zoom)
        if (showControls) {
            Column(
                modifier = controlsModifier
                    .align(controlsAlignment),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto Follow Toggle Button
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            autoFollow = !autoFollow
                            if (autoFollow) {
                                mapViewRef?.controller?.animateTo(currentCenter)
                            }
                        },
                    color = if (autoFollow) AccentCyan else DarkSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (autoFollow) AccentCyan else BorderSubtle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Center on me",
                            tint = if (autoFollow) DarkBackground else AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Zoom In
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { mapViewRef?.controller?.zoomIn() },
                    color = DarkSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom in",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Zoom Out
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { mapViewRef?.controller?.zoomOut() },
                    color = DarkSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom out",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Tactical Watermark & Tile Status
        if (showWatermark) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = DarkBackground.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = "OSM TACTICAL // OFFLINE CACHE ACTIVE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Creates high-performance tactical motorcycle rider map pins.
 */
private fun createRiderPin(
    context: android.content.Context,
    isLocal: Boolean,
    label: String,
    isRelayed: Boolean = false,
    isTransmitting: Boolean = false
): BitmapDrawable {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer glow
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    paint.color = when {
        isTransmitting -> android.graphics.Color.argb(220, 0, 230, 118) // Green
        isLocal -> android.graphics.Color.argb(220, 0, 242, 255) // Cyan
        isRelayed -> android.graphics.Color.argb(220, 255, 179, 0) // Amber
        else -> android.graphics.Color.argb(220, 0, 242, 255)
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

    // Inner filled core
    paint.style = Paint.Style.FILL
    paint.color = when {
        isTransmitting -> android.graphics.Color.rgb(0, 230, 118)
        isLocal -> android.graphics.Color.rgb(0, 242, 255)
        isRelayed -> android.graphics.Color.rgb(255, 179, 0)
        else -> android.graphics.Color.rgb(0, 242, 255)
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)

    return BitmapDrawable(context.resources, bitmap)
}
