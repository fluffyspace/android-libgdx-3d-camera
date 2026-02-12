package com.mygdx.game.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import com.mygdx.game.notbaza.Building
import com.mygdx.game.network.OverpassClient

@Composable
fun BuildingMapPickerDialog(
    lat: Double,
    lon: Double,
    onSelect: (Building) -> Unit,
    onSkip: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var buildings by remember { mutableStateOf<List<BuildingWithDistance>>(emptyList()) }
    var selectedBuilding by remember { mutableStateOf<Building?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scanRadius = 150

    suspend fun scanBuildings(scanLat: Double, scanLon: Double) {
        isLoading = true
        errorMessage = null
        try {
            val results = OverpassClient.fetchBuildingsNearPoint(scanLat, scanLon, scanRadius)
            buildings = results.map { (building, dist) ->
                BuildingWithDistance(building, dist)
            }
            if (buildings.isEmpty()) {
                errorMessage = "No buildings found nearby"
            }
        } catch (_: Exception) {
            errorMessage = "Failed to fetch buildings"
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        scanBuildings(lat, lon)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lon), 18f)
    }

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(520.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select building",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Map area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Fetching buildings...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            properties = MapProperties(mapType = MapType.SATELLITE),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                myLocationButtonEnabled = false
                            )
                        ) {
                            // 150m radius circle at map center
                            Circle(
                                center = cameraPositionState.position.target,
                                radius = scanRadius.toDouble(),
                                fillColor = Color.Transparent,
                                strokeColor = Color(0xFFFFFFFF),
                                strokeWidth = 3f
                            )

                            buildings.forEach { bwd ->
                                val building = bwd.building
                                if (building.polygon.size >= 3) {
                                    val isSelected = selectedBuilding?.id == building.id
                                    Polygon(
                                        points = building.polygon.map { LatLng(it.lat, it.lon) },
                                        fillColor = if (isSelected) Color(0x5500CC00) else Color(0x33888888),
                                        strokeColor = if (isSelected) Color(0xFF00AA00) else Color(0xAA666666),
                                        strokeWidth = if (isSelected) 4f else 2f,
                                        clickable = true,
                                        onClick = {
                                            selectedBuilding = if (isSelected) null else building
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected building info
                if (selectedBuilding != null) {
                    val bwd = buildings.firstOrNull { it.building.id == selectedBuilding!!.id }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "OSM #${selectedBuilding!!.id}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        Text(
                            text = "Height: ${String.format("%.1f", selectedBuilding!!.heightMeters)}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (bwd != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Distance: ${String.format("%.0f", bwd.distanceMeters)}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSkip) {
                        Text("Skip")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val center = cameraPositionState.position.target
                            coroutineScope.launch {
                                scanBuildings(center.latitude, center.longitude)
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text("Scan")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            selectedBuilding?.let { onSelect(it) }
                        },
                        enabled = selectedBuilding != null
                    ) {
                        Text("Select this building")
                    }
                }
            }
        }
    }
}
