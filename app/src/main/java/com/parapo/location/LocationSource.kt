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
import com.parapo.distance.DistanceEngine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
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

    @SuppressLint("MissingPermission")
    private fun registerGps(profile: SamplingProfile, listener: LocationListener) {
        val lm = locationManager ?: return
        runCatching {
            lm.removeUpdates(listener)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    profile.minUpdateIntervalMs,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }
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
        if (fused != null) return fused
        return runCatching {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
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
