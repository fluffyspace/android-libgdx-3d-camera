package com.mygdx.game.baza

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mygdx.game.notbaza.Building
import com.mygdx.game.notbaza.LatLon

@Entity(tableName = "user_building")
data class UserBuilding(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val osmId: Long? = null,
    val polygonJson: String,
    val heightMeters: Float = 10f,
    val minHeightMeters: Float = 0f,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

fun UserBuilding.toBuilding(): Building {
    val polygon: List<LatLon> = Gson().fromJson(
        polygonJson,
        object : TypeToken<List<LatLon>>() {}.type
    )
    return Building(
        id = osmId ?: -id.toLong(),
        polygon = polygon,
        heightMeters = heightMeters,
        minHeightMeters = minHeightMeters
    )
}

fun Building.toPolygonJson(): String {
    return Gson().toJson(polygon)
}
