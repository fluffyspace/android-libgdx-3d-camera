package com.mygdx.game.viewmodel

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.gson.Gson
import com.mygdx.game.DatastoreRepository
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "database-name"
    ).build()

    private val _objects = MutableStateFlow<List<Objekt>>(emptyList())
    val objects: StateFlow<List<Objekt>> = _objects.asStateFlow()

    private val _camera = MutableStateFlow(Objekt(0, 0f, 0f, 0f))
    val camera: StateFlow<Objekt> = _camera.asStateFlow()

    private val _cameraCoordinatesText = MutableStateFlow("0.0, 0.0, 0.0")
    val cameraCoordinatesText: StateFlow<String> = _cameraCoordinatesText.asStateFlow()

    init {
        loadCameraFromDataStore()
    }

    fun loadObjects() {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedObjects = db.objektDao().getAll()
            _objects.value = loadedObjects
        }
    }

    fun addObject(objekt: Objekt) {
        viewModelScope.launch(Dispatchers.IO) {
            db.objektDao().insertAll(objekt)
            _objects.value = db.objektDao().getAll()
        }
    }

    fun updateObject(objekt: Objekt) {
        viewModelScope.launch(Dispatchers.IO) {
            db.objektDao().update(objekt)
            _objects.value = db.objektDao().getAll()
        }
    }

    fun deleteObject(objekt: Objekt) {
        viewModelScope.launch(Dispatchers.IO) {
            db.objektDao().delete(objekt)
            _objects.value = db.objektDao().getAll()
        }
    }

    fun updateCamera(objekt: Objekt) {
        _camera.value = objekt
        updateCameraCoordinatesText()
        saveCameraToDataStore()
    }

    fun setCameraFromText(text: String): Boolean {
        val objekt = textToObject(text) ?: return false
        updateCamera(objekt)
        return true
    }

    fun updateCameraCoordinatesText() {
        val cam = _camera.value
        _cameraCoordinatesText.value = "${cam.x}, ${cam.y}, ${cam.z}"
    }

    private fun saveCameraToDataStore() {
        DatastoreRepository.updateDataStore(
            getApplication(),
            DatastoreRepository.cameraDataStoreKey,
            Gson().toJson(_camera.value)
        )
    }

    private fun loadCameraFromDataStore() {
        DatastoreRepository.readFromDataStore(getApplication()) { objekt ->
            viewModelScope.launch(Dispatchers.Main) {
                _camera.value = objekt
                updateCameraCoordinatesText()
            }
        }
    }

    fun createObjectFromInput(
        coordinates: String,
        name: String,
        colorString: String,
        osmId: Long? = null,
        polygonJson: String? = null,
        heightMeters: Float = 10f,
        minHeightMeters: Float = 0f
    ): Objekt? {
        return textToObject(coordinates)?.apply {
            this.name = name
            if (colorString.isNotEmpty()) {
                try {
                    this.color = Color.parseColor(colorString)
                } catch (e: Exception) {
                    // Keep default color
                }
            }
            this.osmId = osmId
            this.polygonJson = polygonJson
            this.heightMeters = heightMeters
            this.minHeightMeters = minHeightMeters
        }
    }

    companion object {
        fun textToObject(text: String): Objekt? {
            val coordinates = text.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (coordinates.size != 3) return null
            return try {
                Objekt(
                    0,
                    coordinates[0].toFloat(),
                    coordinates[1].toFloat(),
                    coordinates[2].toFloat()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
