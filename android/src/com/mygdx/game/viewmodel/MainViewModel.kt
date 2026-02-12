package com.mygdx.game.viewmodel

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.mygdx.game.BuildConfig
import com.mygdx.game.DatastoreRepository
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import com.mygdx.game.baza.UserBuilding
import com.mygdx.game.network.OsmApiClient
import com.mygdx.game.network.OsmAuthManager
import com.mygdx.game.network.OverpassClient
import com.mygdx.game.network.OverpassServer
import com.mygdx.game.ui.dialogs.BuildingUploadState
import com.mygdx.game.ui.dialogs.BuildingUploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db: AppDatabase = AppDatabase.getInstance(application)

    val osmAuthManager = OsmAuthManager(application, BuildConfig.OSM_CLIENT_ID)

    private val _objects = MutableStateFlow<List<Objekt>>(emptyList())
    val objects: StateFlow<List<Objekt>> = _objects.asStateFlow()

    private val _camera = MutableStateFlow(Objekt(0, 0f, 0f, 0f))
    val camera: StateFlow<Objekt> = _camera.asStateFlow()

    private val _cameraCoordinatesText = MutableStateFlow("0.0, 0.0, 0.0")
    val cameraCoordinatesText: StateFlow<String> = _cameraCoordinatesText.asStateFlow()

    private val _isOsmLoggedIn = MutableStateFlow(false)
    val isOsmLoggedIn: StateFlow<Boolean> = _isOsmLoggedIn.asStateFlow()

    private val _osmDisplayName = MutableStateFlow<String?>(null)
    val osmDisplayName: StateFlow<String?> = _osmDisplayName.asStateFlow()

    private val _uploadableBuildings = MutableStateFlow<List<UserBuilding>>(emptyList())
    val uploadableBuildings: StateFlow<List<UserBuilding>> = _uploadableBuildings.asStateFlow()

    private val _uploadStates = MutableStateFlow<Map<Int, BuildingUploadState>>(emptyMap())
    val uploadStates: StateFlow<Map<Int, BuildingUploadState>> = _uploadStates.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadDone = MutableStateFlow(false)
    val uploadDone: StateFlow<Boolean> = _uploadDone.asStateFlow()

    private val _disabledCategories = MutableStateFlow<Set<String>>(emptySet())
    val disabledCategories: StateFlow<Set<String>> = _disabledCategories.asStateFlow()

    private val _overpassServers = MutableStateFlow(listOf(OverpassServer.DEFAULT))
    val overpassServers: StateFlow<List<OverpassServer>> = _overpassServers.asStateFlow()

    private val _selectedServerId = MutableStateFlow("default")
    val selectedServerId: StateFlow<String> = _selectedServerId.asStateFlow()

    init {
        loadCameraFromDataStore()
        loadDisabledCategories()
        loadOverpassServers()
        refreshOsmLoginState()
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

    private fun loadDisabledCategories() {
        DatastoreRepository.readDisabledCategories(getApplication()) { set ->
            viewModelScope.launch(Dispatchers.Main) {
                _disabledCategories.value = set
            }
        }
    }

    private fun loadOverpassServers() {
        DatastoreRepository.readOverpassServers(getApplication()) { servers ->
            viewModelScope.launch(Dispatchers.Main) {
                _overpassServers.value = servers
            }
        }
        DatastoreRepository.readSelectedOverpassServerId(getApplication()) { id ->
            viewModelScope.launch(Dispatchers.Main) {
                _selectedServerId.value = id
                applySelectedServer(id)
            }
        }
    }

    private fun applySelectedServer(id: String) {
        val server = _overpassServers.value.find { it.id == id } ?: OverpassServer.DEFAULT
        OverpassClient.overpassUrl = server.url
    }

    fun selectServer(id: String) {
        _selectedServerId.value = id
        applySelectedServer(id)
        DatastoreRepository.updateDataStore(
            getApplication(),
            DatastoreRepository.selectedOverpassServerIdKey,
            id
        )
    }

    fun addServer(name: String, url: String) {
        val server = OverpassServer(name = name, url = url)
        val updated = _overpassServers.value + server
        _overpassServers.value = updated
        saveOverpassServers(updated)
    }

    fun updateServer(id: String, name: String, url: String) {
        val updated = _overpassServers.value.map {
            if (it.id == id && !it.isDefault) it.copy(name = name, url = url) else it
        }
        _overpassServers.value = updated
        saveOverpassServers(updated)
        if (_selectedServerId.value == id) {
            applySelectedServer(id)
        }
    }

    fun deleteServer(id: String) {
        if (id == "default") return
        val updated = _overpassServers.value.filter { it.id != id }
        _overpassServers.value = updated
        saveOverpassServers(updated)
        if (_selectedServerId.value == id) {
            selectServer("default")
        }
    }

    private fun saveOverpassServers(servers: List<OverpassServer>) {
        DatastoreRepository.updateDataStore(
            getApplication(),
            DatastoreRepository.overpassServersKey,
            Gson().toJson(servers.toTypedArray())
        )
    }

    fun toggleCategory(key: String) {
        val current = _disabledCategories.value.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _disabledCategories.value = current
        DatastoreRepository.updateDataStore(
            getApplication(),
            DatastoreRepository.disabledCategoriesKey,
            Gson().toJson(current.toTypedArray())
        )
    }

    fun createObjectFromInput(
        coordinates: String,
        name: String,
        colorString: String,
        osmId: Long? = null,
        polygonJson: String? = null,
        heightMeters: Float = 10f,
        minHeightMeters: Float = 0f,
        category: String? = null
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
            this.category = category
        }
    }

    fun refreshOsmLoginState() {
        _isOsmLoggedIn.value = osmAuthManager.isLoggedIn()
        _osmDisplayName.value = osmAuthManager.getDisplayName()
    }

    fun logoutFromOsm() {
        osmAuthManager.logout()
        refreshOsmLoginState()
    }

    fun loadUploadableBuildings() {
        viewModelScope.launch(Dispatchers.IO) {
            val buildings = db.userBuildingDao().getUploadable()
            _uploadableBuildings.value = buildings
            _uploadStates.value = buildings.associate {
                it.id to BuildingUploadState(it)
            }
            _uploadDone.value = false
        }
    }

    fun uploadToOsm(buildings: List<UserBuilding>, comment: String) {
        val token = osmAuthManager.getAccessToken() ?: return
        _isUploading.value = true
        _uploadDone.value = false

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val changesetId = OsmApiClient.createChangeset(token, comment)

                for (building in buildings) {
                    val osmId = building.osmId ?: continue

                    _uploadStates.value = _uploadStates.value.toMutableMap().apply {
                        put(building.id, BuildingUploadState(building, BuildingUploadStatus.UPLOADING))
                    }

                    try {
                        val way = OsmApiClient.fetchWay(osmId)

                        val newTags = mutableMapOf<String, String>()
                        newTags["height"] = building.heightMeters.toString()
                        if (building.minHeightMeters > 0) {
                            newTags["min_height"] = building.minHeightMeters.toString()
                        }

                        OsmApiClient.updateWayTags(token, changesetId, way, newTags)

                        _uploadStates.value = _uploadStates.value.toMutableMap().apply {
                            put(building.id, BuildingUploadState(building, BuildingUploadStatus.SUCCESS))
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to upload building ${building.osmId}", e)
                        _uploadStates.value = _uploadStates.value.toMutableMap().apply {
                            put(building.id, BuildingUploadState(building, BuildingUploadStatus.ERROR, e.message))
                        }
                    }
                }

                OsmApiClient.closeChangeset(token, changesetId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Upload failed", e)
                // Mark all remaining pending as error
                _uploadStates.value = _uploadStates.value.toMutableMap().mapValues { (_, state) ->
                    if (state.status == BuildingUploadStatus.PENDING || state.status == BuildingUploadStatus.UPLOADING) {
                        state.copy(status = BuildingUploadStatus.ERROR, error = e.message)
                    } else state
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isUploading.value = false
                    _uploadDone.value = true
                }
            }
        }
    }

    companion object {
        const val UNCATEGORIZED_KEY = "__uncategorized__"

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
