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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.parapo.ui.theme.DeepBlue
import com.parapo.ui.theme.PrimaryBlue
import com.parapo.ui.theme.SurfaceLight

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
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.LOGIN) {
                LoginScreen(onSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                })
            }
            composable(Routes.HOME) {
                PlaceholderScreen(
                    title = "S3 Home", isTab = true,
                    actions = listOf(
                        "Set My Stop" to { navController.navigate(Routes.TRIP_SETUP) },
                        "Bell: test alert" to { navController.navigate(Routes.ALERT) }
                    )
                )
            }
            composable(Routes.TRIP_SETUP) {
                PlaceholderScreen(
                    title = "S4 Trip Setup", isTab = false,
                    actions = listOf(
                        "Start Trip" to {
                            navController.navigate(Routes.LIVE) { popUpTo(Routes.HOME) }
                        },
                        "Back" to { navController.popBackStack() }
                    )
                )
            }
            composable(Routes.LIVE) {
                PlaceholderScreen(
                    title = "S5 Live Tracker", isTab = true,
                    actions = listOf(
                        "Fake alert" to { navController.navigate(Routes.ALERT) },
                        "Cancel Trip" to backToHome
                    )
                )
            }
            composable(Routes.ALERT) {
                PlaceholderScreen(
                    title = "S6 Alert", isTab = false,
                    actions = listOf("GISING NA AKO" to { navController.navigate(Routes.SUMMARY) })
                )
            }
            composable(Routes.SUMMARY) {
                PlaceholderScreen(
                    title = "S7 Summary", isTab = false,
                    actions = listOf("Save to My Trips" to backToHome)
                )
            }
            composable(Routes.TRIPS) { PlaceholderScreen(title = "S8 My Trips", isTab = true) }
            composable(Routes.SETTINGS) { PlaceholderScreen(title = "S9 Settings", isTab = true) }
            composable(Routes.PERMISSIONS) {
                PlaceholderScreen(
                    title = "S10 Permissions", isTab = false,
                    actions = listOf("Continue" to backToHome)
                )
            }
        }
    }
}

@Composable
private fun ParaPoBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
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
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = DeepBlue,
                    indicatorColor = SurfaceLight
                )
            )
        }
    }
}
