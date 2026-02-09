package com.mygdx.game.baza

import android.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Objekt(
    @PrimaryKey(autoGenerate = true) var id:Int,
    var x: Float,
    var y: Float,
    var z: Float,
    var name: String = "default_name",
    var size: Float = 1f,
    var rotationX: Float = 0f,
    var rotationY: Float = 0f,
    var rotationZ: Float = 0f,
    var color: Int = Color.WHITE,
    var osmId: Long? = null,
    var polygonJson: String? = null,
    var heightMeters: Float = 10f,
    var minHeightMeters: Float = 0f,
)
