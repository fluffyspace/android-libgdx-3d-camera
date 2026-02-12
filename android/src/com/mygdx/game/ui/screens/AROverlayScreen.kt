package com.mygdx.game.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mygdx.game.OrientationIndicator
import com.mygdx.game.R
import com.mygdx.game.viewmodel.ARViewModel

@Composable
fun AROverlayScreen(
    viewModel: ARViewModel,
    onClose: () -> Unit,
    onObjectDistanceChanged: (min: Float, max: Float) -> Unit,
    onBuildingDistanceChanged: (min: Float, max: Float) -> Unit,
    onNoDistanceObjectsToggle: (Boolean) -> Unit,
    onNoDistanceBuildingsToggle: (Boolean) -> Unit,
    onMoveClick: () -> Unit,
    onMoveVerticalClick: () -> Unit,
    onRotateClick: () -> Unit,
    onScaleClick: () -> Unit,
    onAdjustHeightClick: () -> Unit = {},
    onSaveClick: () -> Unit,
    onDiscardClick: () -> Unit,
    onToggleHidden: (index: Int) -> Unit = {},
    onEditObject: (index: Int) -> Unit = {},
    onAddVertex: () -> Unit = {},
    onUndoVertex: () -> Unit = {},
    onClosePolygon: () -> Unit = {},
    onVertexHeightChanged: (Float) -> Unit = {},
    onCancelVertices: () -> Unit = {},
    onSaveVertices: () -> Unit = {},
    onObjectsOnTopToggle: (Boolean) -> Unit = {},
    onFloorGridToggle: (Boolean) -> Unit = {},
    onAutoAdjustAltitude: () -> Unit = {},
    onHeightOffsetChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val orientationIndicator = remember {
        OrientationIndicator(context)
    }

    // Update orientation indicator when degrees change
    DisposableEffect(viewModel.orientationDegrees) {
        orientationIndicator.degrees = viewModel.orientationDegrees
        orientationIndicator.invalidate()
        onDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Top center - Orientation indicator
        AndroidView(
            factory = { orientationIndicator },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .width(300.dp)
                .height(60.dp),
            update = { view ->
                view.degrees = viewModel.orientationDegrees
                view.invalidate()
            }
        )

        // Top right - Close button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(50.dp)
                .background(Color.White)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.close),
                contentDescription = "Close",
                tint = Color.Black
            )
        }

        // Crosshair at center when vertices editor tab is active
        if (viewModel.editModeVisible && !viewModel.buildingSelected &&
            viewModel.selectedEditTab == ARViewModel.EditTab.VERTICES_EDITOR) {
            Icon(
                painter = painterResource(id = R.drawable.crosshair),
                contentDescription = "Crosshair",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        // Bottom left - Settings button + edit mode buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Settings panel (expands upward from button)
            AnimatedVisibility(
                visible = viewModel.settingsExpanded,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                SettingsPanel(
                    viewModel = viewModel,
                    onObjectDistanceChanged = onObjectDistanceChanged,
                    onBuildingDistanceChanged = onBuildingDistanceChanged,
                    onNoDistanceObjectsToggle = onNoDistanceObjectsToggle,
                    onNoDistanceBuildingsToggle = onNoDistanceBuildingsToggle,
                    onObjectsOnTopToggle = onObjectsOnTopToggle,
                    onFloorGridToggle = onFloorGridToggle,
                    onAutoAdjustAltitude = onAutoAdjustAltitude,
                    onHeightOffsetChanged = onHeightOffsetChanged
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                // Settings button
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            if (viewModel.settingsExpanded) Color.Yellow else Color.White
                        )
                        .clickable { viewModel.toggleSettingsExpanded() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.Black
                    )
                }

                // Edit mode buttons
                AnimatedVisibility(visible = viewModel.editModeVisible) {
                    if (viewModel.buildingSelected) {
                        Row(modifier = Modifier.padding(start = 20.dp)) {
                            EditModeButton(
                                iconRes = R.drawable.altitude,
                                isSelected = viewModel.selectedEditMode == ARViewModel.EditMode.ADJUST_HEIGHT,
                                onClick = onAdjustHeightClick
                            )
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 20.dp)) {
                            // Tab bar
                            Row {
                                EditTabButton(
                                    text = "Object",
                                    isSelected = viewModel.selectedEditTab == ARViewModel.EditTab.OBJECT_EDITOR,
                                    onClick = { viewModel.selectEditTab(ARViewModel.EditTab.OBJECT_EDITOR) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                EditTabButton(
                                    text = "Vertices",
                                    isSelected = viewModel.selectedEditTab == ARViewModel.EditTab.VERTICES_EDITOR,
                                    onClick = { viewModel.selectEditTab(ARViewModel.EditTab.VERTICES_EDITOR) }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Tab content
                            when (viewModel.selectedEditTab) {
                                ARViewModel.EditTab.OBJECT_EDITOR -> {
                                    Row {
                                        EditModeButton(
                                            iconRes = R.drawable.in_plane_move,
                                            isSelected = viewModel.selectedEditMode == ARViewModel.EditMode.MOVE,
                                            onClick = onMoveClick
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        EditModeButton(
                                            iconRes = R.drawable.altitude,
                                            isSelected = viewModel.selectedEditMode == ARViewModel.EditMode.MOVE_VERTICAL,
                                            onClick = onMoveVerticalClick
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        EditModeButton(
                                            iconRes = R.drawable.rotate,
                                            isSelected = viewModel.selectedEditMode == ARViewModel.EditMode.ROTATE,
                                            onClick = onRotateClick
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        EditModeButton(
                                            iconRes = R.drawable.scale,
                                            isSelected = viewModel.selectedEditMode == ARViewModel.EditMode.SCALE,
                                            onClick = onScaleClick
                                        )
                                    }
                                }
                                ARViewModel.EditTab.VERTICES_EDITOR -> {
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xCC1A1A1A))
                                            .padding(8.dp)
                                    ) {
                                        // Status text
                                        if (viewModel.vertexHitStatus.isNotEmpty()) {
                                            Text(
                                                viewModel.vertexHitStatus,
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        // Vertex count
                                        Text(
                                            "Vertices: ${viewModel.vertexCount}" +
                                                if (viewModel.vertexPolygonClosed) " (closed)" else "",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        // Add / Undo / Close buttons
                                        Row {
                                            VertexButton(
                                                text = "+ Add",
                                                enabled = !viewModel.vertexPolygonClosed,
                                                onClick = onAddVertex
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            VertexButton(
                                                text = "Undo",
                                                enabled = viewModel.vertexCount > 0 && !viewModel.vertexPolygonClosed,
                                                onClick = onUndoVertex
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            VertexButton(
                                                text = "Close",
                                                enabled = viewModel.vertexCount >= 3 && !viewModel.vertexPolygonClosed,
                                                onClick = onClosePolygon
                                            )
                                        }
                                        // Height slider when polygon is closed
                                        if (viewModel.vertexPolygonClosed) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Height: ${viewModel.vertexExtrudeHeight.toInt()}m",
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                            Slider(
                                                value = viewModel.vertexExtrudeHeight,
                                                onValueChange = { onVertexHeightChanged(it) },
                                                valueRange = 1f..100f,
                                                modifier = Modifier.width(200.dp),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color.White,
                                                    activeTrackColor = Color.White,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        // Cancel / Save
                                        Row {
                                            VertexButton(
                                                text = "Cancel",
                                                color = Color(0xFFFF6666),
                                                onClick = onCancelVertices
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            VertexButton(
                                                text = "Save",
                                                color = Color(0xFF66FF66),
                                                enabled = viewModel.vertexPolygonClosed,
                                                onClick = onSaveVertices
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Save menu buttons
                AnimatedVisibility(visible = viewModel.saveMenuVisible) {
                    Row(modifier = Modifier.padding(start = 20.dp)) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White)
                                .clickable { onSaveClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.save),
                                contentDescription = "Save",
                                tint = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White)
                                .clickable { onDiscardClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.delete_history),
                                contentDescription = "Discard",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }

        // Bottom center - Altitude HUD
        if (viewModel.floorGridEnabled || viewModel.altitudeAutoAdjusted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val hudText = if (viewModel.altitudeAutoAdjusted) {
                    "Alt: ${"%.1f".format(viewModel.computeAltitude())}m ASL"
                } else {
                    "Floor: ${"%.1f".format(viewModel.floorHeightLive)}m below phone"
                }
                Text(
                    hudText,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        // Bottom right - Object list button + panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            // Object list panel (expands upward from button)
            AnimatedVisibility(
                visible = viewModel.objectListExpanded,
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                ObjectListPanel(
                    objects = viewModel.objectList,
                    onToggleHidden = onToggleHidden,
                    onEdit = onEditObject
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (viewModel.objectListExpanded) Color.Yellow else Color.White
                    )
                    .clickable { viewModel.toggleObjectList() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.list),
                    contentDescription = "Object list",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ObjectListPanel(
    objects: List<com.mygdx.game.viewmodel.ARObjectInfo>,
    onToggleHidden: (index: Int) -> Unit,
    onEdit: (index: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC1A1A1A))
            .padding(8.dp)
    ) {
        Text(
            "Objects",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (objects.isEmpty()) {
            Text(
                "No objects",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(objects) { obj ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Eye toggle
                        IconButton(
                            onClick = { onToggleHidden(obj.index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (obj.hidden) R.drawable.visibility_off
                                    else R.drawable.visibility
                                ),
                                contentDescription = if (obj.hidden) "Show" else "Hide",
                                tint = if (obj.hidden) Color.White.copy(alpha = 0.3f)
                                else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Name + distance
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = obj.name.ifBlank { "Object #${obj.id}" },
                                color = if (obj.hidden) Color.White.copy(alpha = 0.3f)
                                else Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "%.0f m".format(obj.distance),
                                color = if (obj.hidden) Color.White.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }

                        // Edit button
                        IconButton(
                            onClick = { onEdit(obj.index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.edit),
                                contentDescription = "Edit",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (obj != objects.last()) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    viewModel: ARViewModel,
    onObjectDistanceChanged: (min: Float, max: Float) -> Unit,
    onBuildingDistanceChanged: (min: Float, max: Float) -> Unit,
    onNoDistanceObjectsToggle: (Boolean) -> Unit,
    onNoDistanceBuildingsToggle: (Boolean) -> Unit,
    onObjectsOnTopToggle: (Boolean) -> Unit,
    onFloorGridToggle: (Boolean) -> Unit,
    onAutoAdjustAltitude: () -> Unit,
    onHeightOffsetChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .heightIn(max = 400.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC1A1A1A))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Objects distance section
        Text("Objects (${viewModel.personalObjectCount} loaded)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${viewModel.minDistanceObjects.toInt()}–${viewModel.maxDistanceObjects.toInt()}m",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.width(70.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("No limit", color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = viewModel.noDistanceObjects,
                onCheckedChange = { onNoDistanceObjectsToggle(it) },
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Yellow,
                    checkedTrackColor = Color.Yellow.copy(alpha = 0.5f)
                )
            )
        }
        if (!viewModel.noDistanceObjects) {
            RangeSlider(
                value = viewModel.minDistanceObjects..viewModel.maxDistanceObjects,
                onValueChange = { range ->
                    onObjectDistanceChanged(range.start, range.endInclusive)
                },
                valueRange = 0f..1000f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Objects on top", color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = viewModel.objectsOnTop,
                onCheckedChange = { onObjectsOnTopToggle(it) },
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Yellow,
                    checkedTrackColor = Color.Yellow.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Buildings distance section
        Text("Buildings (${viewModel.nearbyBuildingCount} fetched)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (viewModel.buildingFetchError != null) {
            Text(
                viewModel.buildingFetchError!!,
                color = Color(0xFFFF6666),
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${viewModel.minDistanceBuildings.toInt()}–${viewModel.maxDistanceBuildings.toInt()}m",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.width(70.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("No limit", color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = viewModel.noDistanceBuildings,
                onCheckedChange = { onNoDistanceBuildingsToggle(it) },
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Yellow,
                    checkedTrackColor = Color.Yellow.copy(alpha = 0.5f)
                )
            )
        }
        if (!viewModel.noDistanceBuildings) {
            RangeSlider(
                value = viewModel.minDistanceBuildings..viewModel.maxDistanceBuildings,
                onValueChange = { range ->
                    onBuildingDistanceChanged(range.start, range.endInclusive)
                },
                valueRange = 0f..1000f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

        // Floor Grid section
        Text("Floor Grid", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show detected floor", color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = viewModel.floorGridEnabled,
                onCheckedChange = { onFloorGridToggle(it) },
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Yellow,
                    checkedTrackColor = Color.Yellow.copy(alpha = 0.5f)
                )
            )
        }
        if (viewModel.floorGridEnabled && viewModel.floorHeightLive > 0f) {
            Text(
                "Phone height: ${"%.1f".format(viewModel.floorHeightLive)}m above floor",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
            if (viewModel.altitudeAutoAdjusted) {
                Text(
                    "Floor at ${"%.1f".format(viewModel.groundElevation + viewModel.heightOffset)}m ASL",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

        // Altitude section
        Text("Altitude", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        if (viewModel.isAutoAdjusting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adjusting...", color = Color.White, fontSize = 12.sp)
            }
        } else if (viewModel.altitudeAutoAdjusted) {
            Button(
                onClick = onAutoAdjustAltitude,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Altitude set (${"%.1f".format(viewModel.computeAltitude())}m)",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        } else {
            Button(
                onClick = onAutoAdjustAltitude,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto-adjust altitude", color = Color.White, fontSize = 12.sp)
            }
        }
        if (viewModel.autoAdjustError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                viewModel.autoAdjustError!!,
                color = Color(0xFFFF6666),
                fontSize = 11.sp
            )
        }
        if (viewModel.altitudeAutoAdjusted) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Floor offset: ${viewModel.heightOffset.toInt()}m",
                color = Color.White,
                fontSize = 12.sp
            )
            Slider(
                value = viewModel.heightOffset,
                onValueChange = { onHeightOffsetChanged(it) },
                valueRange = -20f..20f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
            Text(
                "Camera altitude: ${"%.1f".format(viewModel.computeAltitude())}m ASL",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun EditTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.3f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EditModeButton(
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .background(if (isSelected) Color.Yellow else Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.Black
        )
    }
}

@Composable
private fun VertexButton(
    text: String,
    enabled: Boolean = true,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) color else Color.White.copy(alpha = 0.3f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
