package com.parapo.util

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns coordinates into a readable place name (e.g. "Poblacion, Nasugbu").
 * Falls back to the coordinates themselves when offline or nothing is found,
 * so a trip is never saved without a label.
 */
object PlaceNamer {
    private val coordRegex = Regex("""^\s*(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)\s*$""")
    private val cache = ConcurrentHashMap<String, String>()

    /** Returns lat/lon if [text] is just a "13.8320, 121.1370" style label, else null. */
    fun parseCoords(text: String): Pair<Double, Double>? {
        val m = coordRegex.matchEntire(text) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        return lat to lon
    }

    @Suppress("DEPRECATION")
    suspend fun nameFor(ctx: Context, lat: Double, lon: Double): String? {
        val key = String.format(Locale.US, "%.4f,%.4f", lat, lon)
        cache[key]?.let { return it }
        return withTimeoutOrNull(4000L) {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!Geocoder.isPresent()) return@runCatching null
                    val a = Geocoder(ctx.applicationContext, Locale.getDefault())
                        .getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@runCatching null
                    val parts = listOfNotNull(
                        a.featureName?.takeIf { n -> n.any { it.isLetter() } },
                        a.thoroughfare,
                        a.subLocality,
                        a.locality ?: a.subAdminArea
                    ).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
                    parts.joinToString(", ").takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        }?.also { cache[key] = it }
    }

    /** If [label] is only coordinates, try to replace it with a place name; otherwise return it unchanged. */
    suspend fun resolveLabel(ctx: Context, label: String): String {
        val (lat, lon) = parseCoords(label) ?: return label
        return nameFor(ctx, lat, lon) ?: label
    }
}
