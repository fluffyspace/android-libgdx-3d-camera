package com.mygdx.game.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mygdx.game.R
import com.mygdx.game.notbaza.Building
import com.mygdx.game.notbaza.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.sqrt

object OverpassClient {

    var overpassUrl = "https://overpass-api.de/api/interpreter"

    // TODO: remove mock — set to null to use real Overpass API
    private var appContext: Context? = null

    fun initMock(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun fetchBuildings(lat: Double, lon: Double, radiusMeters: Int = 300): List<Building> {
        return withContext(Dispatchers.IO) {
            try {
                val response = appContext?.let { ctx ->
                    android.util.Log.d("BuildingFetch", "Using mock data from raw resource")
                    ctx.resources.openRawResource(R.raw.mock_overpass)
                        .bufferedReader().use { it.readText() }
                } ?: run {
                    val query = "[out:json][timeout:25];way[\"building\"](around:$radiusMeters,$lat,$lon);out body geom;"
                    val url = URL("$overpassUrl?data=${java.net.URLEncoder.encode(query, "UTF-8")}")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000

                    NetworkLogger.logRequest(connection)
                    val startTime = System.currentTimeMillis()
                    val resp = connection.inputStream.bufferedReader().use { it.readText() }
                    NetworkLogger.logResponse(connection, startTime, resp)
                    connection.disconnect()
                    resp
                }

                val overpassResponse = Gson().fromJson(response, OverpassResponse::class.java)
                android.util.Log.d("BuildingFetch", "Parsed ${overpassResponse.elements.size} elements")
                overpassResponse.elements.mapNotNull { element -> parseBuilding(element) }
            } catch (e: Exception) {
                android.util.Log.e("BuildingFetch", "Exception in fetchBuildings", e)
                NetworkLogger.logError(overpassUrl, e)
                emptyList()
            }
        }
    }

    private fun parseBuilding(element: OverpassElement): Building? {
        val geometry = element.geometry ?: return null
        if (geometry.size < 3) return null

        val polygon = geometry.map { node -> LatLon(node.lat, node.lon) }

        val height = parseHeight(element.tags)

        return Building(
            id = element.id,
            polygon = polygon,
            heightMeters = height,
            minHeightMeters = parseMinHeight(element.tags)
        )
    }

    private fun parseHeight(tags: Map<String, String>?): Float {
        if (tags == null) return 10f

        tags["height"]?.toFloatOrNull()?.let { return it }
        tags["building:height"]?.toFloatOrNull()?.let { return it }
        tags["building:levels"]?.toFloatOrNull()?.let { return it * 3f }

        return 10f
    }

    private fun parseMinHeight(tags: Map<String, String>?): Float {
        if (tags == null) return 0f
        tags["min_height"]?.toFloatOrNull()?.let { return it }
        tags["building:min_height"]?.toFloatOrNull()?.let { return it }
        tags["building:min_level"]?.toFloatOrNull()?.let { return it * 3f }
        return 0f
    }

    suspend fun fetchBuildingAtPoint(lat: Double, lon: Double): Building? {
        val buildings = fetchBuildings(lat, lon, radiusMeters = 50)
        return buildings.firstOrNull { pointInPolygon(lat, lon, it.polygon) }
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val avgLat = Math.toRadians((lat1 + lat2) / 2.0)
        val x = dLon * cos(avgLat)
        return sqrt(x * x + dLat * dLat) * 6_371_000.0
    }

    fun buildingCentroid(polygon: List<LatLon>): LatLon {
        val avgLat = polygon.map { it.lat }.average()
        val avgLon = polygon.map { it.lon }.average()
        return LatLon(avgLat, avgLon)
    }

    suspend fun fetchBuildingsNearPoint(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 50
    ): List<Pair<Building, Double>> {
        val buildings = fetchBuildings(lat, lon, radiusMeters)
        return buildings.map { building ->
            val centroid = buildingCentroid(building.polygon)
            val dist = distanceMeters(lat, lon, centroid.lat, centroid.lon)
            building to dist
        }.sortedBy { it.second }
    }

    fun pointInPolygon(lat: Double, lon: Double, polygon: List<LatLon>): Boolean {
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val yi = polygon[i].lat
            val xi = polygon[i].lon
            val yj = polygon[j].lat
            val xj = polygon[j].lon
            if (((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private data class OverpassResponse(
        val elements: List<OverpassElement> = emptyList()
    )

    private data class OverpassElement(
        val id: Long = 0,
        val type: String = "",
        val tags: Map<String, String>? = null,
        val geometry: List<OverpassNode>? = null
    )

    private data class OverpassNode(
        val lat: Double = 0.0,
        val lon: Double = 0.0
    )
}
