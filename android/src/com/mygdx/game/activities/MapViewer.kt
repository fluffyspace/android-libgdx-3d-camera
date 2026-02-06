package com.mygdx.game.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import com.mygdx.game.baza.UserBuilding
import com.mygdx.game.baza.toBuilding
import com.mygdx.game.network.BuildingCache
import com.mygdx.game.network.OverpassClient
import com.mygdx.game.notbaza.Building
import com.mygdx.game.ui.dialogs.AddOrEditObjectDialog
import com.mygdx.game.ui.screens.MapViewerScreen
import com.mygdx.game.ui.theme.MyGdxGameTheme
import com.mygdx.game.viewmodel.MapBuildingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MapViewer : ComponentActivity() {

    private lateinit var coordinates: Objekt
    private lateinit var db: AppDatabase
    private lateinit var mapBuildingViewModel: MapBuildingViewModel

    private var showAddDialog by mutableStateOf(false)
    private var dialogLatLng by mutableStateOf<LatLng?>(null)
    private var osmBuildings by mutableStateOf<List<Building>>(emptyList())
    private var userBuildingsList by mutableStateOf<List<UserBuilding>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val coordinatesString = intent.extras?.getString("coordinates")
        coordinates = Gson().fromJson(coordinatesString, Objekt::class.java)
        val pickMode = intent.extras?.getBoolean("pickMode", false) ?: false

        db = AppDatabase.getInstance(applicationContext)
        mapBuildingViewModel = ViewModelProvider(this)[MapBuildingViewModel::class.java]

        // Load OSM buildings
        val buildingCache = BuildingCache(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val lat = coordinates.x.toDouble()
                val lon = coordinates.y.toDouble()
                val buildings = buildingCache.getCached(lat, lon)
                    ?: OverpassClient.fetchBuildings(lat, lon).also {
                        buildingCache.putCache(lat, lon, it)
                    }
                withContext(Dispatchers.Main) {
                    osmBuildings = buildings
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Load user buildings
        loadUserBuildings()

        setContent {
            MyGdxGameTheme {
                MapViewerScreen(
                    initialCoordinates = coordinates,
                    db = db,
                    onAddObject = { latLng ->
                        if (pickMode) {
                            // Return the coordinates to the caller
                            val resultIntent = Intent()
                            resultIntent.putExtra("latitude", latLng.latitude)
                            resultIntent.putExtra("longitude", latLng.longitude)
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        } else {
                            dialogLatLng = latLng
                            showAddDialog = true
                        }
                    },
                    onDeleteObject = { objectId ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            Log.d("ingo", "deleting object $objectId")
                            db.objektDao().deleteById(objectId)
                        }
                    },
                    pickMode = pickMode,
                    mapBuildingViewModel = mapBuildingViewModel,
                    osmBuildings = osmBuildings,
                    userBuildings = userBuildingsList,
                    onSaveBuilding = { userBuilding ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dao = db.userBuildingDao()
                            if (userBuilding.id != 0) {
                                dao.update(userBuilding)
                            } else {
                                dao.insert(userBuilding)
                            }
                            loadUserBuildings()
                        }
                    },
                    onDeleteBuilding = { userBuilding ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.userBuildingDao().delete(userBuilding)
                            loadUserBuildings()
                        }
                    }
                )

                if (showAddDialog && dialogLatLng != null) {
                    AddOrEditObjectDialog(
                        objectToEdit = null,
                        initialCoordinates = "${dialogLatLng!!.latitude}, ${dialogLatLng!!.longitude}, 0.0",
                        onDismiss = {
                            showAddDialog = false
                            dialogLatLng = null
                        },
                        onConfirm = { coordsText, name, colorText ->
                            val parts = coordsText.split(",").map { it.trim() }
                            if (parts.size == 3) {
                                try {
                                    val objekt = Objekt(
                                        id = 0,
                                        x = parts[0].toFloat(),
                                        y = parts[1].toFloat(),
                                        z = parts[2].toFloat(),
                                        name = name,
                                        color = if (colorText.isNotEmpty()) {
                                            try {
                                                Color.parseColor(colorText)
                                            } catch (e: Exception) {
                                                Color.WHITE
                                            }
                                        } else Color.WHITE
                                    )
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        Log.d("ingo", "adding object")
                                        db.objektDao().insertAll(objekt)
                                    }
                                } catch (e: NumberFormatException) {
                                    // Invalid coordinates
                                }
                            }
                            showAddDialog = false
                            dialogLatLng = null
                        }
                    )
                }
            }
        }
    }

    private fun loadUserBuildings() {
        lifecycleScope.launch(Dispatchers.IO) {
            val buildings = db.userBuildingDao().getAll()
            withContext(Dispatchers.Main) {
                userBuildingsList = buildings
            }
        }
    }
}
