package com.mygdx.game.network

import android.util.Log
import java.net.HttpURLConnection

object NetworkLogger {

    private const val TAG = "NetworkLog"

    fun logRequest(connection: HttpURLConnection, body: String? = null) {
        val method = connection.requestMethod
        val url = connection.url.toString()

        val sb = StringBuilder()
        sb.appendLine("--> $method $url")

        for ((key, values) in connection.requestProperties) {
            val displayValue = if (key.equals("Authorization", ignoreCase = true)) {
                values.joinToString { redactToken(it) }
            } else {
                values.joinToString()
            }
            sb.appendLine("  $key: $displayValue")
        }

        if (body != null) {
            val preview = if (body.length > 500) body.take(500) + "...[truncated]" else body
            sb.appendLine("  Body: $preview")
        }

        Log.d(TAG, sb.toString())
    }

    fun logResponse(connection: HttpURLConnection, startTimeMs: Long, responseBody: String? = null) {
        val durationMs = System.currentTimeMillis() - startTimeMs
        val code = try { connection.responseCode } catch (_: Exception) { -1 }
        val url = connection.url.toString()

        val sb = StringBuilder()
        sb.appendLine("<-- $code $url (${durationMs}ms)")

        if (responseBody != null) {
            val preview = if (responseBody.length > 500) responseBody.take(500) + "...[truncated]" else responseBody
            sb.appendLine("  Body: $preview")
        }

        Log.d(TAG, sb.toString())
    }

    fun logError(url: String, e: Exception) {
        Log.e(TAG, "<-- FAILED $url: ${e.javaClass.simpleName}: ${e.message}")
    }

    private fun redactToken(value: String): String {
        return if (value.startsWith("Bearer ", ignoreCase = true) && value.length > 15) {
            "Bearer ***${value.takeLast(4)}"
        } else {
            value
        }
    }
}
