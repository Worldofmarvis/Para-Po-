package com.parapo.route

import android.content.Context
import android.util.Log
import com.parapo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class PlaceResult(val name: String, val point: GeoPoint)

data class RouteResult(val points: List<GeoPoint>, val distanceM: Double)

/**
 * FR-03: fetch the route polyline from OpenRouteService, verify it is complete, cache it locally.
 * Also does place search (MapTiler geocoding) for the S4 search bar.
 */
class RouteRepository(context: Context) {

    private companion object { const val TAG = "ParaPoRoute" }

    private val cacheDir = File(context.filesDir, "routes").apply { mkdirs() }

    private fun hasRealKey(key: String): Boolean {
        val k = key.trim()
        return k.isNotBlank() && k != "dummy_key" && !k.startsWith("your_")
    }

    // ---------------------------------------------------------------- route

    suspend fun fetchRoute(origin: GeoPoint, dest: GeoPoint): Result<RouteResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = String.format(
                    Locale.US, "%.4f_%.4f_%.4f_%.4f",
                    origin.latitude, origin.longitude, dest.latitude, dest.longitude
                )
                val file = File(cacheDir, "$key.json")

                // Cache hit: works offline
                if (file.exists()) {
                    runCatching { return@runCatching parseRoute(file.readText()) }
                        .getOrNull()?.let { return@runCatching it }
                }

                val text = if (hasRealKey(BuildConfig.ORS_API_KEY)) {
                    val body = JSONObject().apply {
                        put(
                            "coordinates",
                            JSONArray()
                                .put(JSONArray().put(origin.longitude).put(origin.latitude))
                                .put(JSONArray().put(dest.longitude).put(dest.latitude))
                        )
                        // let ORS snap a tapped point to the nearest road, however far
                        put("radiuses", JSONArray().put(-1).put(-1))
                    }.toString()

                    Log.d(TAG, "ORS request body: $body")
                    request(
                        "https://api.openrouteservice.org/v2/directions/driving-car/geojson",
                        "POST", body, BuildConfig.ORS_API_KEY
                    )
                } else {
                    // Keyless dev fallback: OSRM public driving route wrapped into the same GeoJSON format
                    val osrmUrl = String.format(
                        Locale.US,
                        "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                        origin.longitude, origin.latitude, dest.longitude, dest.latitude
                    )
                    val osrmJson = JSONObject(request(osrmUrl, "GET", null, null))
                    val firstRoute = osrmJson.getJSONArray("routes").getJSONObject(0)
                    val feature = JSONObject()
                        .put("geometry", firstRoute.getJSONObject("geometry"))
                        .put("properties", JSONObject().put("summary", JSONObject().put("distance", firstRoute.getDouble("distance"))))
                    JSONObject().put("features", JSONArray().put(feature)).toString()
                }
                Log.d(TAG, "Route OK, ${text.length} chars")
                val result = parseRoute(text) // throws if incomplete
                file.writeText(text)
                result
            }.onFailure { Log.e(TAG, "fetchRoute failed: ${it.javaClass.simpleName}: ${it.message}", it) }
        }

    private fun parseRoute(text: String): RouteResult {
        try {
            val feature = JSONObject(text).getJSONArray("features").getJSONObject(0)
            val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val dist = feature.getJSONObject("properties").getJSONObject("summary").getDouble("distance")
            if (coords.length() < 2) throw IOException("Incomplete route")
            val pts = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                pts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
            }
            return RouteResult(pts, dist)
        } catch (e: JSONException) {
            throw IOException("Unreadable route data (${e.message}) start=${text.take(80)}".take(160))
        }
    }

    // --------------------------------------------------------------- search

    suspend fun search(query: String, nearLat: Double, nearLon: Double): Result<List<PlaceResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
                val out = ArrayList<PlaceResult>()
                if (hasRealKey(BuildConfig.MAPTILER_KEY)) {
                    val url = "https://api.maptiler.com/geocoding/$q.json" +
                        "?key=${BuildConfig.MAPTILER_KEY}&limit=6&country=ph" +
                        "&proximity=${String.format(Locale.US, "%.5f,%.5f", nearLon, nearLat)}"
                    val features = JSONObject(request(url, "GET", null, null)).getJSONArray("features")
                    for (i in 0 until features.length()) {
                        val f = features.getJSONObject(i)
                        val center = f.optJSONArray("center") ?: continue
                        val name = f.optString("place_name").ifBlank { f.optString("text") }
                        if (name.isBlank()) continue
                        out.add(PlaceResult(name, GeoPoint(center.getDouble(1), center.getDouble(0))))
                    }
                } else {
                    // Keyless dev fallback: OpenStreetMap Nominatim (Philippines)
                    val url = "https://nominatim.openstreetmap.org/search?format=json&countrycodes=ph&limit=6&q=$q"
                    val arr = JSONArray(request(url, "GET", null, null))
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val name = item.optString("display_name")
                        val lat = item.optString("lat").toDoubleOrNull()
                        val lon = item.optString("lon").toDoubleOrNull()
                        if (name.isNotBlank() && lat != null && lon != null) {
                            out.add(PlaceResult(name, GeoPoint(lat, lon)))
                        }
                    }
                }
                out
            }
        }

    /** Looks up a readable place/street name for a tapped map point (online), or null if offline. */
    suspend fun reverseGeocode(point: GeoPoint): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (hasRealKey(BuildConfig.MAPTILER_KEY)) {
                    val coords = String.format(Locale.US, "%.6f,%.6f", point.longitude, point.latitude)
                    val url = "https://api.maptiler.com/geocoding/$coords.json?key=${BuildConfig.MAPTILER_KEY}&limit=1"
                    val features = JSONObject(request(url, "GET", null, null)).getJSONArray("features")
                    if (features.length() > 0) {
                        val f = features.getJSONObject(0)
                        f.optString("place_name").ifBlank { f.optString("text") }.ifBlank { null }
                    } else null
                } else {
                    val url = String.format(
                        Locale.US,
                        "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f",
                        point.latitude, point.longitude
                    )
                    JSONObject(request(url, "GET", null, null)).optString("display_name").ifBlank { null }
                }
            }.getOrNull()
        }

    // ----------------------------------------------------------------- http

    /** Pulls the human-readable message out of an ORS / MapTiler error body. */
    private fun errorMessage(body: String): String = try {
        val o = JSONObject(body)
        val e: Any? = o.opt("error")
        when (e) {
            is JSONObject -> e.optString("message").ifBlank { e.toString() }
            is String -> e
            null -> o.optString("message")
            else -> o.optString("message")
        }
    } catch (ex: Exception) {
        body.take(100)
    }

    private fun request(url: String, method: String, body: String?, auth: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/geo+json, application/json")
            c.setRequestProperty("User-Agent", "ParaPoApp/0.1.0 (CS312 BatStateU)")
            if (auth != null) c.setRequestProperty("Authorization", auth)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.e(TAG, "HTTP $code from ${url.substringBefore('?')}: ${text.take(300)}")
                throw IOException("HTTP $code ${errorMessage(text)}".trim().take(140))
            }
            return text
        } finally {
            c.disconnect()
        }
    }
}
