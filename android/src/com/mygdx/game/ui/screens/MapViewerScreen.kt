package com.mygdx.game.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.mygdx.game.DatastoreRepository
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import com.mygdx.game.baza.UserBuilding
import com.mygdx.game.baza.toBuilding
import com.mygdx.game.notbaza.Building
import com.mygdx.game.viewmodel.MapBuildingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MapViewerScreen(
    initialCoordinates: Objekt,
    db: AppDatabase,
    onAddObject: (LatLng) -> Unit,
    onDeleteObject: (Int) -> Unit,
    pickMode: Boolean = false,
    mapBuildingViewModel: MapBuildingViewModel? = null,
    osmBuildings: List<Building> = emptyList(),
    userBuildings: List<UserBuilding> = emptyList(),
    onSaveBuilding: ((UserBuilding) -> Unit)? = null,
    onDeleteBuilding: ((UserBuilding) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var objects by remember { mutableStateOf<List<Objekt>>(emptyList()) }
    var cameraMarkerPosition by remember { mutableStateOf<LatLng?>(null) }
    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showHeightInput by remember { mutableStateOf(false) }

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

    val drawingMode = mapBuildingViewModel?.drawingMode ?: false
    val vertices = mapBuildingViewModel?.vertices ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = selectedMapType),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
            ),
            onMapClick = { latLng ->
                if (drawingMode) {
                    mapBuildingViewModel?.addVertex(latLng)
                } else if (!pickMode) {
                    onAddObject(latLng)
                }
            },
            onMapLongClick = { latLng ->
                if (!pickMode && !drawingMode) {
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

            // OSM building outlines (semi-transparent gray)
            osmBuildings.forEach { building ->
                if (building.polygon.size >= 3) {
                    Polygon(
                        points = building.polygon.map { LatLng(it.lat, it.lon) },
                        fillColor = Color(0x33888888),
                        strokeColor = Color(0xAA666666),
                        strokeWidth = 2f,
                        clickable = true,
                        onClick = {
                            mapBuildingViewModel?.startEditingOsm(building)
                        }
                    )
                }
            }

            // User building outlines (semi-transparent blue)
            userBuildings.forEach { userBuilding ->
                val building = remember(userBuilding) {
                    userBuilding.toBuilding()
                }
                if (building.polygon.size >= 3) {
                    Polygon(
                        points = building.polygon.map { LatLng(it.lat, it.lon) },
                        fillColor = Color(0x334488CC),
                        strokeColor = Color(0xAA2266AA),
                        strokeWidth = 3f,
                        clickable = true,
                        onClick = {
                            mapBuildingViewModel?.startEditing(userBuilding, building)
                        }
                    )
                }
            }

            // Drawing mode: polygon preview
            if (drawingMode && vertices.size >= 3) {
                Polygon(
                    points = vertices,
                    fillColor = Color(0x3300CC00),
                    strokeColor = Color(0xAA00AA00),
                    strokeWidth = 3f
                )
            } else if (drawingMode && vertices.size >= 2) {
                Polyline(
                    points = vertices,
                    color = Color(0xFF00AA00),
                    width = 3f
                )
            }

            // Drawing mode: draggable markers at vertices
            if (drawingMode) {
                vertices.forEachIndexed { index, latLng ->
                    val markerState = remember(index, latLng) { MarkerState(position = latLng) }

                    // Sync position changes from drag back to ViewModel
                    LaunchedEffect(markerState.position) {
                        if (markerState.position != latLng) {
                            mapBuildingViewModel?.updateVertex(index, markerState.position)
                        }
                    }

                    Marker(
                        state = markerState,
                        draggable = true,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                        title = "Vertex ${index + 1}"
                    )
                }
            }
        }

        // Layer switcher button
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { showLayerMenu = true }
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Change map layer")
            }
            DropdownMenu(
                expanded = showLayerMenu,
                onDismissRequest = { showLayerMenu = false }
            ) {
                listOf(
                    "Normal" to MapType.NORMAL,
                    "Satellite" to MapType.SATELLITE,
                    "Terrain" to MapType.TERRAIN,
                    "Hybrid" to MapType.HYBRID
                ).forEach { (label, type) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedMapType = type
                            showLayerMenu = false
                        },
                        trailingIcon = if (selectedMapType == type) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
        }

        // Center pin overlay for pick mode
        if (pickMode) {
            // Pin icon in center (offset up so the pin point is at center)
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Pick location",
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-24).dp)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Confirm button
            FloatingActionButton(
                onClick = {
                    val centerLatLng = cameraPositionState.position.target
                    onAddObject(centerLatLng)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirm location")
            }
        }

        // "Draw Building" FAB (shown when not in drawing mode and not in pick mode)
        if (!drawingMode && !pickMode && mapBuildingViewModel != null) {
            FloatingActionButton(
                onClick = { mapBuildingViewModel.startDrawing() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 80.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Draw building")
            }
        }

        // Drawing mode bottom controls
        if (drawingMode && mapBuildingViewModel != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .background(
                        Color.White.copy(alpha = 0.9f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!showHeightInput) {
                    Text(
                        text = if (mapBuildingViewModel.editingBuilding != null || mapBuildingViewModel.editingOsmBuilding != null)
                            "Editing building - drag markers to adjust"
                        else
                            "Tap map to add vertices (${vertices.size} placed)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Undo button
                        SmallFloatingActionButton(
                            onClick = { mapBuildingViewModel.removeLastVertex() },
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Undo vertex")
                        }

                        // Delete button (only when editing existing building)
                        if (mapBuildingViewModel.editingBuilding != null) {
                            SmallFloatingActionButton(
                                onClick = {
                                    mapBuildingViewModel.editingBuilding?.let { building ->
                                        onDeleteBuilding?.invoke(building)
                                    }
                                    mapBuildingViewModel.clearDrawing()
                                    showHeightInput = false
                                },
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete building", tint = Color.White)
                            }
                        }

                        // Cancel button
                        SmallFloatingActionButton(
                            onClick = {
                                mapBuildingViewModel.clearDrawing()
                                showHeightInput = false
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text("X")
                        }

                        // Confirm button (enabled when >= 3 vertices)
                        FloatingActionButton(
                            onClick = { showHeightInput = true },
                            containerColor = if (vertices.size >= 3) MaterialTheme.colorScheme.primary else Color.Gray
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Confirm polygon")
                        }
                    }
                } else {
                    // Height input + save
                    Text(
                        text = "Set building height (meters)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = mapBuildingViewModel.heightInput,
                            onValueChange = { mapBuildingViewModel.heightInput = it },
                            label = { Text("Height (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(140.dp),
                            singleLine = true
                        )
                        FloatingActionButton(
                            onClick = {
                                val height = mapBuildingViewModel.heightInput.toFloatOrNull() ?: 10f
                                val polygonJson = Gson().toJson(
                                    vertices.map { mapOf("lat" to it.latitude, "lon" to it.longitude) }
                                )
                                val editing = mapBuildingViewModel.editingBuilding
                                val editingOsm = mapBuildingViewModel.editingOsmBuilding

                                val userBuilding = when {
                                    editing != null -> editing.copy(
                                        polygonJson = polygonJson,
                                        heightMeters = height
                                    )
                                    editingOsm != null -> UserBuilding(
                                        osmId = editingOsm.id,
                                        polygonJson = polygonJson,
                                        heightMeters = height,
                                        minHeightMeters = editingOsm.minHeightMeters
                                    )
                                    else -> UserBuilding(
                                        polygonJson = polygonJson,
                                        heightMeters = height
                                    )
                                }
                                onSaveBuilding?.invoke(userBuilding)
                                mapBuildingViewModel.clearDrawing()
                                showHeightInput = false
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save building")
                        }
                    }
                }
            }
        }
    }
}
