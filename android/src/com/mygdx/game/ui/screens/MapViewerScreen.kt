package com.mygdx.game.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.mygdx.game.DatastoreRepository
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MapViewerScreen(
    initialCoordinates: Objekt,
    db: AppDatabase,
    onAddObject: (LatLng) -> Unit,
    onDeleteObject: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var objects by remember { mutableStateOf<List<Objekt>>(emptyList()) }
    var cameraMarkerPosition by remember { mutableStateOf<LatLng?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(initialCoordinates.x.toDouble(), initialCoordinates.y.toDouble()),
            15f
        )
    }

    // Load objects from database
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            objects = db.objektDao().getAll()
        }
    }

    // Load camera position from datastore
    LaunchedEffect(Unit) {
        DatastoreRepository.readFromDataStore(context) { objekt ->
            cameraMarkerPosition = LatLng(objekt.x.toDouble(), objekt.y.toDouble())
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = false
        ),
        onMapClick = { latLng ->
            onAddObject(latLng)
        },
        onMapLongClick = { latLng ->
            Toast.makeText(
                context,
                "${latLng.latitude}, ${latLng.longitude}",
                Toast.LENGTH_LONG
            ).show()
            Log.d("ingo", "novi marker")
            cameraMarkerPosition = latLng
            DatastoreRepository.updateDataStore(
                context,
                DatastoreRepository.cameraDataStoreKey,
                Gson().toJson(
                    Objekt(
                        0,
                        latLng.latitude.toFloat(),
                        latLng.longitude.toFloat(),
                        0f
                    )
                )
            )
        }
    ) {
        // Camera position marker
        cameraMarkerPosition?.let { position ->
            Marker(
                state = MarkerState(position = position),
                title = "Camera Position",
                snippet = "Current camera location"
            )
        }

        // Object markers
        objects.forEach { objekt ->
            Marker(
                state = MarkerState(
                    position = LatLng(objekt.x.toDouble(), objekt.y.toDouble())
                ),
                title = objekt.name ?: "Object",
                snippet = objekt.id.toString(),
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                onClick = { marker ->
                    Toast.makeText(context, marker.title, Toast.LENGTH_SHORT).show()
                    true
                },
                onInfoWindowLongClick = { marker ->
                    val id = marker.snippet?.toIntOrNull()
                    if (id != null) {
                        scope.launch(Dispatchers.IO) {
                            db.objektDao().deleteById(id)
                            withContext(Dispatchers.Main) {
                                objects = objects.filter { it.id != id }
                                Toast.makeText(context, "Removed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }
    }
}
