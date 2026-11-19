package com.parapo.trip

import android.content.Context
import com.parapo.distance.DistanceEngine
import com.parapo.route.RouteRepository
import com.parapo.ui.TripSetupResult
import com.parapo.util.Constants
import com.parapo.util.VehicleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import kotlin.math.ceil
import kotlin.math.max

/** Trip state machine (build guide 7.6). */
enum class TripPhase { IDLE, ARMED, TRACKING, ALERTING, COMPLETED }

/** Where the current position comes from (S5 status chip). */
enum class PositionSourceKind { GPS, CELL, DEAD_RECKONING }

data class TripUiState(
    val phase: TripPhase = TripPhase.IDLE,
    val setup: TripSetupResult? = null,
    val routePoints: List<GeoPoint> = emptyList(),
    val usingStraightLine: Boolean = false,
    val position: GeoPoint? = null,
    val remainingM: Double = 0.0,
    val totalM: Double = 0.0,
    val speedMps: Double = 0.0,
    val etaMin: Int? = null,          // null = hidden (speed below 1 m/s)
    val triggerM: Double = 0.0,
    val source: PositionSourceKind = PositionSourceKind.GPS,
    val firedAtRemainingM: Double? = null
)

/**
 * Holds the trip for the whole app, so it survives moving between tabs.
 * Later this moves behind the foreground service; the screens only read [state].
 *
 * DEMO DRIVE: until LocationSource (real GPS) is built, the position is simulated
 * by moving along the route at [SIM_SPEED_MPS]. Everything after the position
 * (snap, remaining, speed, trigger, alert decision) is the real logic from 7.5.
 */
object TripController {
    const val SIM_SPEED_MPS = 20.0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(setup: TripSetupResult, appContext: Context) {
        job?.cancel()
        _state.value = TripUiState(phase = TripPhase.ARMED, setup = setup)
        job = scope.launch {
            val origin = GeoPoint(setup.originLat, setup.originLon)
            val dest = GeoPoint(setup.destLat, setup.destLon)

            // Route comes from the local cache saved on S4 (works offline)
            val route = RouteRepository(appContext).fetchRoute(origin, dest).getOrNull()
            val straight = route == null
            val pts = route?.points ?: listOf(origin, dest)
            val engine = DistanceEngine(pts, if (straight) Constants.ROAD_FACTOR else 1.0)
            val lead = Constants.leadTimeSeconds(VehicleMode.valueOf(setup.vehicle))

            _state.value = _state.value.copy(
                phase = TripPhase.TRACKING,
                routePoints = pts,
                usingStraightLine = straight,
                position = origin,
                remainingM = engine.remainingM(0.0),
                totalM = engine.remainingM(0.0)
            )

            var simProgress = 0.0
            var v = -1.0
            var hits = 0
            val history = ArrayDeque<Pair<Long, Double>>()

            while (isActive) {
                delay(1000) // UI updates at most once per second
                val before = simProgress
                simProgress = (simProgress + SIM_SPEED_MPS).coerceAtMost(engine.lengthM)
                val speed = if (simProgress > before) SIM_SPEED_MPS else 0.0

                val pos = engine.pointAt(simProgress)
                val progress = engine.snap(pos)
                val remaining = engine.remainingM(progress)

                // smoothed speed (EMA)
                v = if (v < 0) speed else Constants.SPEED_EMA_ALPHA * speed + (1 - Constants.SPEED_EMA_ALPHA) * v

                val source = PositionSourceKind.GPS
                var trigger = (v * lead).coerceIn(
                    Constants.MIN_TRIGGER_M.toDouble(),
                    max(Constants.MIN_TRIGGER_M, setup.alertDistanceM).toDouble()
                )
                if (source != PositionSourceKind.GPS) trigger += Constants.DEFAULT_SAFETY_MARGIN_M

                val now = System.currentTimeMillis()
                history.addLast(now to progress)
                while (history.size > 1 &&
                    now - history.first().first > Constants.MOVING_TOWARD_WINDOW_S * 1000L
                ) history.removeFirst()
                val gained = progress - history.first().second
                val movingToward = speed > Constants.MOVING_TOWARD_MIN_SPEED_MPS ||
                    gained >= Constants.MOVING_TOWARD_MIN_PROGRESS_M

                hits = if (remaining <= trigger && movingToward) hits + 1 else 0
                val fire = hits >= Constants.HITS_REQUIRED

                val eta = if (v >= Constants.MOVING_TOWARD_MIN_SPEED_MPS)
                    max(1, ceil(remaining / v / 60.0).toInt()) else null

                _state.value = _state.value.copy(
                    phase = if (fire) TripPhase.ALERTING else TripPhase.TRACKING,
                    position = pos,
                    remainingM = remaining,
                    speedMps = v,
                    etaMin = eta,
                    triggerM = trigger,
                    source = source,
                    firedAtRemainingM = if (fire) remaining else null
                )
                if (fire) break
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
        job?.cancel()
        _state.value = TripUiState()
    }
}
