package com.parapo.ui

/** Immutable result shown on S7 (Build Guide 6.3 & 6.6). Null means "not measured" (shown as a dash). */
data class TripSummary(
    val totalRideM: Double?,
    val durationS: Long?,
    val gpsLostS: Long?,
    val firedAtRemainingM: Double?,
    val fingerprintsSaved: Int = 0
)

/**
 * In-memory collector for the current trip. ParaPoApp / AlertActivity fills it as the trip state changes
 * and calls finish() when the user taps "GISING NA AKO".
 */
object TripSummaryStore {
    var startedAtMs: Long = 0L
    var maxRemainingM: Double = 0.0
    var firedRemainingM: Double? = null
    var gpsLostS: Long = 0L
    var newFingerprints: Int = 0
    var batteryStartPct: Int = -1

    /** Last finished trip, read by SummaryScreen. */
    var last: TripSummary = TripSummary(null, null, null, null, 0)
        private set

    fun finish(
        remainingNowM: Double,
        nowMs: Long = System.currentTimeMillis(),
        fallbackStartedAtMs: Long = 0L,
        fallbackFiredRemainingM: Double? = null
    ) {
        val effectiveStart = if (startedAtMs > 0L) startedAtMs else fallbackStartedAtMs
        val effectiveFired = firedRemainingM ?: fallbackFiredRemainingM
        val tracked = effectiveStart > 0L
        val duration = if (tracked) ((nowMs - effectiveStart) / 1000L).coerceAtLeast(0L) else null
        last = TripSummary(
            totalRideM = if (tracked && maxRemainingM > 0.0) (maxRemainingM - remainingNowM).coerceAtLeast(0.0) else null,
            durationS = duration,
            gpsLostS = if (tracked) gpsLostS else null,
            firedAtRemainingM = effectiveFired,
            fingerprintsSaved = newFingerprints
        )
    }

    /** Clears the in-progress fields (not `last`). */
    fun clear() {
        startedAtMs = 0L
        maxRemainingM = 0.0
        firedRemainingM = null
        gpsLostS = 0L
        newFingerprints = 0
        batteryStartPct = -1
    }
}
