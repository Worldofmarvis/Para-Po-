package com.parapo.cell

import android.content.Context
import com.parapo.storage.Fingerprint
import com.parapo.util.Constants
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FR-12 (R2): Persists and updates learned cell-tower fingerprints per route
 * in filesDir/fingerprints/<routeId>.json (Build Guide 7.4 & 7.5).
 */
object FingerprintStore {

    private const val DIR_NAME = "fingerprints"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Builds a stable route ID rounded to ~3 decimals (~110 m cluster) so repeat rides
     * on the same commute reuse the same learned cell-tower fingerprints (Build Guide 7.4).
     */
    fun routeIdFor(originLat: Double, originLon: Double, destLat: Double, destLon: Double): String =
        String.format(
            Locale.US,
            "r_%.3f_%.3f__%.3f_%.3f",
            originLat, originLon, destLat, destLon
        ).replace('.', 'p').replace('-', 'm')

    private fun fileFor(context: Context, routeId: String): File {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        return File(dir, "$routeId.json")
    }

    fun load(context: Context, routeId: String): MutableMap<String, Fingerprint> {
        val f = fileFor(context, routeId)
        if (!f.exists()) return linkedMapOf()
        val list = runCatching { json.decodeFromString<List<Fingerprint>>(f.readText()) }
            .getOrDefault(emptyList())
        return list.associateByTo(linkedMapOf()) { it.cellKey }
    }

    fun save(context: Context, routeId: String, fingerprints: Map<String, Fingerprint>) {
        runCatching {
            fileFor(context, routeId).writeText(json.encodeToString(fingerprints.values.toList()))
        }
    }

    /**
     * Build Guide 7.5: record on serving-cell change or every 200 m of progress while GPS accuracy <= 30 m.
     */
    fun shouldRecord(
        gpsAccuracyM: Float,
        lastCellKey: String?,
        currentCellKey: String,
        lastRecordedProgressM: Double,
        currentProgressM: Double
    ): Boolean {
        if (gpsAccuracyM > Constants.FINGERPRINT_ACCURACY_M) return false
        if (lastCellKey == null || lastCellKey != currentCellKey) return true
        return abs(currentProgressM - lastRecordedProgressM) >= Constants.FINGERPRINT_STEP_M
    }

    /**
     * Upserts a serving cell observation into [fingerprints], maintaining running mean/min/max/rssi/count.
     * Returns true if this was a newly learned cell key on this route.
     */
    fun upsert(
        fingerprints: MutableMap<String, Fingerprint>,
        cell: ServingCell,
        progressM: Double
    ): Boolean {
        val existing = fingerprints[cell.cellKey]
        return if (existing == null) {
            fingerprints[cell.cellKey] = Fingerprint(
                cellKey = cell.cellKey,
                tech = cell.tech,
                areaCode = cell.areaCode,
                meanProgressM = progressM,
                minM = progressM,
                maxM = progressM,
                rssiAvg = cell.rssi,
                count = 1
            )
            true
        } else {
            val newCount = existing.count + 1
            val newMean = (existing.meanProgressM * existing.count + progressM) / newCount
            val newRssi = ((existing.rssiAvg * existing.count + cell.rssi).toDouble() / newCount).roundToInt()
            fingerprints[cell.cellKey] = existing.copy(
                meanProgressM = newMean,
                minM = min(existing.minM, progressM),
                maxM = max(existing.maxM, progressM),
                rssiAvg = newRssi,
                count = newCount
            )
            false
        }
    }
}
