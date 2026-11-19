package com.parapo.ui

/** Immutable result shown on S7. Null means "not measured" (shown as a dash). */
data class TripSummary(
    val totalRideM: Double?,
    val durationS: Long?,
    val gpsActiveS: Long?,
    val firedAtRemainingM: Double?
)

/**
 * In-memory collector for the current trip. ParaPoApp fills it as the trip state changes
 * and calls finish() when the user taps "GISING NA AKO". Persisting to My Trips (FR-15)
 * comes with the Storage step. gpsLostS stays 0 until FallbackEngine exists, so GPS Active
 * currently equals the full duration.
 */
object TripSummaryStore {
    var startedAtMs: Long = 0L
    var maxRemainingM: Double = 0.0
    var firedRemainingM: Double? = null
    var gpsLostS: Long = 0L

    /** Last finished trip, read by SummaryScreen. */
    var last: TripSummary = TripSummary(null, null, null, null)
        private set

    fun finish(remainingNowM: Double, nowMs: Long = System.currentTimeMillis()) {
        val tracked = startedAtMs > 0L
        val duration = if (tracked) (nowMs - startedAtMs) / 1000L else null
        last = TripSummary(
            totalRideM = if (tracked && maxRemainingM > 0.0) (maxRemainingM - remainingNowM).coerceAtLeast(0.0) else null,
            durationS = duration,
            gpsActiveS = duration?.let { (it - gpsLostS).coerceAtLeast(0L) },
            firedAtRemainingM = firedRemainingM
        )
    }

    /** Clears the in-progress fields (not `last`). */
    fun clear() {
        startedAtMs = 0L
        maxRemainingM = 0.0
        firedRemainingM = null
        gpsLostS = 0L
    }
}
