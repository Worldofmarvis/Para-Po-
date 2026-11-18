package com.parapo.storage

import com.parapo.util.Constants
import com.parapo.util.VehicleMode
import kotlinx.serialization.Serializable

// Data models for local JSON storage (build guide 7.4)

@Serializable
data class AuthData(
    val username: String,
    val salt: String,          // base64, 16 bytes
    val hash: String,          // PBKDF2-HMAC-SHA256
    val iterations: Int = Constants.PBKDF2_ITERATIONS
)

@Serializable
data class Settings(
    val alertDistanceM: Int = Constants.DEFAULT_ALERT_DISTANCE_M,
    val safetyMarginM: Int = Constants.DEFAULT_SAFETY_MARGIN_M,
    val soundAfterS: Int = Constants.SOUND_AFTER_S,
    val fullScreenAfterS: Int = Constants.FULLSCREEN_AFTER_S,
    val soundEnabled: Boolean = true,
    val fullScreenEnabled: Boolean = true,
    val roadFactor: Double = Constants.ROAD_FACTOR,
    val deadReckoningTrustS: Int = Constants.DEAD_RECKONING_TRUST_S,
    val smsEnabled: Boolean = false,
    val smsNumber: String? = null
)

@Serializable
data class LatLon(val lat: Double, val lon: Double, val name: String? = null)

@Serializable
data class Route(
    val id: String,
    val mode: VehicleMode,
    val fetchedAt: Long,
    val origin: LatLon,
    val dest: LatLon,
    val polyline: List<List<Double>>,   // [[lat, lon], ...]
    val cumulativeM: List<Double>,      // meters from route start, same length as polyline
    val lengthM: Double
)

@Serializable
data class Fingerprint(
    val cellKey: String,       // "MCC-MNC-areaCode-cellId-TECH"
    val tech: String,
    val areaCode: Int,
    val meanProgressM: Double,
    val minM: Double,
    val maxM: Double,
    val rssiAvg: Int,
    val count: Int
)

@Serializable
data class TripLog(
    val id: String,
    val routeId: String,
    val startedAt: Long,
    val endedAt: Long,
    val mode: VehicleMode,
    val triggerM: Int,
    val alertFiredAtRemainingM: Int,
    val source: String,        // GPS, GPS+CELL, DEAD_RECKONING
    val gpsLostS: Int,
    val newFingerprints: Int,
    val result: String         // ON_TIME or LATE
)
