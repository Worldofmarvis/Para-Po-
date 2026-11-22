package com.parapo.fallback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.parapo.cell.ServingCell
import com.parapo.storage.Fingerprint
import com.parapo.trip.PositionSourceKind
import com.parapo.util.Constants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Result of a single fallback position estimation cycle (Build Guide 7.5). */
data class FallbackEstimate(
    val progressM: Double,
    val source: PositionSourceKind,
    val lowConfidence: Boolean,
    val safetyMarginMultiplier: Int // 1 normally in fallback; 2 when outage > 180 s (TC-06)
)

/**
 * FR-10 & FR-11: GPS-loss detection, cell-tower fingerprint matching, and route-bound dead reckoning
 * (Build Guide 7.5, TC-04, TC-05, TC-06, TC-13).
 */
object FallbackEngine {

    /** True when no GPS fix with accuracy <= 50 m has arrived within the last 10 s. */
    fun isGpsLost(nowMs: Long, lastGoodFixMs: Long): Boolean {
        if (lastGoodFixMs <= 0L) return false
        return (nowMs - lastGoodFixMs) > Constants.GPS_LOST_TIMEOUT_S * 1000L
    }

    fun isGoodGpsFix(accuracyM: Float): Boolean =
        accuracyM in 0f..Constants.GPS_LOST_ACCURACY_M.toFloat()

    /**
     * Estimates route progress when GPS is unavailable:
     *   dr  = lastProgress + v * secondsSinceLastFix (paused if motion sensor confirms stationary)
     *   est = if (fp != null && fp.count >= 1) clamp(fp.meanProgressM, lastProgress, dr * 1.5 - lastProgress * 0.5) else dr
     * If outage exceeds [trustLimitS] (default 180 s), marks low confidence and adds one extra safety margin.
     */
    fun estimateProgress(
        lastProgressM: Double,
        lastKnownSpeedMps: Double,
        secondsSinceLastFix: Double,
        routeLengthM: Double,
        servingCell: ServingCell?,
        fingerprints: Map<String, Fingerprint>,
        trustLimitS: Int = Constants.DEAD_RECKONING_TRUST_S,
        isVehicleMoving: Boolean = true
    ): FallbackEstimate {
        val elapsedS = secondsSinceLastFix.coerceAtLeast(0.0)
        val effectiveSpeed = if (isVehicleMoving) lastKnownSpeedMps.coerceAtLeast(0.0) else 0.0
        val dr = (lastProgressM + effectiveSpeed * elapsedS).coerceIn(lastProgressM, max(lastProgressM, routeLengthM))

        val fp = servingCell?.let { fingerprints[it.cellKey] }
        val upperWindow = max(lastProgressM, dr * 1.5 - lastProgressM * 0.5)

        val (estimated, source) = if (fp != null && fp.count >= 1) {
            val clamped = fp.meanProgressM.coerceIn(lastProgressM, upperWindow)
            clamped.coerceIn(lastProgressM, max(lastProgressM, routeLengthM)) to PositionSourceKind.CELL
        } else {
            dr to PositionSourceKind.DEAD_RECKONING
        }

        val lowConfidence = elapsedS > trustLimitS
        val marginMultiplier = if (lowConfidence) 2 else 1
        return FallbackEstimate(
            progressM = estimated,
            source = source,
            lowConfidence = lowConfidence,
            safetyMarginMultiplier = marginMultiplier
        )
    }
}

/**
 * Lightweight accelerometer motion monitor (FR-11) to detect whether the commuter's vehicle
 * has vibration/motion vs dead stop during dead reckoning. Defaults to true if no sensor exists.
 */
class MotionMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var lastMotionMs: Long = System.currentTimeMillis()
    @Volatile
    private var active: Boolean = false

    fun start() {
        val sm = sensorManager ?: return
        val sensor = accelerometer ?: return
        lastMotionMs = System.currentTimeMillis()
        active = true
        runCatching {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        if (!active) return
        active = false
        runCatching { sensorManager?.unregisterListener(this) }
    }

    /** True if linear acceleration exceeded threshold within the last 12 s, or if no sensor exists. */
    fun isMoving(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!active || accelerometer == null) return true
        return (nowMs - lastMotionMs) <= 12_000L
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values.getOrElse(0) { 0f }
        val y = event.values.getOrElse(1) { 0f }
        val z = event.values.getOrElse(2) { 0f }
        val mag = sqrt((x * x + y * y + z * z).toDouble())
        val delta = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            mag
        } else {
            abs(mag - SensorManager.GRAVITY_EARTH)
        }
        if (delta > 0.35) {
            lastMotionMs = System.currentTimeMillis()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
