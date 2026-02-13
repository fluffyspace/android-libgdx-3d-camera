package com.mygdx.game.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class ARObjectInfo(
    val index: Int,
    val id: Int,
    val name: String,
    val distance: Float,
    val hidden: Boolean
)

class ARViewModel : ViewModel() {
    var editModeVisible by mutableStateOf(false)
        private set

    var saveMenuVisible by mutableStateOf(false)
        private set

    var orientationDegrees by mutableFloatStateOf(0f)
        private set

    var selectedEditMode: EditMode? by mutableStateOf(null)
        private set

    var buildingSelected by mutableStateOf(false)

    // Settings panel
    var settingsExpanded by mutableStateOf(false)

    // Object list panel
    var objectListExpanded by mutableStateOf(false)
    var objectList by mutableStateOf<List<ARObjectInfo>>(emptyList())
        private set

    // Distance controls for personal objects
    var minDistanceObjects by mutableFloatStateOf(0f)
    var maxDistanceObjects by mutableFloatStateOf(1000f)
    var noDistanceObjects by mutableStateOf(false)

    // Distance controls for nearby buildings
    var minDistanceBuildings by mutableFloatStateOf(0f)
    var maxDistanceBuildings by mutableFloatStateOf(1000f)
    var noDistanceBuildings by mutableStateOf(false)

    // Depth ordering
    var objectsOnTop by mutableStateOf(true)

    // Info
    var personalObjectCount by mutableIntStateOf(0)
    var nearbyBuildingCount by mutableIntStateOf(0)
    var buildingFetchError by mutableStateOf<String?>(null)

    // Floor grid
    var floorGridEnabled by mutableStateOf(false)
    var floorHeightLive by mutableFloatStateOf(0f)

    // Altitude auto-adjust
    var altitudeAutoAdjusted by mutableStateOf(false)
    var heightOffset by mutableFloatStateOf(0f)
    var groundElevation by mutableFloatStateOf(0f)
    var phoneHeight by mutableFloatStateOf(0f)
    var isAutoAdjusting by mutableStateOf(false)
    var autoAdjustError by mutableStateOf<String?>(null)

    fun computeAltitude(): Float = groundElevation + phoneHeight + heightOffset

    enum class EditMode {
        MOVE, MOVE_VERTICAL, ROTATE, SCALE, ADJUST_HEIGHT
    }

    enum class EditTab {
        OBJECT_EDITOR, VERTICES_EDITOR
    }

    var selectedEditTab by mutableStateOf(EditTab.OBJECT_EDITOR)

    fun selectEditTab(tab: EditTab) {
        selectedEditTab = tab
    }

    fun showEditMode(visible: Boolean) {
        editModeVisible = visible
        if (!visible) {
            selectedEditTab = EditTab.OBJECT_EDITOR
        }
    }

    fun showSaveMenu(visible: Boolean) {
        saveMenuVisible = visible
    }

    fun toggleSettingsExpanded() {
        settingsExpanded = !settingsExpanded
    }

    fun toggleObjectList() {
        objectListExpanded = !objectListExpanded
    }

    fun updateObjectList(objects: List<ARObjectInfo>) {
        objectList = objects
    }

    fun updateOrientationDegrees(degrees: Float) {
        orientationDegrees = degrees
    }

    fun selectEditMode(mode: EditMode?) {
        selectedEditMode = if (selectedEditMode == mode) null else mode
    }

    fun clearEditMode() {
        selectedEditMode = null
    }

    // Coordinate viewer
    var coordinateViewerEnabled by mutableStateOf(false)
    var centerLat by mutableStateOf<Double?>(null)
    var centerLon by mutableStateOf<Double?>(null)
    var centerAlt by mutableStateOf<Double?>(null)
    var centerDistance by mutableFloatStateOf(0f)
    var distanceMethod by mutableStateOf("manual")
    var manualDistance by mutableFloatStateOf(10f)

    // Vertices editor state
    var vertexCount by mutableIntStateOf(0)
    var vertexPolygonClosed by mutableStateOf(false)
    var vertexExtrudeHeight by mutableFloatStateOf(10f)
    var verticesEditorActive by mutableStateOf(false)
    var vertexHitStatus by mutableStateOf("")

    fun resetVerticesEditor() {
        vertexCount = 0
        vertexPolygonClosed = false
        vertexExtrudeHeight = 10f
        verticesEditorActive = false
        vertexHitStatus = ""
    }
}
