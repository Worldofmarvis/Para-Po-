package com.parapo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.parapo.location.FusedLocationSource
import com.parapo.route.RouteRepository
import com.parapo.storage.TripLog
import com.parapo.trip.TripPhase
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.AlertOrange
import com.parapo.storage.ProfileStore
import com.parapo.ui.components.ProfileAvatar
import androidx.compose.runtime.collectAsState
import com.parapo.ui.theme.AvatarBg
import com.parapo.ui.theme.AvatarIcon
import com.parapo.ui.theme.DangerRed
import com.parapo.ui.theme.InkNavy
import com.parapo.ui.theme.MetaNavy
import com.parapo.ui.theme.ScreenBg
import com.parapo.ui.theme.SuccessGreen
import com.parapo.util.PermissionHelper
import com.parapo.util.VehicleMode
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S3 Home, laid out like the mockup (Figure 2) with the 6.6 corrections.
 * Reads username from AuthGate and recent trips from TripLogStore.
 */
@Composable
fun HomeScreen(
    userName: String = "Juan",
    recentTrips: List<TripLog> = emptyList(),
    activeTripPhase: TripPhase = TripPhase.IDLE,
    onOpenLiveTrip: () -> Unit = {},
    onSetStop: () -> Unit,
    onTestAlert: () -> Unit,
    onOpenTrips: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cachedRoutesCount = File(ctx.filesDir, "routes").listFiles()?.count { it.extension == "json" } ?: 0

    var gpsLoading by remember { mutableStateOf(false) }
    var gpsLabel by remember {
        mutableStateOf(
            FusedLocationSource.lastFetched?.let {
                String.format(Locale.US, "Your location (%.4f, %.4f)", it.latitude, it.longitude)
            } ?: "Your location (GPS)"
        )
    }

    fun reloadLocation() {
        if (gpsLoading) return
        if (!PermissionHelper.hasFineLocation(ctx)) {
            gpsLabel = "Location permission needed"
            return
        }
        val locSource = FusedLocationSource(ctx.applicationContext)
        if (!locSource.isLocationEnabled()) {
            gpsLabel = "Location is off — turn on & tap to retry"
            return
        }
        gpsLoading = true
        gpsLabel = "Reloading GPS…"
        scope.launch {
            val pt = locSource.currentPoint()
            if (pt != null) {
                val coords = String.format(Locale.US, "%.4f, %.4f", pt.latitude, pt.longitude)
                gpsLabel = "Your location ($coords)"
                val place = RouteRepository(ctx.applicationContext).reverseGeocode(pt)
                if (!place.isNullOrBlank()) {
                    gpsLabel = place.substringBefore(", Philippines").ifBlank { place }
                }
            } else {
                gpsLabel = "No GPS fix yet — tap to retry"
            }
            gpsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (FusedLocationSource.lastFetched == null && PermissionHelper.hasFineLocation(ctx)) {
            reloadLocation()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Header (white): avatar, greeting, bell = "Test alert" shortcut
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val profilePhoto by ProfileStore.photo.collectAsState()
            ProfileAvatar(
                photo = profilePhoto,
                size = 56.dp,
                modifier = Modifier.clickable(onClick = onOpenProfile)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hi, $userName!",
                    color = InkNavy,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ride smart, buddy!",
                    color = MetaNavy,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            IconButton(onClick = onTestAlert) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Test alert",
                    tint = ActionBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            val headline = buildAnnotatedString {
                append("Set. ")
                withStyle(SpanStyle(color = ActionBlue)) { append("Rest.") }
                append(" Arrive.")
            }
            Text(
                text = headline,
                color = InkNavy,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Get an alert before your stop",
                    color = InkNavy,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (cachedRoutesCount > 0) SuccessGreen.copy(alpha = 0.14f) else AvatarBg
                ) {
                    Text(
                        text = if (cachedRoutesCount > 0) "$cachedRoutesCount cached" else "No cache",
                        color = if (cachedRoutesCount > 0) SuccessGreen else MetaNavy,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            PillField(
                icon = Icons.Filled.Place,
                iconTint = ActionBlue,
                text = gpsLabel,
                textColor = ActionBlue,
                trailingLoading = gpsLoading,
                showRefreshIcon = true,
                onClick = { reloadLocation() }
            )
            Spacer(Modifier.height(10.dp))
            PillField(
                icon = Icons.Filled.Place,
                iconTint = DangerRed,
                text = "Where do you get off?",
                textColor = InkNavy,
                onClick = onSetStop
            )

            Spacer(Modifier.height(14.dp))
            if (activeTripPhase == TripPhase.ARMED || activeTripPhase == TripPhase.TRACKING) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SuccessGreen.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable(onClick = onOpenLiveTrip)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Trip in progress", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
                            Text("Tap to open Live Tracker", fontSize = 13.sp, color = MetaNavy)
                        }
                        Text("Open →", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                }
            }
            Button(
                onClick = onSetStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text("Set My Stop", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Recent Trips",
                color = InkNavy,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            if (recentTrips.isEmpty()) {
                Text(
                    text = "No saved trips yet. Set your stop above to start your first ride.",
                    color = MetaNavy,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                recentTrips.take(4).forEach { log ->
                    val distKm = String.format(Locale.US, "%.1f", log.totalDistanceM / 1000.0).toDouble()
                    val dateText = SimpleDateFormat("MMM d", Locale.US).format(Date(log.startedAt))
                    val parts = log.routeLabel.split("→").map { it.trim() }
                    TripRow(
                        trip = RecentTrip(
                            from = parts.getOrNull(0)?.ifBlank { "Origin" } ?: "Origin",
                            to = parts.getOrNull(1)?.ifBlank { log.routeId } ?: log.routeLabel.ifBlank { log.routeId },
                            distanceKm = distKm,
                            dateText = dateText,
                            result = if (log.result == "ON_TIME") AlertResult.ON_TIME else AlertResult.LATE
                        ),
                        mode = log.mode,
                        onClick = onOpenTrips
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PillField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    text: String,
    textColor: Color,
    trailingLoading: Boolean = false,
    showRefreshIcon: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            Spacer(Modifier.width(16.dp))
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailingLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ActionBlue
                )
            } else if (showRefreshIcon) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reload GPS",
                    tint = ActionBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TripRow(trip: RecentTrip, mode: VehicleMode = VehicleMode.BUS, onClick: () -> Unit = {}) {
    val icon = when (mode) {
        VehicleMode.JEEPNEY -> Icons.Filled.LocalTaxi
        VehicleMode.BUS -> Icons.Filled.DirectionsBus
        VehicleMode.TRAIN -> Icons.Filled.DirectionsTransit
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ActionBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = mode.name,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = "${trip.from} → ${trip.to}",
                color = InkNavy,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            val resultColor = if (trip.result == AlertResult.ON_TIME) SuccessGreen else AlertOrange
            Text(
                text = buildAnnotatedString {
                    append("${trip.distanceKm} km | ${trip.dateText} | ")
                    withStyle(SpanStyle(color = resultColor, fontWeight = FontWeight.Bold)) {
                        append(trip.result.label)
                    }
                },
                color = MetaNavy,
                fontSize = 14.sp
            )
        }
    }
}
