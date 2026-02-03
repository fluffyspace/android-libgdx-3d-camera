package com.mygdx.game.ui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    onCompassClick: () -> Unit,
    onNoDistanceClick: () -> Unit,
    onMoveClick: () -> Unit,
    onMoveVerticalClick: () -> Unit,
    onRotateClick: () -> Unit,
    onScaleClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDiscardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val orientationIndicator = remember {
        OrientationIndicator(context).apply {
            // Initial setup if needed
        }
    }

    // Update orientation indicator when degrees change
    DisposableEffect(viewModel.orientationDegrees) {
        orientationIndicator.degrees = viewModel.orientationDegrees
        orientationIndicator.invalidate()
        onDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Top left - Camera position
        Text(
            text = viewModel.cameraPositionText,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        )

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

        // Right side buttons (compass, no distance)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Compass result text
            Text(
                text = viewModel.compassResult,
                color = Color.White,
                fontSize = 25.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Compass button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.White)
                    .clickable { onCompassClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.explore_fill0_wght400_grad0_opsz24),
                    contentDescription = "Compass",
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // No distance button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(if (viewModel.noDistance) Color.Yellow else Color.White)
                    .clickable { onNoDistanceClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.travel_explore),
                    contentDescription = "Toggle distance",
                    tint = Color.Black
                )
            }
        }

        // Calibration notification
        AnimatedVisibility(
            visible = viewModel.calibrationVisible,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "Hold phone still...",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Bottom left controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // FOV controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FOV: ${viewModel.fov}",
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                Box(
                    modifier = Modifier
                        .background(Color.White)
                        .clickable { onFovUp() }
                        .padding(10.dp)
                ) {
                    Text(
                        text = "FOV +",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White)
                        .clickable { onFovDown() }
                        .padding(10.dp)
                ) {
                    Text(
                        text = "FOV -",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Edit mode buttons
            AnimatedVisibility(visible = viewModel.editModeVisible) {
                Row(modifier = Modifier.padding(start = 20.dp)) {
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
