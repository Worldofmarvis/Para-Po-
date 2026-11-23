package com.parapo.ui

/** Screen routes. S-numbers match build guide 6.2. */
object Routes {
    const val SPLASH = "splash"           // S1
    const val LOGIN = "login"             // S2
    const val SIGNUP = "signup"           // S2b (create account)
    const val HOME = "home"               // S3
    const val TRIP_SETUP = "trip_setup"   // S4
    const val LIVE = "live"               // S5
    const val ALERT = "alert"             // S6
    const val SUMMARY = "summary"         // S7
    const val TRIPS = "trips"             // S8
    const val SETTINGS = "settings"       // S9
    const val PERMISSIONS = "permissions" // S10

    /** Bottom bar is shown only on these (hidden on S1, S2, S2b, S4, S6, S7, S10). */
    val bottomBarRoutes = setOf(HOME, LIVE, TRIPS, SETTINGS)
}
