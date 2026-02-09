package com.mygdx.game.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

class OsmAuthManager(context: Context, private val clientId: String) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("osm_auth", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "OsmAuthManager"
        private const val BASE_URL = "https://www.openstreetmap.org"
        private const val AUTHORIZE_URL = "$BASE_URL/oauth2/authorize"
        private const val TOKEN_URL = "$BASE_URL/oauth2/token"
        private const val API_URL = "https://api.openstreetmap.org"
        private const val REDIRECT_URI = "com.mygdx.game://oauth/callback"

        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_DISPLAY_NAME = "display_name"
        private const val PREF_CODE_VERIFIER = "code_verifier"
    }

    fun isLoggedIn(): Boolean = prefs.getString(PREF_ACCESS_TOKEN, null) != null

    fun getAccessToken(): String? = prefs.getString(PREF_ACCESS_TOKEN, null)

    fun getDisplayName(): String? = prefs.getString(PREF_DISPLAY_NAME, null)

    fun logout() {
        prefs.edit()
            .remove(PREF_ACCESS_TOKEN)
            .remove(PREF_DISPLAY_NAME)
            .remove(PREF_CODE_VERIFIER)
            .apply()
    }

    fun buildAuthUrl(): String {
        val codeVerifier = generateCodeVerifier()
        prefs.edit().putString(PREF_CODE_VERIFIER, codeVerifier).apply()

        val codeChallenge = generateCodeChallenge(codeVerifier)

        return "$AUTHORIZE_URL?" +
                "response_type=code" +
                "&client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
                "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                "&scope=${URLEncoder.encode("write_api", "UTF-8")}" +
                "&code_challenge=${URLEncoder.encode(codeChallenge, "UTF-8")}" +
                "&code_challenge_method=S256"
    }

    suspend fun exchangeCodeForToken(authCode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val codeVerifier = prefs.getString(PREF_CODE_VERIFIER, null)
                    ?: return@withContext false

                val url = URL(TOKEN_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val body = "grant_type=authorization_code" +
                        "&code=${URLEncoder.encode(authCode, "UTF-8")}" +
                        "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                        "&client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
                        "&code_verifier=${URLEncoder.encode(codeVerifier, "UTF-8")}"

                OutputStreamWriter(connection.outputStream).use { it.write(body) }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    val json = JSONObject(response)
                    val accessToken = json.getString("access_token")

                    prefs.edit()
                        .putString(PREF_ACCESS_TOKEN, accessToken)
                        .remove(PREF_CODE_VERIFIER)
                        .apply()

                    // Fetch display name
                    val displayName = fetchDisplayName(accessToken)
                    if (displayName != null) {
                        prefs.edit().putString(PREF_DISPLAY_NAME, displayName).apply()
                    }

                    true
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "Token exchange failed: ${connection.responseCode} - $error")
                    connection.disconnect()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange error", e)
                false
            }
        }
    }

    private fun fetchDisplayName(token: String): String? {
        return try {
            val url = URL("$API_URL/api/0.6/user/details.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(response)
                json.getJSONObject("user").getString("display_name")
            } else {
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch display name", e)
            null
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
