package com.parapo.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.parapo.auth.AuthGate
import com.parapo.storage.SettingsStore
import com.parapo.storage.TripLog
import com.parapo.storage.TripLogStore
import com.parapo.trip.TripController
import com.parapo.trip.TripPhase
import com.parapo.ui.theme.ActionBlue
import com.parapo.ui.theme.InactiveGray
import com.parapo.ui.theme.InkNavy
import com.parapo.util.PermissionHelper
import com.parapo.util.VehicleMode
import kotlin.math.abs
import kotlin.math.roundToInt

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.LIVE, "Live", Icons.Filled.Map),
    Tab(Routes.TRIPS, "My Trips", Icons.Filled.History),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun ParaPoApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.bottomBarRoutes
    val appContext = LocalContext.current.applicationContext
    val trip by TripController.state.collectAsState()
    val savedTrips by TripLogStore.trips.collectAsState()

    // Load persisted settings and trip history once at app launch.
    LaunchedEffect(Unit) {
        SettingsStore.load(appContext)
        TripLogStore.load(appContext)
    }

    // When the controller decides it is time to alert, open S6 (once).
    LaunchedEffect(trip.phase) {
        // Collect data for S7 Summary
        if (trip.phase == TripPhase.IDLE) {
            TripSummaryStore.clear()
        } else if (TripSummaryStore.startedAtMs == 0L) {
            TripSummaryStore.startedAtMs = trip.startedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val bm = appContext.getSystemService(android.os.BatteryManager::class.java)
            val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 0..100) TripSummaryStore.batteryStartPct = pct
        }
        if (trip.phase == TripPhase.ALERTING && TripSummaryStore.firedRemainingM == null) {
            TripSummaryStore.firedRemainingM = trip.firedAtRemainingM ?: trip.remainingM
        }
        if (trip.phase == TripPhase.ALERTING && navController.currentDestination?.route != Routes.ALERT) {
            navController.navigate(Routes.ALERT)
        }
        if (trip.phase == TripPhase.COMPLETED && navController.currentDestination?.route != Routes.SUMMARY) {
            navController.navigate(Routes.SUMMARY) {
                popUpTo(Routes.HOME)
            }
        }
    }

    // Remember the largest remaining distance seen, so Total Ride = that minus where we are now.
    LaunchedEffect(trip.remainingM) {
        if (trip.phase != TripPhase.IDLE && trip.remainingM > TripSummaryStore.maxRemainingM) {
            TripSummaryStore.maxRemainingM = trip.remainingM
        }
    }

    // Return to Home, dropping everything above it.
    val backToHome: () -> Unit = {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // each screen handles its own insets
        bottomBar = { if (showBottomBar) ParaPoBottomBar(navController, currentRoute) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(onFinished = {
                    val destination = when (trip.phase) {
                        TripPhase.TRACKING, TripPhase.ARMED -> Routes.LIVE
                        TripPhase.ALERTING -> Routes.ALERT
                        TripPhase.COMPLETED -> Routes.SUMMARY
                        else -> if (AuthGate.hasAccount(appContext)) Routes.LOGIN else Routes.SIGNUP
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    onSuccess = {
                        val nextRoute = if (PermissionHelper.hasAllRequiredPermissions(appContext)) {
                            Routes.HOME
                        } else {
                            Routes.PERMISSIONS
                        }
                        navController.navigate(nextRoute) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onGoToSignup = {
                        navController.navigate(Routes.SIGNUP) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.SIGNUP) {
                SignupScreen(
                    onSuccess = {
                        val nextRoute = if (PermissionHelper.hasAllRequiredPermissions(appContext)) {
                            Routes.HOME
                        } else {
                            Routes.PERMISSIONS
                        }
                        navController.navigate(nextRoute) {
                            popUpTo(Routes.SIGNUP) { inclusive = true }
                        }
                    },
                    onGoToLogin = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SIGNUP) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    userName = AuthGate.getUsername(appContext),
                    recentTrips = savedTrips,
                    activeTripPhase = trip.phase,
                    onOpenLiveTrip = { navController.navigate(Routes.LIVE) },
                    onSetStop = { navController.navigate(Routes.TRIP_SETUP) },
                    onTestAlert = { navController.navigate(Routes.ALERT) },
                    onOpenTrips = {
                        navController.navigate(Routes.TRIPS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Routes.TRIP_SETUP) {
                TripSetupScreen(
                    onBack = { navController.popBackStack() },
                    onStartTrip = { setup ->
                        if (!PermissionHelper.hasAllRequiredPermissions(appContext)) {
                            navController.navigate(Routes.PERMISSIONS)
                        } else {
                            TripController.start(setup, appContext)
                            navController.navigate(Routes.LIVE) { popUpTo(Routes.HOME) }
                        }
                    }
                )
            }
            composable(Routes.LIVE) {
                LiveTrackerScreen(
                    onSetStop = { navController.navigate(Routes.TRIP_SETUP) },
                    onCancelled = backToHome
                )
            }
            composable(Routes.ALERT) {
                AlertScreen(
                    onAwake = {
                        if (trip.phase == TripPhase.ALERTING) {
                            TripSummaryStore.gpsLostS = trip.gpsLostS
                            TripSummaryStore.newFingerprints = trip.newFingerprints
                            TripSummaryStore.finish(
                                remainingNowM = trip.remainingM,
                                fallbackStartedAtMs = trip.startedAtMs,
                                fallbackFiredRemainingM = trip.firedAtRemainingM ?: trip.remainingM
                            )
                            TripController.dismissAlert()
                            navController.navigate(Routes.SUMMARY) {
                                popUpTo(Routes.ALERT) { inclusive = true }
                            }
                        } else {
                            // Dismissing the S3 "Test alert" preview — stop alarm without touching any trip
                            com.parapo.alert.AlertManager.stop(appContext)
                            navController.popBackStack()
                        }
                    }
                )
            }
            composable(Routes.SUMMARY) {
                SummaryScreen(onSave = {
                    val setup = trip.setup
                    val summary = TripSummaryStore.last
                    val triggerM = trip.triggerM.roundToInt().takeIf { it > 0 }
                        ?: setup?.alertDistanceM
                        ?: SettingsStore.current(appContext).alertDistanceM
                    val firedM = summary.firedAtRemainingM?.roundToInt() ?: triggerM
                    val isOnTime = abs(firedM - triggerM) <= 300
                    val mode = runCatching { VehicleMode.valueOf(setup?.vehicle ?: "BUS") }
                        .getOrDefault(VehicleMode.BUS)
                    val destLabel = setup?.destName?.takeIf { it.isNotBlank() }
                        ?: if (setup != null) {
                            String.format(java.util.Locale.US, "%.4f, %.4f", setup.destLat, setup.destLon)
                        } else {
                            "Selected Stop"
                        }
                    val sourceLabel = when {
                        trip.usedCellFallback -> "GPS+CELL"
                        trip.source == com.parapo.trip.PositionSourceKind.DEAD_RECKONING -> "DEAD_RECKONING"
                        else -> "GPS"
                    }
                    val bm = appContext.getSystemService(android.os.BatteryManager::class.java)
                    val endPct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        ?.takeIf { it in 0..100 } ?: -1
                    TripLogStore.append(
                        appContext,
                        TripLog(
                            id = "t_${System.currentTimeMillis()}",
                            routeId = trip.routeId.ifBlank { "r_${setup?.destLat?.hashCode() ?: 0}" },
                            startedAt = TripSummaryStore.startedAtMs.takeIf { it > 0L }
                                ?: trip.startedAtMs.takeIf { it > 0L }
                                ?: System.currentTimeMillis(),
                            endedAt = System.currentTimeMillis(),
                            mode = mode,
                            triggerM = triggerM,
                            alertFiredAtRemainingM = firedM,
                            source = sourceLabel,
                            gpsLostS = trip.gpsLostS.toInt(),
                            newFingerprints = trip.newFingerprints,
                            result = if (isOnTime) "ON_TIME" else "LATE",
                            routeLabel = "Current Location → $destLabel",
                            totalDistanceM = (summary.totalRideM?.takeIf { it > 0.0 } ?: trip.totalM).roundToInt(),
                            batteryStartPct = TripSummaryStore.batteryStartPct,
                            batteryEndPct = endPct
                        )
                    )
                    TripController.reset()
                    backToHome()
                })
            }
            composable(Routes.TRIPS) { TripsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) })
            }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onContinue = backToHome)
            }
        }
    }
}

@Composable
private fun ParaPoBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ActionBlue,
                    selectedTextColor = ActionBlue,
                    unselectedIconColor = InactiveGray,
                    unselectedTextColor = InkNavy,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

