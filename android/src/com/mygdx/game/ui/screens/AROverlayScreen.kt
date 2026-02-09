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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
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
    onFovUp: () -> Unit,
    onFovDown: () -> Unit,
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
                    onFovUp = onFovUp,
                    onFovDown = onFovDown,
                    onObjectDistanceChanged = onObjectDistanceChanged,
                    onBuildingDistanceChanged = onBuildingDistanceChanged,
                    onNoDistanceObjectsToggle = onNoDistanceObjectsToggle,
                    onNoDistanceBuildingsToggle = onNoDistanceBuildingsToggle
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
                    Row(modifier = Modifier.padding(start = 20.dp)) {
                        if (viewModel.buildingSelected) {
                            EditModeButton(
                                iconRes = R.drawable.altitude,
                                isSelected = viewModel.selectedEditMode == ARViewModel.EditMode.ADJUST_HEIGHT,
                                onClick = onAdjustHeightClick
                            )
                        } else {
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
    }
}

@Composable
private fun SettingsPanel(
    viewModel: ARViewModel,
    onFovUp: () -> Unit,
    onFovDown: () -> Unit,
    onObjectDistanceChanged: (min: Float, max: Float) -> Unit,
    onBuildingDistanceChanged: (min: Float, max: Float) -> Unit,
    onNoDistanceObjectsToggle: (Boolean) -> Unit,
    onNoDistanceBuildingsToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC1A1A1A))
            .padding(12.dp)
    ) {
        // FOV section
        Text("FOV: ${viewModel.fov}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .clickable { onFovDown() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("-", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .clickable { onFovUp() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("+", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Objects distance section
        Text("Objects", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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

        Spacer(modifier = Modifier.height(8.dp))

        // Buildings distance section
        Text("Buildings", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
