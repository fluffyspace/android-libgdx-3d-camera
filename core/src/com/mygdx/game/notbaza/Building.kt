package com.mygdx.game.notbaza

data class LatLon(val lat: Double, val lon: Double)

data class Building(
    val id: Long,
    val polygon: List<LatLon>,
    val heightMeters: Float,
    val minHeightMeters: Float = 0f
)
