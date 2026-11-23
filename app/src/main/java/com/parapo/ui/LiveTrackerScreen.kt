package com.parapo.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.parapo.R
import com.parapo.location.SamplingTier
import com.parapo.storage.SettingsStore
import com.parapo.trip.PositionSourceKind
import com.parapo.trip.TripController
import com.parapo.trip.TripPhase
import com.parapo.trip.TripUiState
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.theme.AlertOrange
import com.parapo.ui.theme.DangerRed
import com.parapo.ui.theme.DeepBlue
import com.parapo.ui.theme.PrimaryBlue
import com.parapo.ui.theme.SuccessGreen
import com.parapo.ui.theme.SurfaceLight
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.max

private val SHEET_CORNER = 24.dp
private val MutedText = Color(0xFF6B7A8C)

/** S5 Live Tracker (Part 6.3). Shows the trip held by [TripController]. */
@Composable
fun LiveTrackerScreen(onSetStop: () -> Unit, onCancelled: () -> Unit) {
    val ui by TripController.state.collectAsState()
    when (ui.phase) {
        TripPhase.IDLE, TripPhase.COMPLETED -> NoTrip(onSetStop)
        TripPhase.ARMED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Preparing your route\u2026", fontSize = 16.sp, color = DeepBlue)
        }
        TripPhase.TRACKING, TripPhase.ALERTING -> TrackerBody(ui, onCancelled)
    }
}

@Composable
private fun NoTrip(onSetStop: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Sulo(SuloPose.CALM, size = 140.dp)
        Spacer(Modifier.height(16.dp))
        Text("No trip in progress", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeepBlue)
        Text(
            "Set your stop and Sulo will watch the road for you.",
            fontSize = 16.sp, color = MutedText, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSetStop, shape = RoundedCornerShape(50), modifier = Modifier.height(52.dp)) {
            Text("Set My Stop", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun fmtDistance(m: Double): String =
    if (m < 1000) "${m.toInt()} m" else "${"%.1f".format(m / 1000.0)} km"

@Composable
private fun TrackerBody(ui: TripUiState, onCancelled: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    remember {
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(File(ctx.cacheDir, "osmdroid"), "tiles")
        }
        true
    }

    var sheetHeight by remember { mutableStateOf(0.dp) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }

    val redPin = remember { pinDrawable(ctx, DangerRed.toArgb()) }
    val userDot = remember { userDotDrawable(ctx, PrimaryBlue.toArgb()) }
    val dest = remember(ui.setup) { ui.setup?.let { GeoPoint(it.destLat, it.destLon) } }
    val alertMarkIcon = remember { alertMarkDrawable(ctx) }
    // Same distance the trip uses: this trip's alert distance, else the saved setting.
    val alertDistM = ui.setup?.alertDistanceM?.takeIf { it > 0 }
        ?: SettingsStore.current(ctx.applicationContext).alertDistanceM
    val alertMark = remember(ui.routePoints, ui.usingStraightLine, alertDistM) {
        val scale = if (ui.usingStraightLine) SettingsStore.current(ctx.applicationContext).roadFactor else 1.0
        alertMarkPoint(ui.routePoints, scale, alertDistM.toDouble())
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFE8EEF4))) {

        // ---- Map ----
        val mapBottomPad: Dp = max(0f, (sheetHeight - SHEET_CORNER).value).dp
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(bottom = mapBottomPad),
            factory = { c ->
                MapView(c).apply {
                    setTileSource(activeTileSource())
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 4.0
                    controller.setZoom(14.0)
                    ui.position?.let { controller.setCenter(it) }
                    mapRef = this
                }
            },
            update = { map ->
                map.overlays.clear()
                if (ui.routePoints.size >= 2) {
                    map.overlays.add(Polyline().apply {
                        setPoints(ui.routePoints)
                        outlinePaint.color = android.graphics.Color.WHITE
                        outlinePaint.strokeWidth = 18f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    })
                    map.overlays.add(Polyline().apply {
                        setPoints(ui.routePoints)
                        outlinePaint.color = PrimaryBlue.toArgb()
                        outlinePaint.strokeWidth = 11f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    })
                }
                alertMark?.let { a ->
                    map.overlays.add(Marker(map).apply {
                        position = a
                        icon = alertMarkIcon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        infoWindow = null
                    })
                }
                dest?.let { d ->
                    map.overlays.add(Marker(map).apply {
                        position = d
                        icon = redPin
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        infoWindow = null
                    })
                }
                ui.position?.let { p ->
                    map.overlays.add(Marker(map).apply {
                        position = p
                        icon = userDot
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        infoWindow = null
                    })
                }
                map.overlays.add(CopyrightOverlay(map.context).apply {
                    setAlignBottom(false)
                    setAlignRight(true)
                })
                // Fit the whole route once when the trip starts
                if (map.tag !== ui.routePoints && ui.routePoints.size >= 2) {
                    map.tag = ui.routePoints
                    val pts = ui.routePoints
                    map.post {
                        val box = BoundingBox.fromGeoPoints(pts)
                        map.zoomToBoundingBox(box, true, (110 * map.resources.displayMetrics.density).toInt())
                    }
                }
                map.invalidate()
            },
            onRelease = { it.onDetach() }
        )

        // ---- Status chips ----
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(
                text = if (ui.phase == TripPhase.ALERTING) "Alerting" else "On trip",
                color = if (ui.phase == TripPhase.ALERTING) AlertOrange else PrimaryBlue
            )
            val (srcText, srcColor) = when (ui.source) {
                PositionSourceKind.GPS -> "GPS" to SuccessGreen
                PositionSourceKind.CELL -> "Cell fallback" to PrimaryBlue
                PositionSourceKind.DEAD_RECKONING ->
                    (if (ui.lowConfidence) "Dead reckoning (low)" else "Dead reckoning") to AlertOrange
            }
            Chip(text = srcText, color = srcColor)
            val samplingLabel = when (ui.samplingTier) {
                SamplingTier.NEAR -> "High acc"
                SamplingTier.FAR_SCREEN_ON -> "Eco 5s"
                SamplingTier.FAR_SCREEN_OFF -> "Eco 10s"
            }
            Chip(text = samplingLabel, color = MutedText)
        }

        // ---- My location button ----
        Surface(
            shape = CircleShape, color = Color.White, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = sheetHeight + 14.dp).size(48.dp)
        ) {
            IconButton(onClick = { ui.position?.let { mapRef?.controller?.animateTo(it) } }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "My location", tint = PrimaryBlue)
            }
        }

        // ---- Bottom card ----
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
            shadowElevation = 16.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { sheetHeight = with(density) { it.height.toDp() } }
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 16.dp)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFFD5DCE4), RoundedCornerShape(50))
                )

                Text(
                    "Remaining distance", fontSize = 14.sp, color = DeepBlue,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmtDistance(ui.remainingM), fontSize = 40.sp,
                        fontWeight = FontWeight.Bold, color = DeepBlue, modifier = Modifier.weight(1f)
                    )
                    // ETA is hidden when speed is below 1 m/s
                    ui.etaMin?.let {
                        Text(
                            "Arrive in $it min", fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, color = PrimaryBlue,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                if (ui.usingStraightLine) {
                    Text(
                        "No road route saved: using straight-line distance x 1.25.",
                        fontSize = 12.sp, color = AlertOrange, modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (ui.lowConfidence) {
                    Text(
                        "GPS lost over 3 min: estimate is low confidence, alert margin widened.",
                        fontSize = 12.sp, color = AlertOrange, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                val line = when {
                    ui.source != PositionSourceKind.GPS -> "Walang GPS? Okay lang, naririnig ko ang mga tower."
                    ui.speedMps >= 8.0 -> "Fast-moving! Almost there!"
                    else -> "Pikit ka lang, ako na ang magbabantay."
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Image(
                        painter = painterResource(R.drawable.sulo_s5),
                        contentDescription = "Sulo the Lanternfox",
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = SurfaceLight,
                        modifier = Modifier.padding(start = 8.dp, bottom = 14.dp).weight(1f)
                    ) {
                        Text(
                            line, fontSize = 16.sp, color = DeepBlue,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { confirmCancel = true },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Cancel Trip", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this trip?") },
            text = { Text("Tracking stops and this trip will not be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    TripController.reset()
                    onCancelled()
                }) { Text("Cancel trip", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep tracking") }
            }
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color, shadowElevation = 6.dp) {
        Text(
            text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
