package com.mygdx.game.activities

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mygdx.game.AndroidDeviceCameraController
import com.mygdx.game.MyGdxGame
import com.mygdx.game.OnDrawFrame
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.googlecardboard.HeadTracker
import com.mygdx.game.notbaza.Objekt
import com.mygdx.game.overr.AndroidApplicationOverrided
import com.mygdx.game.ui.screens.AROverlayScreen
import com.mygdx.game.ui.theme.MyGdxGameTheme
import com.mygdx.game.viewmodel.ARViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidLauncher : AndroidApplicationOverrided(), OnDrawFrame, SensorEventListener {
    private var mHeadTracker: HeadTracker? = null
    private var origWidth = 0
    private var origHeight = 0
    var fov: Int = 34
    var gson = Gson()

    private lateinit var arViewModel: ARViewModel
    private lateinit var orientationUpdateJob: Job

    var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    lateinit var cameraControl: AndroidDeviceCameraController

    lateinit var objects: MutableList<Objekt>
    lateinit var game: MyGdxGame
    lateinit var db: AppDatabase

    var calibrationCounter = 0
    var calibrationList = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mHeadTracker = HeadTracker(this)

        arViewModel = ViewModelProvider(this)[ARViewModel::class.java]

        val config = AndroidApplicationConfiguration()
        config.useAccelerometer = false
        config.useCompass = false
        config.useGyroscope = false
        cameraControl = AndroidDeviceCameraController(this@AndroidLauncher)
        config.r = 8
        config.g = 8
        config.b = 8
        config.a = 8
        config.useGL30 = false

        val cameraIntentExtra = intent.getStringExtra("camera")
        val objectsIntentExtra = intent.getStringExtra("objects")
        objects = gson.fromJson(objectsIntentExtra, object : TypeToken<ArrayList<Objekt>>() {}.type)
        convertObjectColors(objects)

        game = MyGdxGame(
            this@AndroidLauncher,
            gson.fromJson(cameraIntentExtra, Objekt::class.java),
            objects,
            cameraControl,
            fov,
            { editModeEnabled ->
                lifecycleScope.launch(Dispatchers.Main) {
                    arViewModel.showEditMode(editModeEnabled)
                }
            },
            { change ->
                lifecycleScope.launch(Dispatchers.Main) {
                    arViewModel.showSaveMenu(change)
                }
            },
            { pos ->
                lifecycleScope.launch(Dispatchers.Main) {
                    arViewModel.updateCameraPosition(pos.x, pos.y, pos.z)
                }
            }
        )
        initialize(game, config)
        initializeLayouts()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()

        orientationUpdateJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(50)
                withContext(Dispatchers.Main) {
                    val degrees = (mHeadTracker?.mTracker?.headingDegrees?.toFloat()
                        ?: 0f) + game.worldRotation + game.worldRotationTmp
                    arViewModel.updateOrientationDegrees(degrees)
                }
            }
        }
    }

    fun startCalibration() {
        calibrationCounter = 0
        calibrationList.clear()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer != null && magnetometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun convertObjectColors(objekti: List<Objekt>) {
        for (objekt in objekti) {
            objekt.libgdxcolor = colorStringToLibgdxColor(Color.valueOf(objekt.color))
        }
    }

    fun initializeLayouts() {
        // Setup ComposeView
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyGdxGameTheme {
                    AROverlayScreen(
                        viewModel = arViewModel,
                        onClose = { finish() },
                        onFovUp = {
                            fov++
                            arViewModel.increaseFov()
                            game.fov = fov
                        },
                        onFovDown = {
                            fov--
                            arViewModel.decreaseFov()
                            game.fov = fov
                        },
                        onCompassClick = {
                            arViewModel.showCalibration(true)
                            startCalibration()
                        },
                        onNoDistanceClick = {
                            val noDistance = arViewModel.toggleNoDistance()
                            game.noDistance = noDistance
                            game.noDistanceChanged()
                        },
                        onMoveClick = {
                            if (game.editMode == MyGdxGame.EditMode.move) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.move
                                arViewModel.selectEditMode(ARViewModel.EditMode.MOVE)
                            }
                        },
                        onMoveVerticalClick = {
                            if (game.editMode == MyGdxGame.EditMode.move_vertical) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.move_vertical
                                arViewModel.selectEditMode(ARViewModel.EditMode.MOVE_VERTICAL)
                            }
                        },
                        onRotateClick = {
                            if (game.editMode == MyGdxGame.EditMode.rotate) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.rotate
                                arViewModel.selectEditMode(ARViewModel.EditMode.ROTATE)
                            }
                        },
                        onScaleClick = {
                            if (game.editMode == MyGdxGame.EditMode.scale) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.scale
                                arViewModel.selectEditMode(ARViewModel.EditMode.SCALE)
                            }
                        },
                        onSaveClick = { saveChanges() },
                        onDiscardClick = { discardChanges() }
                    )
                }
            }
        }

        if (graphics.view is SurfaceView) {
            Log.d("ingo", "is surface view")
            val glView = graphics.view as SurfaceView
            glView.setZOrderOnTop(true)
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }
        graphics.view.keepScreenOn = true
        origWidth = graphics.width
        origHeight = graphics.height

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    fun discardChanges() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                game.updateObjectsCoordinates()
                arViewModel.showSaveMenu(false)
            }
            game.noRender = false
        }
    }

    fun saveChanges() {
        lifecycleScope.launch(Dispatchers.IO) {
            val objektDao = db.objektDao()

            for (objekt in game.objects) {
                if (objekt.changed) {
                    objektDao.update(
                        com.mygdx.game.baza.Objekt(
                            objekt.id,
                            (-(objekt.diffX / game.scalar) + game.camera.x),
                            (-(objekt.diffZ / game.scalar) + game.camera.z),
                            (-(objekt.diffY / game.scalar) + game.camera.y),
                            objekt.name,
                            objekt.size,
                            objekt.rotationX,
                            objekt.rotationY,
                            objekt.rotationZ,
                            objekt.color
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                arViewModel.showSaveMenu(false)
            }
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    companion object {
        private const val TAG = "CameraXApp"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    cameraControl.prepareCamera()
                    it.setSurfaceProvider(cameraControl.previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    fun post(r: Runnable?) {
        handler.post(r!!)
    }

    fun setFixedSize(width: Int, height: Int) {
        if (graphics.view is SurfaceView) {
            val glView = graphics.view as SurfaceView
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setFixedSize(width, height)
        }
    }

    fun restoreFixedSize() {
        if (graphics.view is SurfaceView) {
            val glView = graphics.view as SurfaceView
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setFixedSize(origWidth, origHeight)
        }
    }

    override fun onResume() {
        super.onResume()
        mHeadTracker!!.startTracking()
    }

    override fun onPause() {
        super.onPause()
        orientationUpdateJob.cancel()
        mHeadTracker!!.stopTracking()
        sensorManager?.unregisterListener(this)
    }

    override fun getLastHeadView(): FloatArray {
        val floats1 = FloatArray(16)
        mHeadTracker!!.getLastHeadView(floats1, 0)
        return floats1
    }

    fun colorStringToLibgdxColor(color: Color): com.badlogic.gdx.graphics.Color {
        return com.badlogic.gdx.graphics.Color(color.red(), color.green(), color.blue(), color.alpha())
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }
        updateOrientation()
    }

    private fun updateOrientation() {
        calibrationCounter++
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        var azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

        if (calibrationCounter % 200 == 0) {
            arViewModel.updateCompassResult(azimuthInDegrees.toInt().toString())
        }

        if (calibrationCounter < 1000) return
        calibrationList.add(azimuthInDegrees.toInt())

        println(azimuthInDegrees)

        if (calibrationCounter > 5000) {
            arViewModel.showCalibration(false)
            val averageAzimuth = (calibrationList.sum() / calibrationList.size.toDouble())
            mHeadTracker?.mTracker?.headingDegrees = averageAzimuth
            println("Average azimuth: $averageAzimuth, $calibrationList")
            sensorManager?.unregisterListener(this)
            arViewModel.updateCompassResult(
                "${averageAzimuth.toInt()}\n+-${calibrationList.max() - calibrationList.min()}"
            )
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Not implemented
    }
}
