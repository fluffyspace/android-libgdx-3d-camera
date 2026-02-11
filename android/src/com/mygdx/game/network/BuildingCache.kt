package com.mygdx.game.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mygdx.game.notbaza.Building

class BuildingCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("building_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val GRID_SIZE = 0.003 // ~300m grid
    }

    fun getCached(lat: Double, lon: Double): List<Building>? {
        val key = gridKey(lat, lon)
        val json = prefs.getString(key, null) ?: return null

        return try {
            val entry = gson.fromJson(json, CacheEntry::class.java)
            if (System.currentTimeMillis() - entry.timestampMs > TTL_MS) {
                prefs.edit().remove(key).apply()
                null
            } else {
                entry.buildings
            }
        } catch (e: Exception) {
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun putCache(lat: Double, lon: Double, buildings: List<Building>) {
        if (buildings.isEmpty()) return
        val key = gridKey(lat, lon)
        val entry = CacheEntry(buildings, System.currentTimeMillis())
        prefs.edit().putString(key, gson.toJson(entry)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun gridKey(lat: Double, lon: Double): String {
        val gridLat = "%.3f".format(Math.floor(lat / GRID_SIZE) * GRID_SIZE)
        val gridLon = "%.3f".format(Math.floor(lon / GRID_SIZE) * GRID_SIZE)
        return "buildings_${gridLat}_${gridLon}"
    }

    private data class CacheEntry(
        val buildings: List<Building>,
        val timestampMs: Long
    )
}
