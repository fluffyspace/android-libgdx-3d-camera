package com.mygdx.game

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Objekt(
    @PrimaryKey(autoGenerate = true) var id:Int,
    var x: Float,
    var y: Float,
    var z: Float,
    var name: String,
    var size: Float,
    var rotation: Float,
    var color: Int,
)
