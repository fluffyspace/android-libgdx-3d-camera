package com.mygdx.game.ui.dialogs

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mygdx.game.BuildConfig
import com.mygdx.game.baza.Objekt
import com.mygdx.game.network.OverpassClient
import com.mygdx.game.notbaza.Building
import com.mygdx.game.ui.components.BuildingPreviewRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OsmBuildingData(
    val osmId: Long,
    val polygonJson: String,
    val heightMeters: Float,
    val minHeightMeters: Float,
    val polygon: List<com.mygdx.game.notbaza.LatLon>
)

private data class GeocodingResponse(
    val results: List<GeocodingResult> = emptyList(),
    val status: String = ""
)

private data class GeocodingResult(
    @SerializedName("formatted_address") val formattedAddress: String = "",
    val geometry: Geometry = Geometry()
)

private data class Geometry(
    val location: LatLng = LatLng()
)

private data class LatLng(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

private suspend fun searchPlaces(query: String): List<GeocodingResult> {
    if (query.isBlank() || BuildConfig.MAPS_API_KEY.isBlank()) return emptyList()

    return withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://maps.googleapis.com/maps/api/geocode/json?address=$encodedQuery&key=${BuildConfig.MAPS_API_KEY}")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val geocodingResponse = Gson().fromJson(response, GeocodingResponse::class.java)
            geocodingResponse.results
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private fun parseCoordinatesLatLon(coords: String): Pair<Double, Double>? {
    val parts = coords.split(",").map { it.trim() }
    if (parts.size != 3) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    return lat to lon
}

@Composable
fun AddOrEditObjectDialog(
    objectToEdit: Objekt?,
    initialCoordinates: String? = null,
    initialName: String? = null,
    initialColor: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (coordinates: String, name: String, color: String, osmData: OsmBuildingData?) -> Unit,
    onChooseFromMap: ((currentCoordinates: String, currentName: String, currentColor: String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    var coordinates by remember {
        mutableStateOf(
            objectToEdit?.let { "${it.x}, ${it.y}, ${it.z}" }
                ?: initialCoordinates
                ?: ""
        )
    }
    var name by remember { mutableStateOf(objectToEdit?.name ?: initialName ?: "") }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // OSM building state
    var osmBuildingData by remember { mutableStateOf<OsmBuildingData?>(null) }
    var osmSearchStatus by remember { mutableStateOf("") }
    var isSearchingOsm by remember { mutableStateOf(false) }
    var lastSearchedCoords by remember { mutableStateOf("") }
    var nearbyBuildings by remember { mutableStateOf<List<BuildingWithDistance>>(emptyList()) }
    var showBuildingPicker by remember { mutableStateOf(false) }

    // Auto-fetch nearby OSM buildings when coordinates change
    LaunchedEffect(coordinates) {
        val parsed = parseCoordinatesLatLon(coordinates)
        if (parsed != null && coordinates != lastSearchedCoords) {
            lastSearchedCoords = coordinates
            isSearchingOsm = true
            osmSearchStatus = "Searching for buildings..."
            osmBuildingData = null
            nearbyBuildings = emptyList()
            try {
                val results = OverpassClient.fetchBuildingsNearPoint(parsed.first, parsed.second, 50)
                if (results.isNotEmpty()) {
                    nearbyBuildings = results.map { (building, dist) ->
                        BuildingWithDistance(building, dist)
                    }
                    showBuildingPicker = true
                    osmSearchStatus = "${results.size} building(s) found"
                } else {
                    osmSearchStatus = "No buildings found (will use sphere)"
                }
            } catch (_: Exception) {
                osmSearchStatus = "OSM lookup failed (will use sphere)"
            }
            isSearchingOsm = false
        }
    }

    // Convert existing color to hue, or use initial color, or default to 0 (red)
    var hue by remember {
        mutableFloatStateOf(
            run {
                val colorInt = objectToEdit?.color
                    ?: initialColor?.let {
                        try { AndroidColor.parseColor(it) } catch (e: Exception) { null }
                    }
                if (colorInt != null) {
                    val hsv = FloatArray(3)
                    AndroidColor.colorToHSV(colorInt, hsv)
                    hsv[0]
                } else {
                    0f
                }
            }
        )
    }

    val previewColor = Color.hsv(hue, 1f, 1f)

    // Building picker dialog
    if (showBuildingPicker && nearbyBuildings.isNotEmpty()) {
        BuildingPickerDialog(
            buildings = nearbyBuildings,
            colorHue = hue,
            onSelect = { building ->
                osmBuildingData = OsmBuildingData(
                    osmId = building.id,
                    polygonJson = Gson().toJson(building.polygon),
                    heightMeters = building.heightMeters,
                    minHeightMeters = building.minHeightMeters,
                    polygon = building.polygon
                )
                osmSearchStatus = "Building selected (OSM #${building.id})"
                showBuildingPicker = false
            },
            onSkip = {
                osmSearchStatus = "No building selected (will use sphere)"
                showBuildingPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (objectToEdit == null) "Add new object" else "Edit object",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Search field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search place") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                coroutineScope.launch {
                                    isSearching = true
                                    searchResults = searchPlaces(searchQuery)
                                    isSearching = false
                                }
                            }
                        },
                        enabled = !isSearching && searchQuery.isNotBlank()
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                }

                // Search results
                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 150.dp)
                        ) {
                            items(searchResults) { result ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val lat = result.geometry.location.lat
                                            val lng = result.geometry.location.lng
                                            coordinates = "$lat, $lng, 0"
                                            if (name.isBlank()) {
                                                name = result.formattedAddress.split(",").firstOrNull()?.trim() ?: ""
                                            }
                                            searchResults = emptyList()
                                            searchQuery = ""
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = result.formattedAddress,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (result != searchResults.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = coordinates,
                        onValueChange = { coordinates = it },
                        label = { Text("Coordinates (lat, lon, alt)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    if (onChooseFromMap != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
                                val colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
                                onChooseFromMap(coordinates, name, colorHex)
                            }
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Choose from map"
                            )
                        }
                    }
                }

                // OSM building status
                if (osmSearchStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSearchingOsm) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = osmSearchStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = if (osmBuildingData != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!isSearchingOsm && nearbyBuildings.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showBuildingPicker = true },
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 8.dp, vertical = 0.dp
                                )
                            ) {
                                Text(
                                    text = if (osmBuildingData != null) "Change" else "Pick",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // 3D building preview
                if (osmBuildingData != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val hsv = floatArrayOf(hue, 1f, 1f)
                    val colorInt = AndroidColor.HSVToColor(hsv)
                    val pR = android.graphics.Color.red(colorInt) / 255f
                    val pG = android.graphics.Color.green(colorInt) / 255f
                    val pB = android.graphics.Color.blue(colorInt) / 255f
                    AndroidView(
                        factory = { ctx ->
                            BuildingPreviewRenderer(
                                context = ctx,
                                polygon = osmBuildingData!!.polygon,
                                heightMeters = osmBuildingData!!.heightMeters,
                                minHeightMeters = osmBuildingData!!.minHeightMeters,
                                colorR = pR,
                                colorG = pG,
                                colorB = pB
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = previewColor,
                            activeTrackColor = previewColor
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parts = coordinates.split(",").map { it.trim() }
                    if (parts.size == 3) {
                        val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
                        val colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
                        onConfirm(coordinates, name, colorHex, osmBuildingData)
                    }
                }
            ) {
                Text(if (objectToEdit == null) "Add" else "Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
