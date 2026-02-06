package com.mygdx.game.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ARViewModel : ViewModel() {
    var fov by mutableIntStateOf(34)
        private set

    var cameraPositionText by mutableStateOf("")
        private set

    var compassResult by mutableStateOf("")
        private set

    var editModeVisible by mutableStateOf(false)
        private set

    var saveMenuVisible by mutableStateOf(false)
        private set

    var calibrationVisible by mutableStateOf(false)
        private set

    var noDistance by mutableStateOf(false)
        private set

    var orientationDegrees by mutableFloatStateOf(0f)
        private set

    var selectedEditMode: EditMode? by mutableStateOf(null)
        private set

    var buildingSelected by mutableStateOf(false)

    enum class EditMode {
        MOVE, MOVE_VERTICAL, ROTATE, SCALE, ADJUST_HEIGHT
    }

    fun increaseFov() {
        fov++
    }

    fun decreaseFov() {
        fov--
    }

    fun updateCameraPosition(x: Float, y: Float, z: Float) {
        cameraPositionText = "Camera: %.2f, %.2f, %.2f".format(x, y, z)
    }

    fun updateCompassResult(text: String) {
        compassResult = text
    }

    fun showEditMode(visible: Boolean) {
        editModeVisible = visible
    }

    fun showSaveMenu(visible: Boolean) {
        saveMenuVisible = visible
    }

    fun showCalibration(visible: Boolean) {
        calibrationVisible = visible
    }

    fun toggleNoDistance(): Boolean {
        noDistance = !noDistance
        return noDistance
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
}
