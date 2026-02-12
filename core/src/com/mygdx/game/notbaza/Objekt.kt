package com.mygdx.game.notbaza

import com.badlogic.gdx.graphics.Color

data class Objekt(
    var id:Int,
    var x: Float,
    var y: Float,
    var z: Float,
    var name: String = "default_name",
    var size: Float = 1f,
    var rotationX: Float = 0f,
    var rotationY: Float = 0f,
    var rotationZ: Float = 0f,
    var color: Int = 0,
    var libgdxcolor: Color = Color.GREEN,
    var diffX: Float = 0f,
    var diffY: Float = 0f,
    var diffZ: Float = 0f,
    var changed: Boolean = false,
    var visible: Boolean = false,
    var osmId: Long? = null,
    var polygonJson: String? = null,
    var polygon: List<LatLon>? = null,
    var heightMeters: Float = 10f,
    var minHeightMeters: Float = 0f,
    var hidden: Boolean = false,
    var category: String? = null,
)

