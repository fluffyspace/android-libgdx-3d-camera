package com.mygdx.game.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.OutputStreamWriter
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

object OsmApiClient {

    private const val TAG = "OsmApiClient"
    private const val API_URL = "https://api.openstreetmap.org/api/0.6"

    data class OsmWay(
        val id: Long,
        val version: Int,
        val nodes: List<Long>,
        val tags: Map<String, String>
    )

    suspend fun createChangeset(token: String, comment: String): Long {
        return withContext(Dispatchers.IO) {
            val xml = """<osm>
  <changeset>
    <tag k="comment" v="${escapeXml(comment)}"/>
    <tag k="created_by" v="AR Building Editor"/>
    <tag k="source" v="survey"/>
  </changeset>
</osm>"""

            val url = URL("$API_URL/changeset/create")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/xml")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            NetworkLogger.logRequest(connection, xml)
            val startTime = System.currentTimeMillis()
            OutputStreamWriter(connection.outputStream).use { it.write(xml) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, response)
                connection.disconnect()
                response.trim().toLong()
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, error)
                connection.disconnect()
                throw OsmApiException("Failed to create changeset: ${connection.responseCode} - $error")
            }
        }
    }

    suspend fun fetchWay(wayId: Long): OsmWay {
        return withContext(Dispatchers.IO) {
            val url = URL("$API_URL/way/$wayId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            NetworkLogger.logRequest(connection)
            val startTime = System.currentTimeMillis()
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, response)
                connection.disconnect()
                parseWayXml(response)
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, error)
                connection.disconnect()
                throw OsmApiException("Failed to fetch way $wayId: ${connection.responseCode} - $error")
            }
        }
    }

    suspend fun updateWayTags(
        token: String,
        changesetId: Long,
        way: OsmWay,
        newTags: Map<String, String>
    ): Int {
        return withContext(Dispatchers.IO) {
            val mergedTags = way.tags.toMutableMap()
            mergedTags.putAll(newTags)

            val nodesXml = way.nodes.joinToString("\n    ") { """<nd ref="$it"/>""" }
            val tagsXml = mergedTags.entries.joinToString("\n    ") {
                """<tag k="${escapeXml(it.key)}" v="${escapeXml(it.value)}"/>"""
            }

            val xml = """<osm>
  <way id="${way.id}" changeset="$changesetId" version="${way.version}">
    $nodesXml
    $tagsXml
  </way>
</osm>"""

            val url = URL("$API_URL/way/${way.id}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/xml")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            NetworkLogger.logRequest(connection, xml)
            val startTime = System.currentTimeMillis()
            OutputStreamWriter(connection.outputStream).use { it.write(xml) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, response)
                connection.disconnect()
                response.trim().toInt() // returns new version number
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, error)
                connection.disconnect()
                throw OsmApiException("Failed to update way ${way.id}: ${connection.responseCode} - $error")
            }
        }
    }

    suspend fun closeChangeset(token: String, changesetId: Long) {
        withContext(Dispatchers.IO) {
            val url = URL("$API_URL/changeset/$changesetId/close")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            NetworkLogger.logRequest(connection)
            val startTime = System.currentTimeMillis()
            if (connection.responseCode != 200) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                NetworkLogger.logResponse(connection, startTime, error)
                connection.disconnect()
                Log.e(TAG, "Failed to close changeset $changesetId: ${connection.responseCode} - $error")
            } else {
                NetworkLogger.logResponse(connection, startTime)
                connection.disconnect()
            }
        }
    }

    private fun parseWayXml(xml: String): OsmWay {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var wayId = 0L
        var version = 0
        val nodes = mutableListOf<Long>()
        val tags = mutableMapOf<String, String>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "way" -> {
                        wayId = parser.getAttributeValue(null, "id")?.toLongOrNull() ?: 0L
                        version = parser.getAttributeValue(null, "version")?.toIntOrNull() ?: 0
                    }
                    "nd" -> {
                        parser.getAttributeValue(null, "ref")?.toLongOrNull()?.let { nodes.add(it) }
                    }
                    "tag" -> {
                        val k = parser.getAttributeValue(null, "k")
                        val v = parser.getAttributeValue(null, "v")
                        if (k != null && v != null) tags[k] = v
                    }
                }
            }
            eventType = parser.next()
        }

        return OsmWay(wayId, version, nodes, tags)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    class OsmApiException(message: String) : Exception(message)
}
