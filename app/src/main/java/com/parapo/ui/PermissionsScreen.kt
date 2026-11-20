package com.parapo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parapo.ui.components.Sulo
import com.parapo.ui.components.SuloPose
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.InactiveGray
import com.parapo.ui.theme.InkNavy
import com.parapo.ui.theme.MetaNavy
import com.parapo.ui.theme.SuccessGreen
import com.parapo.ui.theme.SurfaceLight
import com.parapo.util.PermissionHelper

/**
 * S10 Permissions Wizard (Build Guide 6.3, 6.6, 7.3, FR-05, FR-14).
 * Guides the commuter through required location, notification, full-screen alert,
 * and battery exemption permissions before arming a trip.
 */
@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Incrementing tick forces recomposition whenever the user returns from system settings or dialogs.
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val fineGranted = remember(refreshTick) { PermissionHelper.hasFineLocation(context) }
    val bgGranted = remember(refreshTick) { PermissionHelper.hasBackgroundLocation(context) }
    val notifGranted = remember(refreshTick) { PermissionHelper.hasNotifications(context) }
    val fullScreenGranted = remember(refreshTick) { PermissionHelper.hasFullScreenIntent(context) }
    val batteryUnrestricted = remember(refreshTick) { PermissionHelper.isIgnoringBatteryOptimizations(context) }
    val requiredDone = fineGranted && bgGranted && notifGranted

    var fineRequested by remember { mutableStateOf(false) }
    var bgRequested by remember { mutableStateOf(false) }
    var notifRequested by remember { mutableStateOf(false) }

    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        fineRequested = true
        refreshTick++
    }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        bgRequested = true
        refreshTick++
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifRequested = true
        refreshTick++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        Sulo(pose = SuloPose.SETUP, size = 150.dp)

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Before we ride",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = InkNavy
        )
        Text(
            text = "Allow these so Para Po! can wake you up even with the screen off.",
            fontSize = 15.sp,
            color = MetaNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp, start = 8.dp, end = 8.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PermissionRow(
                icon = Icons.Filled.MyLocation,
                title = "Precise location (required)",
                subtitle = "Measures route distance and reads cell towers.",
                granted = fineGranted,
                onAllow = {
                    if (fineRequested && !fineGranted) {
                        PermissionHelper.launchSettingsSafely(
                            context,
                            PermissionHelper.openAppSettingsIntent(context)
                        )
                    } else {
                        fineLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            )

            PermissionRow(
                icon = Icons.Filled.LocationOn,
                title = "Location: allow all the time (required)",
                subtitle = if (fineGranted) {
                    "Keeps tracking your stop while your screen is off."
                } else {
                    "Allow precise location first, then set to All the time."
                },
                granted = bgGranted,
                enabled = fineGranted,
                onAllow = {
                    if (bgRequested && !bgGranted) {
                        PermissionHelper.launchSettingsSafely(
                            context,
                            PermissionHelper.openAppSettingsIntent(context)
                        )
                    } else {
                        bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
            )

            PermissionRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications (required)",
                subtitle = "Shows live trip progress and stop alerts.",
                granted = notifGranted,
                onAllow = {
                    if (notifRequested && !notifGranted) {
                        PermissionHelper.launchSettingsSafely(
                            context,
                            PermissionHelper.openAppSettingsIntent(context)
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            PermissionRow(
                icon = Icons.Filled.NotificationsActive,
                title = "Full-screen alerts",
                subtitle = "Shows the wake-up screen over your lock screen.",
                granted = fullScreenGranted,
                onAllow = {
                    PermissionHelper.launchSettingsSafely(
                        context,
                        PermissionHelper.fullScreenIntentSettingsIntent(context)
                    )
                }
            )

            PermissionRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Battery unrestricted",
                subtitle = "Prevents your phone from pausing tracking mid-ride.",
                granted = batteryUnrestricted,
                onAllow = {
                    PermissionHelper.launchSettingsSafely(
                        context,
                        PermissionHelper.requestBatteryOptimizationIntent(context)
                    )
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onContinue,
            enabled = requiredDone,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = ActionBlue,
                contentColor = Color.White,
                disabledContainerColor = InactiveGray.copy(alpha = 0.45f),
                disabledContentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Continue", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        if (!requiredDone) {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "Set up later (trips cannot be armed yet)",
                    fontSize = 13.sp,
                    color = MetaNavy
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    enabled: Boolean = true,
    onAllow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceLight, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) SuccessGreen else ActionBlue,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = InkNavy,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkNavy
                )
            }
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MetaNavy,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (!granted) {
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = onAllow,
                enabled = enabled,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "Allow",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) ActionBlue else InactiveGray
                )
            }
        }
    }
}
