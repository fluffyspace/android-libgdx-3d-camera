package com.mygdx.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotatingCompass(
    headingDegrees: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(80.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerRadius = size.minDimension / 2f
        val innerRadius = outerRadius * 0.85f

        // Dark semi-transparent background circle
        drawCircle(
            color = Color(0xAA000000.toInt()),
            radius = outerRadius,
            center = Offset(cx, cy)
        )

        // Fixed indicator triangle at top
        val triSize = outerRadius * 0.18f
        val triPath = Path().apply {
            moveTo(cx, cy - outerRadius + 2f)
            lineTo(cx - triSize / 2f, cy - outerRadius - triSize + 2f)
            lineTo(cx + triSize / 2f, cy - outerRadius - triSize + 2f)
            close()
        }
        drawPath(triPath, Color.White, style = Fill)

        // Rotating ring: rotate by -heading so current direction is at top
        rotate(-headingDegrees, pivot = Offset(cx, cy)) {
            // Tick marks every 15 degrees
            for (i in 0 until 360 step 15) {
                val isCardinal = i % 90 == 0
                val isIntercardinal = i % 45 == 0 && !isCardinal
                val tickLen = when {
                    isCardinal -> outerRadius * 0.18f
                    isIntercardinal -> outerRadius * 0.12f
                    else -> outerRadius * 0.08f
                }
                val tickWidth = if (isCardinal) 2.5f else 1.5f
                val angleRad = Math.toRadians(i.toDouble()).toFloat()
                val startR = innerRadius - tickLen
                val endR = innerRadius
                drawLine(
                    color = Color.White,
                    start = Offset(
                        cx + startR * sin(angleRad),
                        cy - startR * cos(angleRad)
                    ),
                    end = Offset(
                        cx + endR * sin(angleRad),
                        cy - endR * cos(angleRad)
                    ),
                    strokeWidth = tickWidth
                )
            }

            // Cardinal and intercardinal labels
            val labels = listOf(
                0f to "N", 45f to "NE", 90f to "E", 135f to "SE",
                180f to "S", 225f to "SW", 270f to "W", 315f to "NW"
            )
            val textRadius = innerRadius * 0.68f
            for ((angleDeg, label) in labels) {
                val isCardinal = angleDeg % 90f == 0f
                val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                val textX = cx + textRadius * sin(angleRad)
                val textY = cy - textRadius * cos(angleRad)
                val textSize = if (isCardinal) outerRadius * 0.32f else outerRadius * 0.22f
                val textColor = if (label == "N") android.graphics.Color.RED
                    else android.graphics.Color.WHITE

                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    textX,
                    textY + textSize / 3f, // vertical centering adjustment
                    android.graphics.Paint().apply {
                        color = textColor
                        this.textSize = textSize
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = isCardinal
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}
