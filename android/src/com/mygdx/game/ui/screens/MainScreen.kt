package com.mygdx.game.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mygdx.game.R
import com.mygdx.game.baza.Objekt
import com.mygdx.game.ui.components.ObjectListItem
import com.mygdx.game.ui.dialogs.AddOrEditObjectDialog
import com.mygdx.game.ui.dialogs.OsmUploadDialog
import com.mygdx.game.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenMap: (Objekt) -> Unit,
    onOpenViewer: (camera: Objekt, objects: List<Objekt>) -> Unit,
    onUpdateLocation: ((Objekt) -> Unit) -> Unit,
    onShowInvalidCoordinatesToast: () -> Unit,
    onPickCoordinatesFromMap: (currentCoordinates: String, pendingName: String, pendingColor: String) -> Unit = { _, _, _ -> },
    pickedCoordinates: String? = null,
    pendingName: String? = null,
    pendingColor: String? = null,
    onClearPickedCoordinates: () -> Unit = {},
    onOsmLogin: () -> Unit = {},
    onOsmLogout: () -> Unit = {}
) {
    val objects by viewModel.objects.collectAsState()
    val camera by viewModel.camera.collectAsState()
    val cameraText by viewModel.cameraCoordinatesText.collectAsState()
    val isOsmLoggedIn by viewModel.isOsmLoggedIn.collectAsState()
    val osmDisplayName by viewModel.osmDisplayName.collectAsState()
    val uploadableBuildings by viewModel.uploadableBuildings.collectAsState()
    val uploadStates by viewModel.uploadStates.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadDone by viewModel.uploadDone.collectAsState()
    val disabledCategories by viewModel.disabledCategories.collectAsState()

    val allCategories by remember(objects) {
        derivedStateOf { objects.mapNotNull { it.category }.distinct().sorted() }
    }
    val hasUncategorized by remember(objects) {
        derivedStateOf { objects.any { it.category == null } }
    }
    val filteredObjects by remember(objects, disabledCategories) {
        derivedStateOf {
            objects.filter { obj ->
                val key = obj.category ?: MainViewModel.UNCATEGORIZED_KEY
                key !in disabledCategories
            }
        }
    }
    val existingCategories by remember(objects) {
        derivedStateOf { objects.mapNotNull { it.category }.distinct().sorted() }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var objectToEdit by remember { mutableStateOf<Objekt?>(null) }
    var coordinatesInput by remember(cameraText) { mutableStateOf(cameraText) }
    var showOsmMenu by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var objectToDelete by remember { mutableStateOf<Objekt?>(null) }

    // Show dialog with picked coordinates when returning from map
    var showDialogWithPickedCoords by remember { mutableStateOf(false) }
    if (pickedCoordinates != null && !showDialogWithPickedCoords && !showAddDialog) {
        showDialogWithPickedCoords = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR Objects") },
                actions = {
                    Box {
                        IconButton(onClick = {
                            if (isOsmLoggedIn) {
                                showOsmMenu = true
                            } else {
                                onOsmLogin()
                            }
                        }) {
                            Icon(Icons.Default.Person, contentDescription = "OSM Account")
                        }
                        DropdownMenu(
                            expanded = showOsmMenu,
                            onDismissRequest = { showOsmMenu = false }
                        ) {
                            osmDisplayName?.let { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = { },
                                    enabled = false
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Upload to OSM") },
                                onClick = {
                                    showOsmMenu = false
                                    viewModel.loadUploadableBuildings()
                                    showUploadDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    showOsmMenu = false
                                    onOsmLogout()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val success = viewModel.setCameraFromText(coordinatesInput)
                    if (!success) {
                        onShowInvalidCoordinatesToast()
                        return@FloatingActionButton
                    }
                    onOpenViewer(viewModel.camera.value, filteredObjects)
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.view_in_ar_fill0_wght400_grad0_opsz24),
                    contentDescription = "AR View"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Camera coordinates section
            Text(
                text = "Camera Coordinates",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = coordinatesInput,
                    onValueChange = { coordinatesInput = it },
                    label = { Text("Lat, Lon, Alt") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        onUpdateLocation { objekt ->
                            viewModel.updateCamera(objekt)
                            coordinatesInput = "${objekt.x}, ${objekt.y}, ${objekt.z}"
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.my_location_fill0_wght400_grad0_opsz24),
                        contentDescription = "Get current location"
                    )
                }
                IconButton(
                    onClick = {
                        onUpdateLocation { objekt ->
                            onOpenMap(objekt)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.map_fill0_wght400_grad0_opsz24),
                        contentDescription = "Open map"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Objects list header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Objects",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                SmallFloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add object")
                }
            }

            // Category filter chips
            if (allCategories.isNotEmpty() || hasUncategorized) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasUncategorized) {
                        FilterChip(
                            selected = MainViewModel.UNCATEGORIZED_KEY !in disabledCategories,
                            onClick = { viewModel.toggleCategory(MainViewModel.UNCATEGORIZED_KEY) },
                            label = { Text("Uncategorized") }
                        )
                    }
                    allCategories.forEach { cat ->
                        FilterChip(
                            selected = cat !in disabledCategories,
                            onClick = { viewModel.toggleCategory(cat) },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredObjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No objects added yet.\nTap + above to add your first object.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredObjects, key = { it.id }) { objekt ->
                        ObjectListItem(
                            objekt = objekt,
                            onClick = { objectToEdit = objekt },
                            onLongClick = { objectToDelete = objekt }
                        )
                    }
                }
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        AddOrEditObjectDialog(
            objectToEdit = null,
            existingCategories = existingCategories,
            onDismiss = { showAddDialog = false },
            onConfirm = { coordinates, name, color, osmData, category ->
                viewModel.createObjectFromInput(
                    coordinates, name, color,
                    osmId = osmData?.osmId,
                    polygonJson = osmData?.polygonJson,
                    heightMeters = osmData?.heightMeters ?: 10f,
                    minHeightMeters = osmData?.minHeightMeters ?: 0f,
                    category = category
                )?.let {
                    viewModel.addObject(it)
                }
                showAddDialog = false
            },
            onChooseFromMap = { currentCoordinates, currentName, currentColor ->
                showAddDialog = false
                onPickCoordinatesFromMap(currentCoordinates, currentName, currentColor)
            }
        )
    }

    // Dialog with coordinates picked from map
    if (showDialogWithPickedCoords && pickedCoordinates != null) {
        AddOrEditObjectDialog(
            objectToEdit = null,
            initialCoordinates = pickedCoordinates,
            initialName = pendingName,
            initialColor = pendingColor,
            existingCategories = existingCategories,
            onDismiss = {
                showDialogWithPickedCoords = false
                onClearPickedCoordinates()
            },
            onConfirm = { coordinates, name, color, osmData, category ->
                viewModel.createObjectFromInput(
                    coordinates, name, color,
                    osmId = osmData?.osmId,
                    polygonJson = osmData?.polygonJson,
                    heightMeters = osmData?.heightMeters ?: 10f,
                    minHeightMeters = osmData?.minHeightMeters ?: 0f,
                    category = category
                )?.let {
                    viewModel.addObject(it)
                }
                showDialogWithPickedCoords = false
                onClearPickedCoordinates()
            },
            onChooseFromMap = { currentCoordinates, currentName, currentColor ->
                showDialogWithPickedCoords = false
                onClearPickedCoordinates()
                onPickCoordinatesFromMap(currentCoordinates, currentName, currentColor)
            }
        )
    }

    // Edit dialog
    objectToEdit?.let { objekt ->
        AddOrEditObjectDialog(
            objectToEdit = objekt,
            existingCategories = existingCategories,
            onDismiss = { objectToEdit = null },
            onConfirm = { coordinates, name, color, osmData, category ->
                viewModel.createObjectFromInput(
                    coordinates, name, color,
                    osmId = osmData?.osmId ?: objekt.osmId,
                    polygonJson = osmData?.polygonJson ?: objekt.polygonJson,
                    heightMeters = osmData?.heightMeters ?: objekt.heightMeters,
                    minHeightMeters = osmData?.minHeightMeters ?: objekt.minHeightMeters,
                    category = category
                )?.let {
                    it.id = objekt.id
                    viewModel.updateObject(it)
                }
                objectToEdit = null
            }
        )
    }

    // Delete confirmation dialog
    objectToDelete?.let { objekt ->
        AlertDialog(
            onDismissRequest = { objectToDelete = null },
            title = { Text("Delete object") },
            text = { Text("Delete \"${objekt.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteObject(objekt)
                    objectToDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { objectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // OSM Upload dialog
    if (showUploadDialog) {
        OsmUploadDialog(
            buildings = uploadableBuildings,
            uploadStates = uploadStates,
            isUploading = isUploading,
            uploadDone = uploadDone,
            onUpload = { comment ->
                viewModel.uploadToOsm(uploadableBuildings, comment)
            },
            onDismiss = { showUploadDialog = false }
        )
    }
}
