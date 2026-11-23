package com.parapo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.storage.TripLog
import com.parapo.storage.TripLogCsv
import com.parapo.storage.TripLogStore
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.AlertOrange
import com.parapo.ui.theme.InkNavy
import com.parapo.ui.theme.MetaNavy
import com.parapo.ui.theme.ScreenBg
import com.parapo.ui.theme.SuccessGreen
import com.parapo.ui.theme.SurfaceLight
import com.parapo.util.VehicleMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S8 My Trips (FR-15, Build Guide 6.3 & 6.6).
 * Shows real saved trips from trips.json with On time / Late badges and CSV export.
 */
@Composable
fun TripsScreen() {
    val context = LocalContext.current
    val trips by TripLogStore.trips.collectAsState()
    var selectedTrip by remember { mutableStateOf<TripLog?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Trips",
                color = InkNavy,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val file = TripLogCsv.exportToFile(context, trips)
                    exportMessage = "Exported ${trips.size} trip(s) to ${file.name}"
                },
                enabled = trips.isNotEmpty(),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export CSV", fontWeight = FontWeight.SemiBold)
            }
        }

        if (exportMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuccessGreen.copy(alpha = 0.14f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exportMessage!!,
                    color = InkNavy,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { exportMessage = null }) {
                    Text("OK", color = ActionBlue, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (trips.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Sulo(pose = SuloPose.CALM, size = 150.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No trips recorded yet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkNavy
                )
                Text(
                    text = "Complete a ride and tap Save to My Trips to log alert accuracy and export CSV metrics.",
                    fontSize = 15.sp,
                    color = MetaNavy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trips, key = { it.id }) { trip ->
                    TripLogCard(trip = trip, onClick = { selectedTrip = trip })
                }
            }
        }
    }

    selectedTrip?.let { trip ->
        val errorM = trip.alertFiredAtRemainingM - trip.triggerM
        AlertDialog(
            onDismissRequest = { selectedTrip = null },
            title = { Text(trip.routeLabel.ifBlank { "Route ${trip.routeId}" }, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("Mode", trip.mode.name)
                    DetailLine("Result", if (trip.result == "ON_TIME") "On time" else "Late")
                    DetailLine("Target trigger", "${trip.triggerM} m")
                    DetailLine("Alert fired at", "${trip.alertFiredAtRemainingM} m (${if (errorM >= 0) "+" else ""}$errorM m)")
                    DetailLine("Position source", trip.source)
                    DetailLine("GPS lost", "${trip.gpsLostS} s")
                    DetailLine("New cell fingerprints", "${trip.newFingerprints}")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = TripLogCsv.exportToFile(context, trips)
                        exportMessage = "Exported ${trips.size} trip(s) to ${file.name}"
                        selectedTrip = null
                    }
                ) { Text("Export CSV") }
            },
            dismissButton = {
                TextButton(onClick = { selectedTrip = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun TripLogCard(trip: TripLog, onClick: () -> Unit) {
    val isOnTime = trip.result == "ON_TIME"
    val badgeColor = if (isOnTime) SuccessGreen else AlertOrange
    val badgeText = if (isOnTime) "On time" else "Late"
    val dateStr = remember(trip.startedAt) {
        SimpleDateFormat("MMM d | HH:mm", Locale.US).format(Date(trip.startedAt))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
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
                    imageVector = vehicleIcon(trip.mode),
                    contentDescription = trip.mode.name,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.routeLabel.ifBlank { "Route ${trip.routeId}" },
                    color = InkNavy,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$dateStr • ${trip.source}",
                    color = MetaNavy,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceLight, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MetaNavy)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = InkNavy)
    }
}

private fun vehicleIcon(mode: VehicleMode): ImageVector = when (mode) {
    VehicleMode.JEEPNEY -> Icons.Filled.LocalTaxi
    VehicleMode.BUS -> Icons.Filled.DirectionsBus
    VehicleMode.TRAIN -> Icons.Filled.DirectionsTransit
}
