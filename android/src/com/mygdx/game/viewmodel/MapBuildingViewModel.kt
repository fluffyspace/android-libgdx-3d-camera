package com.mygdx.game.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.mygdx.game.baza.UserBuilding
import com.mygdx.game.notbaza.Building

class MapBuildingViewModel : ViewModel() {

    var drawingMode by mutableStateOf(false)
        private set

    var editingBuilding: UserBuilding? by mutableStateOf(null)
        private set

    var editingOsmBuilding: Building? by mutableStateOf(null)
        private set

    var vertices by mutableStateOf<List<LatLng>>(emptyList())
        private set

    var heightInput by mutableStateOf("10")

    fun startDrawing() {
        drawingMode = true
        editingBuilding = null
        editingOsmBuilding = null
        vertices = emptyList()
        heightInput = "10"
    }

    fun startEditing(userBuilding: UserBuilding, building: Building) {
        drawingMode = true
        editingBuilding = userBuilding
        editingOsmBuilding = null
        vertices = building.polygon.map { LatLng(it.lat, it.lon) }
        heightInput = userBuilding.heightMeters.toString()
    }

    fun startEditingOsm(building: Building) {
        drawingMode = true
        editingBuilding = null
        editingOsmBuilding = building
        vertices = building.polygon.map { LatLng(it.lat, it.lon) }
        heightInput = building.heightMeters.toString()
    }

    fun addVertex(latLng: LatLng) {
        vertices = vertices + latLng
    }

    fun removeLastVertex() {
        if (vertices.isNotEmpty()) {
            vertices = vertices.dropLast(1)
        }
    }

    fun updateVertex(index: Int, latLng: LatLng) {
        if (index in vertices.indices) {
            vertices = vertices.toMutableList().also { it[index] = latLng }
        }
    }

    fun clearDrawing() {
        drawingMode = false
        editingBuilding = null
        editingOsmBuilding = null
        vertices = emptyList()
        heightInput = "10"
    }
}
