package com.parapo.trip

import android.content.Context
import android.os.PowerManager
import com.parapo.cell.AndroidCellTracker
import com.parapo.cell.CellProvider
import com.parapo.cell.FingerprintStore
import com.parapo.distance.DistanceEngine
import com.parapo.fallback.FallbackEngine
import com.parapo.fallback.MotionMonitor
import com.parapo.location.AdaptiveSampler
import com.parapo.location.FusedLocationSource
import com.parapo.location.LocationFix
import com.parapo.location.LocationSource
import com.parapo.location.SamplingTier
import com.parapo.route.RouteRepository
import com.parapo.storage.SettingsStore
import com.parapo.ui.TripSetupResult
import com.parapo.util.Constants
import com.parapo.util.VehicleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/** Trip state machine (build guide 7.6). */
enum class TripPhase { IDLE, ARMED, TRACKING, ALERTING, COMPLETED }

/** Where the current position comes from (S5 status chip). */
enum class PositionSourceKind { GPS, CELL, DEAD_RECKONING }

data class TripUiState(
    val phase: TripPhase = TripPhase.IDLE,
    val setup: TripSetupResult? = null,
    val routeId: String = "",
    val routePoints: List<GeoPoint> = emptyList(),
    val usingStraightLine: Boolean = false,
    val position: GeoPoint? = null,
    val remainingM: Double = 0.0,
    val totalM: Double = 0.0,
    val speedMps: Double = 0.0,
    val etaMin: Int? = null,          // null = hidden (speed below 1 m/s)
    val triggerM: Double = 0.0,
    val source: PositionSourceKind = PositionSourceKind.GPS,
    val usedCellFallback: Boolean = false,
    val lowConfidence: Boolean = false,
    val gpsLostS: Long = 0L,
    val newFingerprints: Int = 0,
    val samplingTier: SamplingTier = SamplingTier.FAR_SCREEN_ON,
    val firedAtRemainingM: Double? = null
)

/**
 * Holds the trip for the whole app, so it survives moving between tabs.
 * Combines LocationSource + AdaptiveSampler + DistanceEngine (Phase 3) with
 * CellTracker + FingerprintStore + FallbackEngine (Phase 4).
 */
object TripController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var motionMonitor: MotionMonitor? = null

    fun start(
        setup: TripSetupResult,
        appContext: Context,
        customSource: LocationSource? = null,
        customCellProvider: CellProvider? = null
    ) {
        stopInternal()
        _state.value = TripUiState(phase = TripPhase.ARMED, setup = setup)
        job = scope.launch {
            val settings = SettingsStore.current(appContext)
            val origin = GeoPoint(setup.originLat, setup.originLon)
            val dest = GeoPoint(setup.destLat, setup.destLon)
            val routeId = FingerprintStore.routeIdFor(
                origin.latitude, origin.longitude, dest.latitude, dest.longitude
            )

            // Route comes from the local cache saved on S4 (works offline)
            val route = RouteRepository(appContext).fetchRoute(origin, dest).getOrNull()
            val straight = route == null
            val pts = route?.points ?: listOf(origin, dest)
            val engine = DistanceEngine(pts, if (straight) settings.roadFactor else 1.0)
            val mode = runCatching { VehicleMode.valueOf(setup.vehicle) }.getOrDefault(VehicleMode.BUS)
            val alertDistM = setup.alertDistanceM.takeIf { it > 0 } ?: settings.alertDistanceM

            val fingerprints = FingerprintStore.load(appContext, routeId)
            val cellProvider = customCellProvider ?: AndroidCellTracker(appContext)
            val monitor = MotionMonitor(appContext).also {
                motionMonitor = it
                it.start()
            }

            val initialRemaining = engine.remainingM(0.0)
            val powerManager = appContext.getSystemService(PowerManager::class.java)
            val initialProfile = AdaptiveSampler.choose(
                remainingM = initialRemaining,
                triggerM = alertDistM.toDouble(),
                isScreenInteractive = powerManager?.isInteractive ?: true
            )
            _state.value = _state.value.copy(
                phase = TripPhase.TRACKING,
                routeId = routeId,
                routePoints = pts,
                usingStraightLine = straight,
                position = origin,
                remainingM = initialRemaining,
                totalM = initialRemaining,
                triggerM = alertDistM.toDouble(),
                samplingTier = initialProfile.tier
            )

            val source = customSource ?: FusedLocationSource(appContext, initialProfile)

            val lock = Any()
            var smoothedSpeed: Double? = null
            var hits = 0
            var lastUiUpdateMs = 0L
            var prevFix: LocationFix? = null
            var lastGoodFixMs = System.currentTimeMillis()
            var lastProgressM = 0.0
            var lastRecordedCellKey: String? = null
            var lastRecordedProgressM = -Constants.FINGERPRINT_STEP_M.toDouble()
            var newFpCount = 0
            var gpsLostTotalS = 0L
            var usedCell = false
            val history = ArrayDeque<Pair<Long, Double>>()

            fun evaluateAndPublish(
                now: Long,
                pos: GeoPoint,
                progress: Double,
                remaining: Double,
                v: Double,
                posKind: PositionSourceKind,
                lowConfidence: Boolean,
                marginMultiplier: Int
            ): Boolean {
                val baseTrigger = DistanceEngine.computeTriggerDistanceM(
                    smoothedSpeedMps = v,
                    mode = mode,
                    alertDistanceM = alertDistM,
                    isEstimatedSource = false,
                    safetyMarginM = settings.safetyMarginM
                )
                val trigger = if (posKind != PositionSourceKind.GPS) {
                    baseTrigger + settings.safetyMarginM * marginMultiplier
                } else {
                    baseTrigger
                }

                val isInteractive = powerManager?.isInteractive ?: true
                val samplingProfile = AdaptiveSampler.choose(remaining, trigger, isInteractive)
                source.updateSampling(samplingProfile)

                history.addLast(now to progress)
                while (history.size > 1 &&
                    now - history.first().first > Constants.MOVING_TOWARD_WINDOW_S * 1000L
                ) history.removeFirst()
                val gained = progress - history.first().second
                val movingToward = DistanceEngine.isMovingToward(gained, v)

                hits = if (remaining <= trigger && movingToward) hits + 1 else 0
                val fire = hits >= Constants.HITS_REQUIRED
                val eta = DistanceEngine.computeEtaMinutes(remaining, v)

                if (fire || now - lastUiUpdateMs >= 950L) {
                    lastUiUpdateMs = now
                    _state.value = _state.value.copy(
                        phase = if (fire) TripPhase.ALERTING else TripPhase.TRACKING,
                        position = pos,
                        remainingM = remaining,
                        speedMps = v,
                        etaMin = eta,
                        triggerM = trigger,
                        source = posKind,
                        usedCellFallback = usedCell,
                        lowConfidence = lowConfidence,
                        gpsLostS = gpsLostTotalS,
                        newFingerprints = newFpCount,
                        samplingTier = samplingProfile.tier,
                        firedAtRemainingM = if (fire) remaining else null
                    )
                }
                return fire
            }

            // Watchdog ticker for GPS-loss detection & fallback estimation (FR-10 / FR-11)
            val fallbackJob = launch {
                while (isActive) {
                    delay(1_000L)
                    val now = System.currentTimeMillis()
                    val lost = synchronized(lock) { FallbackEngine.isGpsLost(now, lastGoodFixMs) }
                    if (!lost) continue

                    val cell = cellProvider.readServingCell()
                    val shouldStop = synchronized(lock) {
                        val elapsedS = (now - lastGoodFixMs) / 1000.0
                        gpsLostTotalS += 1L
                        val est = FallbackEngine.estimateProgress(
                            lastProgressM = lastProgressM,
                            lastKnownSpeedMps = smoothedSpeed ?: 0.0,
                            secondsSinceLastFix = elapsedS,
                            routeLengthM = engine.lengthM,
                            servingCell = cell,
                            fingerprints = fingerprints,
                            trustLimitS = settings.deadReckoningTrustS,
                            isVehicleMoving = monitor.isMoving(now)
                        )
                        if (est.source == PositionSourceKind.CELL) usedCell = true
                        val pos = engine.pointAt(est.progressM)
                        val remaining = engine.remainingM(est.progressM)
                        evaluateAndPublish(
                            now = now,
                            pos = pos,
                            progress = est.progressM,
                            remaining = remaining,
                            v = smoothedSpeed ?: 0.0,
                            posKind = est.source,
                            lowConfidence = est.lowConfidence,
                            marginMultiplier = est.safetyMarginMultiplier
                        )
                    }
                    if (shouldStop) {
                        stopInternal()
                        break
                    }
                }
            }

            try {
                source.updates().collect { fix ->
                    if (!FallbackEngine.isGoodGpsFix(fix.accuracyM)) return@collect
                    val now = fix.timestampMs
                    val pos = fix.toGeoPoint()

                    val progress: Double
                    val shouldStop = synchronized(lock) {
                        lastGoodFixMs = now
                        progress = engine.snap(pos)
                        lastProgressM = progress
                        val remaining = if (straight) {
                            DistanceEngine.straightLineFallbackM(pos, dest, settings.roadFactor)
                        } else {
                            engine.remainingM(progress)
                        }

                        val rawSpeed = if (fix.speedMps > 0.0) {
                            fix.speedMps
                        } else {
                            val prev = prevFix
                            val dtS = if (prev != null) (now - prev.timestampMs) / 1000.0 else 0.0
                            if (prev != null && dtS > 0.1) {
                                DistanceEngine.haversineM(prev.toGeoPoint(), pos) / dtS
                            } else {
                                0.0
                            }
                        }
                        prevFix = fix

                        val v = DistanceEngine.smoothSpeedEma(smoothedSpeed, rawSpeed)
                        smoothedSpeed = v

                        evaluateAndPublish(
                            now = now,
                            pos = pos,
                            progress = progress,
                            remaining = remaining,
                            v = v,
                            posKind = PositionSourceKind.GPS,
                            lowConfidence = false,
                            marginMultiplier = 0
                        )
                    }

                    // R2 Fingerprint learning while GPS accuracy <= 30 m
                    if (fix.accuracyM <= Constants.FINGERPRINT_ACCURACY_M) {
                        val cell = cellProvider.readServingCell()
                        if (cell != null) {
                            var shouldSave = false
                            synchronized(lock) {
                                if (FingerprintStore.shouldRecord(
                                        gpsAccuracyM = fix.accuracyM,
                                        lastCellKey = lastRecordedCellKey,
                                        currentCellKey = cell.cellKey,
                                        lastRecordedProgressM = lastRecordedProgressM,
                                        currentProgressM = progress
                                    )
                                ) {
                                    val isNew = FingerprintStore.upsert(fingerprints, cell, progress)
                                    if (isNew) newFpCount += 1
                                    lastRecordedCellKey = cell.cellKey
                                    lastRecordedProgressM = progress
                                    shouldSave = true
                                }
                            }
                            if (shouldSave) {
                                FingerprintStore.save(appContext, routeId, fingerprints)
                            }
                        }
                    }

                    if (shouldStop) {
                        stopInternal()
                    }
                }
            } finally {
                fallbackJob.cancel()
                monitor.stop()
            }
        }
    }

    /** User tapped "GISING NA AKO". */
    fun dismissAlert() {
        if (_state.value.phase == TripPhase.ALERTING) {
            _state.value = _state.value.copy(phase = TripPhase.COMPLETED)
        }
    }

    /** Cancel Trip, or finishing after the summary. */
    fun reset() {
        stopInternal()
        _state.value = TripUiState()
    }

    private fun stopInternal() {
        job?.cancel()
        motionMonitor?.stop()
        motionMonitor = null
    }
}
