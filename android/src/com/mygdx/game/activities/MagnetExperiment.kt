package com.mygdx.game.activities

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.mygdx.game.ui.screens.MagnetExperimentScreen
import com.mygdx.game.ui.theme.MyGdxGameTheme

class MagnetExperiment : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var azimuth by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        setContent {
            MyGdxGameTheme {
                MagnetExperimentScreen(azimuth = azimuth)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor == rotationVector) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val calculatedAzimuth = (Math.toDegrees(
                SensorManager.getOrientation(rotationMatrix, orientationAngles)[0].toDouble()
            ) + 360).toInt() % 360
            azimuth = calculatedAzimuth
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not implemented
    }
}
