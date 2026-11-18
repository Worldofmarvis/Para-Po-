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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.AlertOrange
import com.parapo.ui.theme.AvatarBg
import com.parapo.ui.theme.AvatarIcon
import com.parapo.ui.theme.DangerRed
import com.parapo.ui.theme.InkNavy
import com.parapo.ui.theme.MetaNavy
import com.parapo.ui.theme.ScreenBg
import com.parapo.ui.theme.SuccessGreen

/**
 * S3 Home, laid out like the mockup (Figure 2) with the 6.6 corrections:
 * no ETB fares, no Ethiopian places, "Set My Stop", "Get an alert before your stop".
 * Fake data for now.
 * TODO: "Trip in progress" card (opens S5) when TripController is ARMED/TRACKING.
 * TODO: username from AuthGate/Storage; trips from Storage.
 */
@Composable
fun HomeScreen(
    userName: String = "Juan",
    onSetStop: () -> Unit,
    onTestAlert: () -> Unit
) {
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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AvatarBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = AvatarIcon,
                    modifier = Modifier.size(32.dp)
                )
            }
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
            Text(
                text = "Get an alert before your stop",
                color = InkNavy,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))
            // Read-only. TODO: show live GPS status from LocationSource.
            PillField(
                icon = Icons.Filled.Place,
                iconTint = ActionBlue,
                text = "Your location (GPS)",
                textColor = ActionBlue
            )
            Spacer(Modifier.height(10.dp))
            PillField(
                icon = Icons.Filled.Place,
                iconTint = DangerRed,
                text = "Where do you get off?",
                textColor = InkNavy
            )

            Spacer(Modifier.height(14.dp))
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
            fakeRecentTrips.forEach { trip -> TripRow(trip) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PillField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    text: String,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            Spacer(Modifier.width(16.dp))
            Text(text = text, color = textColor, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TripRow(trip: RecentTrip) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                imageVector = Icons.Filled.DirectionsBus,
                contentDescription = null,
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
