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

                if (BuildConfig.ORS_API_KEY.isBlank()) throw IOException("No ORS key")

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
                val text = request(
                    "https://api.openrouteservice.org/v2/directions/driving-car/geojson",
                    "POST", body, BuildConfig.ORS_API_KEY
                )
                Log.d(TAG, "ORS OK, ${text.length} chars")
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
                val url = "https://api.maptiler.com/geocoding/$q.json" +
                    "?key=${BuildConfig.MAPTILER_KEY}&limit=6&country=ph" +
                    "&proximity=${String.format(Locale.US, "%.5f,%.5f", nearLon, nearLat)}"
                val features = JSONObject(request(url, "GET", null, null)).getJSONArray("features")
                val out = ArrayList<PlaceResult>()
                for (i in 0 until features.length()) {
                    val f = features.getJSONObject(i)
                    val center = f.optJSONArray("center") ?: continue
                    val name = f.optString("place_name").ifBlank { f.optString("text") }
                    if (name.isBlank()) continue
                    out.add(PlaceResult(name, GeoPoint(center.getDouble(1), center.getDouble(0))))
                }
                out
            }
        }

    // ----------------------------------------------------------------- http

    /** Pulls the human-readable message out of an ORS / MapTiler error body. */
    private fun errorMessage(body: String): String = try {
        val o = JSONObject(body)
        val e = o.opt("error")
        when (e) {
            is JSONObject -> e.optString("message").ifBlank { e.toString() }
            is String -> e
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
