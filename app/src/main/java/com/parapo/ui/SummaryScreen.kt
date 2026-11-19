package com.parapo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.R
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.InkNavy
import java.util.Locale
import kotlin.math.roundToInt

private val SurfaceLight = Color(0xFFEAF4FF)

/**
 * S7 Summary. Rows: Total Ride, Duration, GPS Active, Alert fired at.
 */
@Composable
fun SummaryScreen(onSave: () -> Unit) {
    val s = remember { TripSummaryStore.last }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Arrived pose (already includes the green check)
        Image(
            painter = painterResource(R.drawable.sulo_arrived),
            contentDescription = "Sulo has arrived",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.8f).height(190.dp)
        )

        Spacer(Modifier.height(12.dp))
        Text("You have arrived!", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = InkNavy)
        Text("Salamat sa biyahe!", fontSize = 16.sp, color = InkNavy.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceLight, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ResultRow(Icons.Filled.DirectionsBus, "Total Ride", s.totalRideM?.let { fmtKm(it) } ?: "—")
            ResultRow(Icons.Filled.Schedule, "Duration", s.durationS?.let { fmtDuration(it) } ?: "—")
            ResultRow(Icons.Filled.GpsFixed, "GPS Active", s.gpsActiveS?.let { fmtSpan(it) } ?: "—")
            ResultRow(Icons.Filled.NotificationsActive, "Alert fired at", s.firedAtRemainingM?.let { fmtKm(it) } ?: "—")
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onSave,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = ActionBlue, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Save to My Trips", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 16.sp, color = InkNavy, modifier = Modifier.padding(start = 14.dp).weight(1f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = InkNavy)
    }
}

private fun fmtKm(m: Double): String =
    if (m >= 1000.0) String.format(Locale.US, "%.2f km", m / 1000.0) else "${m.roundToInt()} m"

private fun fmtDuration(totalS: Long): String =
    String.format(Locale.US, "%d:%02d min", totalS / 60, totalS % 60)

private fun fmtSpan(s: Long): String =
    if (s < 60) "$s s" else "${s / 60} min ${s % 60} s"
