package com.mygdx.game.activities

import android.graphics.Color
import android.os.Bundle
import android.os.StrictMode
import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.gson.Gson
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import com.mygdx.game.ui.dialogs.AddOrEditObjectDialog
import com.mygdx.game.ui.screens.MapViewerScreen
import com.mygdx.game.ui.theme.MyGdxGameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MapViewer : ComponentActivity() {

    private lateinit var coordinates: Objekt
    private lateinit var db: AppDatabase

    private var showAddDialog by mutableStateOf(false)
    private var dialogGeoPoint by mutableStateOf<GeoPoint?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val policy: StrictMode.ThreadPolicy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        val coordinatesString = intent.extras?.getString("coordinates")
        coordinates = Gson().fromJson(coordinatesString, Objekt::class.java)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()

        setContent {
            MyGdxGameTheme {
                MapViewerScreen(
                    initialCoordinates = coordinates,
                    db = db,
                    onAddObject = { geoPoint ->
                        dialogGeoPoint = geoPoint
                        showAddDialog = true
                    },
                    onDeleteObject = { objectId ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            Log.d("ingo", "deleting object $objectId")
                            db.objektDao().deleteById(objectId)
                        }
                    }
                )

                if (showAddDialog && dialogGeoPoint != null) {
                    AddOrEditObjectDialog(
                        objectToEdit = null,
                        initialCoordinates = "${dialogGeoPoint!!.latitude}, ${dialogGeoPoint!!.longitude}, 0.0",
                        onDismiss = {
                            showAddDialog = false
                            dialogGeoPoint = null
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
                            dialogGeoPoint = null
                        }
                    )
                }
            }
        }
    }
}