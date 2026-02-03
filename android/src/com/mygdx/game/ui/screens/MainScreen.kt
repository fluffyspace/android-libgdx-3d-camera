package com.mygdx.game.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.mygdx.game.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenMap: (Objekt) -> Unit,
    onOpenViewer: (camera: Objekt, objects: List<Objekt>) -> Unit,
    onUpdateLocation: ((Objekt) -> Unit) -> Unit,
    onShowInvalidCoordinatesToast: () -> Unit
) {
    val objects by viewModel.objects.collectAsState()
    val camera by viewModel.camera.collectAsState()
    val cameraText by viewModel.cameraCoordinatesText.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var objectToEdit by remember { mutableStateOf<Objekt?>(null) }
    var coordinatesInput by remember(cameraText) { mutableStateOf(cameraText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR Objects") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add object")
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
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onUpdateLocation { objekt ->
                            viewModel.updateCamera(objekt)
                            coordinatesInput = "${objekt.x}, ${objekt.y}, ${objekt.z}"
                        }
                    }
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Get current location")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        onUpdateLocation { objekt ->
                            onOpenMap(objekt)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.map_fill0_wght400_grad0_opsz24),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Map")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val success = viewModel.setCameraFromText(coordinatesInput)
                        if (!success) {
                            onShowInvalidCoordinatesToast()
                            return@Button
                        }
                        onOpenViewer(viewModel.camera.value, objects)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AR View")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Objects list
            Text(
                text = "Objects",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (objects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No objects added yet.\nTap + to add your first object.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(objects, key = { it.id }) { objekt ->
                        ObjectListItem(
                            objekt = objekt,
                            onClick = { objectToEdit = objekt },
                            onLongClick = { viewModel.deleteObject(objekt) }
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
            onDismiss = { showAddDialog = false },
            onConfirm = { coordinates, name, color ->
                viewModel.createObjectFromInput(coordinates, name, color)?.let {
                    viewModel.addObject(it)
                }
                showAddDialog = false
            }
        )
    }

    // Edit dialog
    objectToEdit?.let { objekt ->
        AddOrEditObjectDialog(
            objectToEdit = objekt,
            onDismiss = { objectToEdit = null },
            onConfirm = { coordinates, name, color ->
                viewModel.createObjectFromInput(coordinates, name, color)?.let {
                    it.id = objekt.id
                    viewModel.updateObject(it)
                }
                objectToEdit = null
            }
        )
    }
}
