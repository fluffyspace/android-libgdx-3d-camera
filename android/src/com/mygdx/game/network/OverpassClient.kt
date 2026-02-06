package com.mygdx.game.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mygdx.game.notbaza.Building
import com.mygdx.game.notbaza.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object OverpassClient {

    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"

    suspend fun fetchBuildings(lat: Double, lon: Double, radiusMeters: Int = 300): List<Building> {
        return withContext(Dispatchers.IO) {
            try {
                val query = "[out:json][timeout:25];way[\"building\"](around:$radiusMeters,$lat,$lon);out body geom;"
                val url = URL("$OVERPASS_URL?data=${java.net.URLEncoder.encode(query, "UTF-8")}")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val overpassResponse = Gson().fromJson(response, OverpassResponse::class.java)
                overpassResponse.elements.mapNotNull { element -> parseBuilding(element) }
            } catch (e: Exception) {
                e.printStackTrace()
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
