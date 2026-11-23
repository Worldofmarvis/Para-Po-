package com.parapo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.parapo.BuildConfig
import com.parapo.location.FusedLocationSource
import com.parapo.route.PlaceResult
import com.parapo.route.RouteRepository
import com.parapo.route.RouteResult
import com.parapo.storage.SettingsStore
import com.parapo.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Mockup tokens (Part 6.4)
private val PrimaryBlue = Color(0xFF0A8CFF)
private val DeepBlue = Color(0xFF0B5FB5)
private val SurfaceLight = Color(0xFFEAF4FF)
private val AlertAmber = Color(0xFFFF7A1A)
private val DangerRed = Color(0xFFD93A3A)
private val Muted = Color(0xFF6B7A8C)

/** What S5 needs once the trip is started. */
data class TripSetupResult(
    val destLat: Double,
    val destLon: Double,
    val vehicle: String,          // "JEEPNEY" | "BUS" | "TRAIN"
    val alertDistanceM: Int,
    val routeKm: Double,          // road km from ORS, or straight-line x 1.25 if no route
    val originLat: Double = 13.7847,   // where the trip starts (S5 reloads the cached route with this)
    val originLon: Double = 121.0735,
    val destName: String? = null
)

// Placeholder origin (BatStateU Alangilan). Replace with LocationSource's last fix in Week 2.
private val DEFAULT_ORIGIN = GeoPoint(13.7847, 121.0735)
private const val ROAD_FACTOR = 1.25
private val SHEET_CORNER = 24.dp

internal val MAPTILER = object : OnlineTileSourceBase(
    "MapTilerStreets", 0, 19, 256, ".png",
    arrayOf("https://api.maptiler.com/maps/streets-v2/256/"),
    "\u00A9 MapTiler \u00A9 OpenStreetMap contributors"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + ".png" +
            "?key=" + BuildConfig.MAPTILER_KEY
}

internal fun activeTileSource(): ITileSource {
    val k = BuildConfig.MAPTILER_KEY.trim()
    val hasRealKey = k.isNotBlank() && k != "dummy_key" && !k.startsWith("your_")
    return if (hasRealKey) MAPTILER else TileSourceFactory.MAPNIK
}

private fun haversineM(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/** Cumulative meters along the polyline (same idea as Polyline.cumulativeM in the guide). */
private fun cumulative(points: List<GeoPoint>): DoubleArray {
    val cum = DoubleArray(points.size)
    for (i in 1 until points.size) cum[i] = cum[i - 1] + haversineM(points[i - 1], points[i])
    return cum
}

/** The first [frac] (0..1) of the line by distance, so the blue line "draws itself" along the road. */
private fun partialPoints(points: List<GeoPoint>, cum: DoubleArray, frac: Float): List<GeoPoint> {
    if (points.size < 2 || frac <= 0f) return emptyList()
    if (frac >= 1f) return points
    val target = cum.last() * frac
    var i = 1
    while (i < cum.size - 1 && cum[i] < target) i++
    val seg = cum[i] - cum[i - 1]
    val t = if (seg <= 0.0) 0.0 else (target - cum[i - 1]) / seg
    val a = points[i - 1]
    val b = points[i]
    val tip = GeoPoint(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t
    )
    return points.subList(0, i) + tip
}

internal fun pinDrawable(ctx: Context, color: Int): Drawable {
    val d = ctx.resources.displayMetrics.density
    val w = (34 * d).toInt()
    val h = (46 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = w / 2f - 2 * d
    val cx = w / 2f
    val cy = r + 2 * d
    p.color = color
    c.drawCircle(cx, cy, r, p)
    val path = Path().apply {
        moveTo(cx - r * 0.8f, cy + r * 0.6f)
        lineTo(cx, h - 1 * d)
        lineTo(cx + r * 0.8f, cy + r * 0.6f)
        close()
    }
    c.drawPath(path, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, r * 0.4f, p)
    return BitmapDrawable(ctx.resources, bmp)
}

internal fun userDotDrawable(ctx: Context, color: Int): Drawable {
    val d = ctx.resources.displayMetrics.density
    val s = (44 * d).toInt()
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val m = s / 2f
    p.color = (color and 0x00FFFFFF) or 0x40000000
    c.drawCircle(m, m, 20 * d, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(m, m, 11 * d, p)
    p.color = color
    c.drawCircle(m, m, 8 * d, p)
    return BitmapDrawable(ctx.resources, bmp)
}

@Composable
fun TripSetupScreen(
    onBack: () -> Unit = {},
    onStartTrip: (TripSetupResult) -> Unit = {}
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    remember {
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(File(ctx.cacheDir, "osmdroid"), "tiles")
        }
        true
    }

    val repo = remember { RouteRepository(ctx.applicationContext) }
    val anim = remember { Animatable(0f) }
    val savedSettings = remember { SettingsStore.current(ctx.applicationContext) }

    var origin by remember { mutableStateOf(FusedLocationSource.lastFetched ?: DEFAULT_ORIGIN) }
    var dest by remember { mutableStateOf<GeoPoint?>(null) }
    var pendingPin by remember { mutableStateOf<GeoPoint?>(null) }
    var pendingPinName by remember { mutableStateOf<String?>(null) }
    var pendingPinLookingUp by remember { mutableStateOf(false) }
    var gpsStatusMsg by remember { mutableStateOf<String?>(null) }
    var vehicle by remember { mutableStateOf("JEEPNEY") }
    var alertKm by remember {
        mutableFloatStateOf((savedSettings.alertDistanceM / 1000f).coerceIn(1f, 5f))
    }
    var sheetHeight by remember { mutableStateOf(0.dp) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    // Fetch cached fix right away, then request a fresh live lock (supports both GPS phones and Wi-Fi tablets)
    LaunchedEffect(Unit) {
        if (PermissionHelper.hasFineLocation(ctx)) {
            val locSource = FusedLocationSource(ctx.applicationContext)
            locSource.lastKnownPoint()?.let { cached ->
                origin = cached
            }
            locSource.currentPoint()?.let { fresh ->
                origin = fresh
            }
        }
    }

    // Always keep the map centered on the live origin once MapView is ready (fixes mapRef-null race)
    LaunchedEffect(origin, mapRef) {
        if (dest == null) {
            mapRef?.controller?.animateTo(origin)
        }
    }

    // search
    var query by remember { mutableStateOf("") }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchMsg by remember { mutableStateOf<String?>(null) }

    // route
    var route by remember { mutableStateOf<RouteResult?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var routeFailed by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }

    // Debounced place search
    LaunchedEffect(query) {
        val q = query.trim()
        if (q == pickedName || q.length < 3) {
            results = emptyList(); searchMsg = null
            return@LaunchedEffect
        }
        delay(450)
        searching = true
        searchMsg = null
        try {
            repo.search(q, origin.latitude, origin.longitude)
                .onSuccess { results = it; if (it.isEmpty()) searchMsg = "No places found" }
                .onFailure { results = emptyList(); searchMsg = "Search failed. Check your connection." }
        } finally {
            searching = false
        }
    }

    // Fetch the road route whenever the stop, origin, or vehicle mode changes, then draw it.
    // Part 1 & Part 8 #6: ORS has no rail profile, so Train trips use the straight-line fallback.
    LaunchedEffect(dest, origin, vehicle) {
        val d = dest
        route = null
        routeFailed = false
        routeError = null
        anim.snapTo(0f)
        if (d == null) return@LaunchedEffect
        if (vehicle == "TRAIN") {
            routeFailed = true
            routeError = "ORS has no rail profile"
            return@LaunchedEffect
        }
        routeLoading = true
        repo.fetchRoute(origin, d)
            .onSuccess { route = it }
            .onFailure { routeFailed = true; routeError = it.message ?: it.javaClass.simpleName }
        routeLoading = false
        if (route != null) anim.animateTo(1f, tween(1800, easing = LinearOutSlowInEasing))
    }

    val mapRoute = route
    val cum = remember(mapRoute) { mapRoute?.let { cumulative(it.points) } }
    val displayKm: Double? = mapRoute?.let { it.distanceM / 1000.0 }
        ?: dest?.let { haversineM(origin, it) * savedSettings.roadFactor / 1000.0 }

    val onTap by rememberUpdatedState<(GeoPoint) -> Unit> { tapped ->
        focus.clearFocus()
        results = emptyList()
        searchMsg = null
        pendingPin = tapped
        pendingPinName = null
        pendingPinLookingUp = true
        scope.launch {
            val resolved = repo.reverseGeocode(tapped)
            if (pendingPin == tapped) {
                pendingPinName = resolved
                pendingPinLookingUp = false
            }
        }
    }

    val redPin = remember { pinDrawable(ctx, DangerRed.toArgb()) }
    val amberPin = remember { pinDrawable(ctx, AlertAmber.toArgb()) }
    val userDot = remember { userDotDrawable(ctx, PrimaryBlue.toArgb()) }

    Box(Modifier.fillMaxSize().background(Color(0xFFE8EEF4))) {

        // ---- Map ----
        val mapBottomPad: Dp = max(0f, (sheetHeight - SHEET_CORNER).value).dp
        val mapDest = dest
        val mapPending = pendingPin
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(bottom = mapBottomPad),
            factory = { c ->
                MapView(c).apply {
                    setTileSource(activeTileSource())
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    minZoomLevel = 4.0
                    controller.setZoom(14.0)
                    controller.setCenter(origin)
                    mapRef = this
                }
            },
            update = { map ->
                val frac = anim.value // read here so only this block re-runs per animation frame
                map.overlays.clear()
                map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        onTap(p); return true
                    }
                    override fun longPressHelper(p: GeoPoint): Boolean = false
                }))

                val line: List<GeoPoint> = when {
                    mapRoute != null && cum != null -> partialPoints(mapRoute.points, cum, frac)
                    routeFailed && mapDest != null -> listOf(origin, mapDest)
                    else -> emptyList()
                }
                if (line.size >= 2) {
                    map.overlays.add(Polyline().apply {
                        setPoints(line)
                        outlinePaint.color = android.graphics.Color.WHITE
                        outlinePaint.strokeWidth = 18f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    })
                    map.overlays.add(Polyline().apply {
                        setPoints(line)
                        outlinePaint.color = PrimaryBlue.toArgb()
                        outlinePaint.strokeWidth = 11f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    })
                }
                if (mapDest != null) {
                    map.overlays.add(Marker(map).apply {
                        position = mapDest
                        icon = redPin
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        infoWindow = null
                    })
                }
                if (mapPending != null) {
                    map.overlays.add(Marker(map).apply {
                        position = mapPending
                        icon = amberPin
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        infoWindow = null
                    })
                }
                map.overlays.add(Marker(map).apply {
                    position = origin
                    icon = userDot
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    infoWindow = null
                })
                map.overlays.add(CopyrightOverlay(map.context).apply {
                    setAlignBottom(false)
                    setAlignRight(true)
                })

                // Fit the camera once per stop, and again when the road route arrives
                val fitKey: Any? = mapRoute ?: mapDest
                if (map.tag != fitKey) {
                    map.tag = fitKey
                    if (mapDest != null) {
                        val pts = mapRoute?.points ?: listOf(origin, mapDest)
                        map.post {
                            val box = BoundingBox.fromGeoPoints(pts)
                            map.zoomToBoundingBox(box, true, (110 * map.resources.displayMetrics.density).toInt())
                        }
                    }
                }
                map.invalidate()
            },
            onRelease = { it.onDetach() }
        )

        // ---- Distance / status chip (under the search bar) ----
        val chipText = when {
            gpsStatusMsg != null -> gpsStatusMsg!!
            dest == null -> "Search or tap the map to set your stop"
            routeLoading -> "Finding the road route\u2026"
            routeFailed -> "No road route: ${routeError ?: "unknown"}. Estimate ~${"%.1f".format(displayKm ?: 0.0)} km (x 1.25)"
            else -> "${"%.1f".format(displayKm ?: 0.0)} km by road"
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (routeFailed || gpsStatusMsg != null) AlertAmber else PrimaryBlue,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = chipText,
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ---- My location button ----
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = sheetHeight + 14.dp).size(48.dp)
        ) {
            IconButton(onClick = {
                mapRef?.controller?.animateTo(origin)
                if (!PermissionHelper.hasFineLocation(ctx)) {
                    gpsStatusMsg = "Allow Precise Location in Permissions first"
                } else {
                    val locSource = FusedLocationSource(ctx.applicationContext)
                    if (!locSource.isLocationEnabled()) {
                        gpsStatusMsg = "Location is off — turn on Location in settings"
                    } else {
                        gpsStatusMsg = "Reloading GPS\u2026"
                        scope.launch {
                            val live = locSource.currentPoint()
                            if (live != null) {
                                origin = live
                                mapRef?.controller?.animateTo(live)
                                gpsStatusMsg = null
                            } else {
                                gpsStatusMsg = "No GPS fix yet — enable Wi-Fi/Location and retry"
                            }
                        }
                    }
                }
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = "My location", tint = PrimaryBlue)
            }
        }

        // ---- Bottom sheet ----
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
            shadowElevation = 16.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { sheetHeight = with(density) { it.height.toDp() } }
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 16.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFFD5DCE4), RoundedCornerShape(50))
                )

                Text(
                    "Vehicle type (speed profile):", fontSize = 14.sp, color = DeepBlue,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp)
                )
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

                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Alert distance:", fontSize = 14.sp, color = DeepBlue,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                    )
                    Text("${"%.1f".format(alertKm)} km", fontSize = 16.sp, color = DeepBlue, fontWeight = FontWeight.Bold)
                }
                AlertDistanceSlider(
                    value = alertKm,
                    onValueChange = { alertKm = it },
                    valueRange = 1f..5f,
                    step = 0.5f
                )

                Button(
                    onClick = {
                        val d = dest ?: return@Button
                        val result = TripSetupResult(
                            d.latitude, d.longitude, vehicle, (alertKm * 1000).toInt(), displayKm ?: 0.0,
                            origin.latitude, origin.longitude, pickedName
                        )
                        scope.launch {
                            starting = true
                            if (route != null) {
                                // replay the snake before going to S5
                                anim.snapTo(0f)
                                anim.animateTo(1f, tween(1400, easing = LinearEasing))
                            }
                            starting = false
                            onStartTrip(result)
                        }
                    },
                    // Part 6.3: disabled until the route is ready (or failed, so the x1.25 fallback applies)
                    enabled = dest != null && !routeLoading && !starting && (route != null || routeFailed),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue, contentColor = Color.White,
                        disabledContainerColor = SurfaceLight, disabledContentColor = Muted
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Start Trip", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- Top bar: back + search (drawn last so the dropdown sits above everything) ----
        Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White, shadowElevation = 6.dp, modifier = Modifier.size(48.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DeepBlue)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.weight(1f).padding(start = 10.dp).heightIn(min = 48.dp)
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it; pickedName = null },
                        placeholder = { Text("Where do you get off?", fontSize = 16.sp, color = Muted) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = PrimaryBlue) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; pickedName = null; results = emptyList(); searchMsg = null }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Muted)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = PrimaryBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (results.isNotEmpty() || searching || searchMsg != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column {
                        if (searching && results.isEmpty()) {
                            Text("Searching\u2026", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                        }
                        searchMsg?.let {
                            Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                        }
                        results.forEach { place ->
                            Text(
                                text = place.name,
                                fontSize = 16.sp,
                                color = DeepBlue,
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        focus.clearFocus()
                                        pickedName = place.name
                                        query = place.name
                                        results = emptyList()
                                        searchMsg = null
                                        dest = place.point
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingPin?.let { pin ->
        val coordsText = String.format(java.util.Locale.US, "%.5f, %.5f", pin.latitude, pin.longitude)
        val placeLabel = when {
            pendingPinLookingUp -> "Looking up place name\u2026"
            !pendingPinName.isNullOrBlank() -> pendingPinName!!
            else -> "Pinned location"
        }
        AlertDialog(
            onDismissRequest = {
                pendingPin = null
                pendingPinName = null
                pendingPinLookingUp = false
            },
            title = { Text("Set this as your stop?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(placeLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DeepBlue)
                    Text("Coordinates: $coordsText", fontSize = 13.sp, color = Muted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosenName = pendingPinName
                    dest = pin
                    gpsStatusMsg = null
                    if (!chosenName.isNullOrBlank()) {
                        pickedName = chosenName
                        query = chosenName
                    } else {
                        pickedName = null
                    }
                    pendingPin = null
                    pendingPinName = null
                    pendingPinLookingUp = false
                }) {
                    Text("Confirm Stop", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingPin = null
                    pendingPinName = null
                    pendingPinLookingUp = false
                }) {
                    Text("Cancel", color = Muted)
                }
            }
        )
    }
}