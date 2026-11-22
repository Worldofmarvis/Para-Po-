package com.parapo.distance

import com.parapo.util.Constants
import com.parapo.util.VehicleMode
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FR-07 / FR-08 / FR-09: snaps a position to the route polyline, computes remaining distance,
 * smooths speed via EMA, and calculates the speed-adaptive trigger distance.
 * [scale] is 1.0 for a real road route, or ROAD_FACTOR (1.25) when only a straight line exists.
 */
class DistanceEngine(val points: List<GeoPoint>, private val scale: Double = 1.0) {

    val cumulativeM = DoubleArray(points.size)
    val lengthM: Double
    private var lastProgress = 0.0

    init {
        for (i in 1 until points.size) cumulativeM[i] = cumulativeM[i - 1] + haversineM(points[i - 1], points[i])
        lengthM = if (points.size > 1) cumulativeM.last() else 0.0
    }

    /** Meters from the route start, monotonic (never going backward by more than 50 m). */
    fun snap(p: GeoPoint): Double {
        if (points.size < 2) return 0.0
        val from = segmentAt(max(0.0, lastProgress - 200.0))
        val to = segmentAt(min(lengthM, lastProgress + 5000.0))
        var bestDist = Double.MAX_VALUE
        var bestProgress = lastProgress
        for (i in from..to) {
            val a = points[i]
            val b = points[i + 1]
            val mLat = 111_320.0
            val mLon = 111_320.0 * cos(Math.toRadians(p.latitude))
            val bx = (b.longitude - a.longitude) * mLon
            val by = (b.latitude - a.latitude) * mLat
            val px = (p.longitude - a.longitude) * mLon
            val py = (p.latitude - a.latitude) * mLat
            val len2 = bx * bx + by * by
            val t = if (len2 <= 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val dx = px - t * bx
            val dy = py - t * by
            val d = sqrt(dx * dx + dy * dy)
            if (d < bestDist) {
                bestDist = d
                bestProgress = cumulativeM[i] + t * (cumulativeM[i + 1] - cumulativeM[i])
            }
        }
        lastProgress = max(bestProgress, lastProgress - 50.0)
        return lastProgress
    }

    fun remainingM(progress: Double): Double = (lengthM - progress).coerceAtLeast(0.0) * scale

    /** The point [progressM] meters along the route (used by GPX/route replay). */
    fun pointAt(progressM: Double): GeoPoint {
        if (points.size < 2) return points.firstOrNull() ?: GeoPoint(0.0, 0.0)
        val m = progressM.coerceIn(0.0, lengthM)
        val i = segmentAt(m)
        val seg = cumulativeM[i + 1] - cumulativeM[i]
        val t = if (seg <= 0.0) 0.0 else (m - cumulativeM[i]) / seg
        val a = points[i]
        val b = points[i + 1]
        return GeoPoint(
            a.latitude + (b.latitude - a.latitude) * t,
            a.longitude + (b.longitude - a.longitude) * t
        )
    }

    private fun segmentAt(m: Double): Int {
        var i = 0
        while (i < points.size - 2 && cumulativeM[i + 1] <= m) i++
        return i
    }

    companion object {
        fun haversineM(a: GeoPoint, b: GeoPoint): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * atan2(sqrt(h), sqrt(1 - h))
        }

        /** FR-08: Straight-line fallback distance scaled by roadFactor (default 1.25). */
        fun straightLineFallbackM(
            position: GeoPoint,
            dest: GeoPoint,
            roadFactor: Double = Constants.ROAD_FACTOR
        ): Double = haversineM(position, dest) * roadFactor

        /** Exponential Moving Average (EMA) speed filter (alpha = 0.3 per Build Guide 7.5). */
        fun smoothSpeedEma(
            prevSpeedMps: Double?,
            currentSpeedMps: Double,
            alpha: Double = Constants.SPEED_EMA_ALPHA
        ): Double {
            val clamped = currentSpeedMps.coerceAtLeast(0.0)
            return if (prevSpeedMps == null || prevSpeedMps < 0.0) {
                clamped
            } else {
                alpha * clamped + (1.0 - alpha) * prevSpeedMps
            }
        }

        /**
         * FR-09: Speed-adaptive trigger distance (Build Guide 7.5):
         * clamp(v * leadTime, MIN_TRIGGER_M = 1000, alertDistanceM) + safetyMarginM if estimated.
         */
        fun computeTriggerDistanceM(
            smoothedSpeedMps: Double,
            mode: VehicleMode,
            alertDistanceM: Int = Constants.DEFAULT_ALERT_DISTANCE_M,
            isEstimatedSource: Boolean = false,
            safetyMarginM: Int = Constants.DEFAULT_SAFETY_MARGIN_M
        ): Double {
            val leadS = Constants.leadTimeSeconds(mode)
            val maxTrigger = max(Constants.MIN_TRIGGER_M, alertDistanceM).toDouble()
            val base = (smoothedSpeedMps * leadS).coerceIn(
                Constants.MIN_TRIGGER_M.toDouble(),
                maxTrigger
            )
            return if (isEstimatedSource) base + safetyMarginM else base
        }

        /** Local ETA in minutes; hidden (null) when speed < 1 m/s (Build Guide 6.3). */
        fun computeEtaMinutes(remainingM: Double, smoothedSpeedMps: Double): Int? =
            if (smoothedSpeedMps >= Constants.MOVING_TOWARD_MIN_SPEED_MPS) {
                max(1, ceil(remainingM / smoothedSpeedMps / 60.0).toInt())
            } else {
                null
            }

        /**
         * Alert decision movingToward check (Build Guide 7.5, TC-09):
         * true if progress increased by >= 30 m over the last 20 s OR speed > 1 m/s while not retreating.
         */
        fun isMovingToward(progressDeltaM: Double, speedMps: Double): Boolean =
            progressDeltaM >= Constants.MOVING_TOWARD_MIN_PROGRESS_M ||
                (speedMps > Constants.MOVING_TOWARD_MIN_SPEED_MPS && progressDeltaM >= 0.0)
    }
}
