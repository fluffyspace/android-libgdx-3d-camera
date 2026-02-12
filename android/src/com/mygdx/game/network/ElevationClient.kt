package com.mygdx.game.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ElevationClient {

    private const val BASE_URL = "https://api.open-meteo.com/v1/elevation"

    /**
     * Fetch ground elevation (meters above sea level) for given coordinates.
     * Uses Open-Meteo free elevation API (no key needed).
     */
    suspend fun fetchElevation(lat: Double, lon: Double): Float {
        return withContext(Dispatchers.IO) {
            val url = URL("$BASE_URL?latitude=$lat&longitude=$lon")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            NetworkLogger.logRequest(connection)
            val startTime = System.currentTimeMillis()
            try {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, response)
                val parsed = Gson().fromJson(response, ElevationResponse::class.java)
                parsed.elevation.firstOrNull()
                    ?: throw IllegalStateException("No elevation data in response")
            } catch (e: Exception) {
                NetworkLogger.logError(url.toString(), e)
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    private data class ElevationResponse(
        val elevation: List<Float> = emptyList()
    )
}
