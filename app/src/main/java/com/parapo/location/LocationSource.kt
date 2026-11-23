package com.parapo.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.parapo.distance.DistanceEngine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** A single position reading from Fused Location Provider, hardware GPS, or GPX replay. */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val speedMps: Double,
    val accuracyM: Float,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lon)
}

interface LocationSource {
    fun updates(): Flow<LocationFix>
    fun updateSampling(profile: SamplingProfile) {}
}

/**
 * FR-06: Production location source backed by FusedLocationProviderClient + direct hardware
 * LocationManager GPS_PROVIDER fallback so offline commutes and emulators always receive fixes.
 * Dynamically adapts update intervals when [AdaptiveSampler] switches tiers.
 */
class FusedLocationSource(
    private val context: Context,
    initialProfile: SamplingProfile = AdaptiveSampler.FAR_SCREEN_ON_PROFILE
) : LocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val profileFlow = MutableStateFlow(initialProfile)
    @Volatile
    private var activeCallback: LocationCallback? = null
    @Volatile
    private var activeGpsListener: LocationListener? = null

    override fun updateSampling(profile: SamplingProfile) {
        if (profileFlow.value.tier == profile.tier) return
        profileFlow.value = profile
        activeCallback?.let { registerFused(profile, it) }
        activeGpsListener?.let { registerGps(profile, it) }
    }

    @SuppressLint("MissingPermission")
    override fun updates(): Flow<LocationFix> = callbackFlow {
        var lastEmittedMs = 0L

        fun emitLocation(loc: Location) {
            val now = System.currentTimeMillis()
            // Deduplicate simultaneous callbacks from Fused + GPS_PROVIDER
            if (now - lastEmittedMs < 400L) return
            lastEmittedMs = now
            trySend(
                LocationFix(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0,
                    accuracyM = if (loc.hasAccuracy()) loc.accuracy else 999f,
                    timestampMs = now
                )
            )
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::emitLocation)
            }
        }
        val gpsListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = emitLocation(location)
            @Deprecated("Required on older APIs")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        activeCallback = callback
        activeGpsListener = gpsListener
        registerFused(profileFlow.value, callback)
        registerGps(profileFlow.value, gpsListener)

        awaitClose {
            activeCallback = null
            activeGpsListener = null
            runCatching { client.removeLocationUpdates(callback) }
            runCatching { locationManager?.removeUpdates(gpsListener) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerFused(profile: SamplingProfile, callback: LocationCallback) {
        val req = LocationRequest.Builder(profile.priority, profile.intervalMs)
            .setMinUpdateIntervalMillis(profile.minUpdateIntervalMs)
            .build()
        runCatching {
            client.removeLocationUpdates(callback)
            client.requestLocationUpdates(req, callback, Looper.getMainLooper())
        }
    }

    companion object {
        /** Shared in-memory cache of the most recent live fix so S3 Home and S4 Trip Setup stay in sync. */
        @Volatile
        var lastFetched: GeoPoint? = null
    }

    fun isLocationEnabled(): Boolean = runCatching {
        locationManager?.isLocationEnabled == true
    }.getOrDefault(true)

    @SuppressLint("MissingPermission")
    private fun registerGps(profile: SamplingProfile, listener: LocationListener) {
        val lm = locationManager ?: return
        runCatching {
            lm.removeUpdates(listener)
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            } ?: return
            lm.requestLocationUpdates(
                provider,
                profile.minUpdateIntervalMs,
                0f,
                listener,
                Looper.getMainLooper()
            )
        }
    }

    /**
     * Actively requests a fresh live fix by racing Fused (Balanced + High Accuracy) and direct
     * LocationManager (GPS + Network) in parallel and taking the first fix that arrives.
     * Works reliably on both GPS phones and Wi-Fi-only tablets (e.g. OPPO Pad SE).
     */
    @SuppressLint("MissingPermission")
    suspend fun currentPoint(timeoutMs: Long = 6_000L): GeoPoint? {
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<GeoPoint?> { cont ->
                val done = java.util.concurrent.atomic.AtomicBoolean(false)
                var fusedBalancedCb: LocationCallback? = null
                var fusedHighCb: LocationCallback? = null
                var osListener: LocationListener? = null
                val cts = CancellationTokenSource()

                fun cleanup() {
                    cts.cancel()
                    fusedBalancedCb?.let { runCatching { client.removeLocationUpdates(it) } }
                    fusedHighCb?.let { runCatching { client.removeLocationUpdates(it) } }
                    osListener?.let { runCatching { locationManager?.removeUpdates(it) } }
                }

                fun deliver(loc: Location?) {
                    if (loc == null) return
                    if (done.compareAndSet(false, true)) {
                        val pt = GeoPoint(loc.latitude, loc.longitude)
                        lastFetched = pt
                        cleanup()
                        if (cont.isActive) cont.resume(pt)
                    }
                }

                cont.invokeOnCancellation {
                    done.set(true)
                    cleanup()
                }

                // 1) Fused getCurrentLocation (balanced = fast Wi-Fi/network lock)
                runCatching {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { deliver(it) }
                }

                // 2) Active 1-shot Fused updates (Balanced + High Accuracy)
                val balCb = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) = deliver(result.lastLocation)
                }.also { fusedBalancedCb = it }
                val highCb = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) = deliver(result.lastLocation)
                }.also { fusedHighCb = it }

                runCatching {
                    val balReq = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1_000L)
                        .setMaxUpdates(1)
                        .build()
                    client.requestLocationUpdates(balReq, balCb, Looper.getMainLooper())
                }
                runCatching {
                    val highReq = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                        .setMaxUpdates(1)
                        .build()
                    client.requestLocationUpdates(highReq, highCb, Looper.getMainLooper())
                }

                // 3) Direct LocationManager listeners (NETWORK + GPS) for tablets where Play Services stalls
                val lm = locationManager
                if (lm != null) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) = deliver(location)
                        @Deprecated("Required on older APIs")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }.also { osListener = it }
                    listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).forEach { p ->
                        runCatching {
                            if (lm.isProviderEnabled(p)) {
                                lm.requestLocationUpdates(p, 500L, 0f, listener, Looper.getMainLooper())
                            }
                        }
                    }
                }
            }
        }
        return fresh ?: lastKnownPoint()
    }

    @SuppressLint("MissingPermission")
    suspend fun lastKnownPoint(): GeoPoint? {
        val fused = suspendCoroutine<GeoPoint?> { cont ->
            runCatching {
                client.lastLocation
                    .addOnSuccessListener { loc ->
                        cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { cont.resume(null) }
            }.onFailure { cont.resume(null) }
        }
        if (fused != null) {
            lastFetched = fused
            return fused
        }
        val cached = runCatching {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers.firstNotNullOfOrNull { p ->
                runCatching { locationManager?.getLastKnownLocation(p) }.getOrNull()
            }?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
        if (cached != null) lastFetched = cached
        return cached ?: lastFetched
    }
}

/**
 * Repeatable GPX / route replay LocationSource for emulator and unit testing (Build Guide 7.1 & 7.8).
 */
class GpxReplayLocationSource(
    private val fixes: List<LocationFix>,
    private val stepDelayMs: Long = 1_000L
) : LocationSource {

    override fun updates(): Flow<LocationFix> = flow {
        for (fix in fixes) {
            delay(stepDelayMs)
            emit(fix.copy(timestampMs = System.currentTimeMillis()))
        }
    }

    companion object {
        /** Builds a synthetic GPX replay stream along a route polyline at [speedMps]. */
        fun fromRoute(
            points: List<GeoPoint>,
            speedMps: Double = 10.0,
            stepSeconds: Int = 1,
            accuracyM: Float = 8f
        ): GpxReplayLocationSource {
            val engine = DistanceEngine(points)
            val out = ArrayList<LocationFix>()
            var progress = 0.0
            val stepM = speedMps * stepSeconds
            while (progress < engine.lengthM) {
                progress = (progress + stepM).coerceAtMost(engine.lengthM)
                val p = engine.pointAt(progress)
                out.add(LocationFix(p.latitude, p.longitude, speedMps, accuracyM))
            }
            return GpxReplayLocationSource(out, stepDelayMs = stepSeconds * 1000L)
        }
    }
}

/** Lightweight GPX trackpoint parser (no Android XML dependencies, works in JVM tests). */
object GpxParser {
    private val trkptRegex = Regex("""<trkpt\s+[^>]*lat="([^"]+)"[^>]*lon="([^"]+)"[^>]*>""")
    private val trkptLonFirstRegex = Regex("""<trkpt\s+[^>]*lon="([^"]+)"[^>]*lat="([^"]+)"[^>]*>""")

    fun parsePoints(gpxXml: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        for (line in gpxXml.lines()) {
            val m1 = trkptRegex.find(line)
            if (m1 != null) {
                val lat = m1.groupValues[1].toDoubleOrNull()
                val lon = m1.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) points.add(GeoPoint(lat, lon))
                continue
            }
            val m2 = trkptLonFirstRegex.find(line)
            if (m2 != null) {
                val lon = m2.groupValues[1].toDoubleOrNull()
                val lat = m2.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) points.add(GeoPoint(lat, lon))
            }
        }
        return points
    }

    fun toFixes(
        points: List<GeoPoint>,
        defaultSpeedMps: Double = 10.0,
        stepSeconds: Int = 1,
        accuracyM: Float = 8f
    ): List<LocationFix> {
        if (points.isEmpty()) return emptyList()
        return points.mapIndexed { idx, pt ->
            val speed = if (idx == 0) {
                defaultSpeedMps
            } else {
                DistanceEngine.haversineM(points[idx - 1], pt) / stepSeconds.coerceAtLeast(1)
            }
            LocationFix(
                lat = pt.latitude,
                lon = pt.longitude,
                speedMps = speed,
                accuracyM = accuracyM
            )
        }
    }
}
