package com.parapo.util

/** All tunable constants (build guide 7.5). Change values here, not inside the logic. */
object Constants {
    // Alert distance
    const val DEFAULT_ALERT_DISTANCE_M = 3000
    const val MIN_TRIGGER_M = 1000
    const val DEFAULT_SAFETY_MARGIN_M = 500

    // Lead time in seconds per vehicle type (starting values)
    const val LEAD_TIME_JEEPNEY_S = 240
    const val LEAD_TIME_BUS_S = 300
    const val LEAD_TIME_TRAIN_S = 360

    // Speed smoothing (EMA)
    const val SPEED_EMA_ALPHA = 0.3

    // Straight-line fallback when no route is cached
    const val ROAD_FACTOR = 1.25

    // Alert decision
    const val HITS_REQUIRED = 2
    const val MOVING_TOWARD_MIN_PROGRESS_M = 30
    const val MOVING_TOWARD_WINDOW_S = 20
    const val MOVING_TOWARD_MIN_SPEED_MPS = 1.0

    // GPS loss and dead reckoning
    const val GPS_LOST_ACCURACY_M = 50
    const val GPS_LOST_TIMEOUT_S = 10
    const val DEAD_RECKONING_TRUST_S = 180
    const val FINGERPRINT_ACCURACY_M = 30
    const val FINGERPRINT_STEP_M = 200

    // Escalation ladder
    const val VIBRATE_REPEAT_S = 3
    const val SOUND_AFTER_S = 10
    const val FULLSCREEN_AFTER_S = 20

    // PIN hash
    const val PBKDF2_ITERATIONS = 120_000

    // Route download target
    const val ROUTE_DOWNLOAD_TIMEOUT_S = 10

    fun leadTimeSeconds(mode: VehicleMode): Int = when (mode) {
        VehicleMode.JEEPNEY -> LEAD_TIME_JEEPNEY_S
        VehicleMode.BUS -> LEAD_TIME_BUS_S
        VehicleMode.TRAIN -> LEAD_TIME_TRAIN_S
    }
}

enum class VehicleMode { JEEPNEY, BUS, TRAIN }
