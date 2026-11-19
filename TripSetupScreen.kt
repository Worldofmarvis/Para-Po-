package com.parapo.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Mockup tokens (Part 6.4)
private val PrimaryBlue = Color(0xFF0A8CFF)
private val DeepBlue = Color(0xFF0B5FB5)
private val SurfaceLight = Color(0xFFEAF4FF)
private val AlertAmber = Color(0xFFFF7A1A)
private val Muted = Color(0xFF6B7A8C)

/** What S5 needs once the trip is started. */
data class TripSetupResult(
    val destLat: Double,
    val destLon: Double,
    val vehicle: String,          // "JEEPNEY" | "BUS" | "TRAIN"
    val alertDistanceM: Int,
    val routeKm: Double           // placeholder until RouteRepository (ORS) is built
)

// Placeholder origin (BatStateU Alangilan). Replace with LocationSource's last fix in Week 2.
private val DEFAULT_ORIGIN = GeoPoint(13.7847, 121.0735)
private const val ROAD_FACTOR = 1.25

private fun haversineM(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

@Composable
fun TripSetupScreen(
    onBack: () -> Unit = {},
    onStartTrip: (TripSetupResult) -> Unit = {}
) {
    val ctx = LocalContext.current
    remember {
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(File(ctx.cacheDir, "osmdroid"), "tiles")
        }
        true
    }

    var dest by remember { mutableStateOf<GeoPoint?>(null) }
    var vehicle by remember { mutableStateOf("JEEPNEY") }
    var alertKm by remember { mutableFloatStateOf(3.0f) }

    val routeKm = dest?.let { haversineM(DEFAULT_ORIGIN, it) * ROAD_FACTOR / 1000.0 }
    val onTap by rememberUpdatedState<(GeoPoint) -> Unit> { dest = it }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
            }
            Text(
                "Trip Setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeepBlue,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        // Map
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val mapDest = dest
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    MapView(c).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(13.0)
                        controller.setCenter(DEFAULT_ORIGIN)
                    }
                },
                update = { map ->
                    map.overlays.clear()
                    map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onTap(p); return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    }))
                    if (mapDest != null) {
                        map.overlays.add(Polyline().apply {
                            setPoints(listOf(DEFAULT_ORIGIN, mapDest))
                            outlinePaint.color = PrimaryBlue.toArgb()
                            outlinePaint.strokeWidth = 10f
                        })
                        map.overlays.add(Marker(map).apply {
                            position = mapDest
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Your stop"
                            infoWindow = null
                        })
                    }
                    map.overlays.add(Marker(map).apply {
                        position = DEFAULT_ORIGIN
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "You"
                        infoWindow = null
                    })
                    map.invalidate()
                },
                onRelease = { it.onDetach() }
            )

            // Hint / distance chip
            Surface(
                shape = RoundedCornerShape(50),
                color = PrimaryBlue,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).shadow(4.dp, RoundedCornerShape(50))
            ) {
                Text(
                    text = if (routeKm == null) "Tap the map to drop your stop"
                    else "≈ ${"%.1f".format(routeKm)} km",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom panel
        Column(
            Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Vehicle type (speed profile):", fontSize = 14.sp, color = DeepBlue, fontWeight = FontWeight.Medium)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("JEEPNEY" to "Jeepney", "BUS" to "Bus", "TRAIN" to "Train").forEach { (key, label) ->
                    val selected = vehicle == key
                    Surface(
                        onClick = { vehicle = key },
                        shape = RoundedCornerShape(50),
                        color = if (selected) PrimaryBlue else SurfaceLight,
                        border = if (selected) null else BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else DeepBlue
                            )
                        }
                    }
                }
            }
            if (vehicle == "TRAIN") {
                Text(
                    "No rail routing: Train uses straight-line distance unless this route was learned on an earlier ride.",
                    fontSize = 12.sp, color = AlertAmber, modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Alert distance:", fontSize = 14.sp, color = DeepBlue, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                Text("${"%.1f".format(alertKm)} km", fontSize = 16.sp, color = DeepBlue, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = alertKm,
                onValueChange = { alertKm = it },
                valueRange = 1f..5f,
                steps = 7, // 1.0, 1.5 ... 5.0 km
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = SurfaceLight
                )
            )

            Button(
                onClick = {
                    val d = dest ?: return@Button
                    onStartTrip(
                        TripSetupResult(d.latitude, d.longitude, vehicle, (alertKm * 1000).toInt(), routeKm ?: 0.0)
                    )
                },
                enabled = dest != null,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue, contentColor = Color.White,
                    disabledContainerColor = SurfaceLight, disabledContentColor = Muted
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 0.dp)
            ) {
                Text("Start Trip", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
