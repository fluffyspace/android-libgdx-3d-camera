package com.mygdx.game.ui.dialogs

import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mygdx.game.notbaza.Building
import com.mygdx.game.ui.components.BuildingPreviewRenderer
import kotlinx.coroutines.launch

data class BuildingWithDistance(
    val building: Building,
    val distanceMeters: Double
)

@Composable
fun BuildingPickerDialog(
    buildings: List<BuildingWithDistance>,
    colorHue: Float,
    onSelect: (Building) -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { buildings.size })
    val coroutineScope = rememberCoroutineScope()

    val hsv = floatArrayOf(colorHue, 1f, 1f)
    val colorInt = AndroidColor.HSVToColor(hsv)
    val colorR = AndroidColor.red(colorInt) / 255f
    val colorG = AndroidColor.green(colorInt) / 255f
    val colorB = AndroidColor.blue(colorInt) / 255f

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(480.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Title + page indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select building",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} / ${buildings.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pager with arrows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left arrow
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous"
                        )
                    }

                    // Pager content
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) { page ->
                        val bwd = buildings[page]
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 3D preview - only render the settled page
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                if (pagerState.settledPage == page) {
                                    key(page) {
                                        AndroidView(
                                            factory = { ctx ->
                                                BuildingPreviewRenderer(
                                                    context = ctx,
                                                    polygon = bwd.building.polygon,
                                                    heightMeters = bwd.building.heightMeters,
                                                    minHeightMeters = bwd.building.minHeightMeters,
                                                    colorR = colorR,
                                                    colorG = colorG,
                                                    colorB = colorB
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Swipe to view",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Building info
                            Text(
                                text = "OSM #${bwd.building.id}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Height: ${String.format("%.1f", bwd.building.heightMeters)}m" +
                                        if (bwd.building.minHeightMeters > 0f)
                                            " (min ${String.format("%.1f", bwd.building.minHeightMeters)}m)"
                                        else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Distance: ${String.format("%.0f", bwd.distanceMeters)}m from pin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Right arrow
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (pagerState.currentPage < buildings.size - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage < buildings.size - 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom buttons
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
                            val current = buildings[pagerState.currentPage]
                            onSelect(current.building)
                        }
                    ) {
                        Text("Select this building")
                    }
                }
            }
        }
    }
}
