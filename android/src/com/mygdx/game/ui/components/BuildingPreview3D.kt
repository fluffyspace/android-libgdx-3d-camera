package com.mygdx.game.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mygdx.game.notbaza.LatLon

@Composable
fun BuildingPreview3D(
    polygon: List<LatLon>,
    heightMeters: Float,
    minHeightMeters: Float,
    colorHue: Float,
    modifier: Modifier = Modifier
) {
    val hsv = floatArrayOf(colorHue, 1f, 1f)
    val colorInt = AndroidColor.HSVToColor(hsv)
    val pR = AndroidColor.red(colorInt) / 255f
    val pG = AndroidColor.green(colorInt) / 255f
    val pB = AndroidColor.blue(colorInt) / 255f

    AndroidView(
        factory = { ctx ->
            BuildingPreviewRenderer(
                context = ctx,
                polygon = polygon,
                heightMeters = heightMeters,
                minHeightMeters = minHeightMeters,
                colorR = pR,
                colorG = pG,
                colorB = pB
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}
