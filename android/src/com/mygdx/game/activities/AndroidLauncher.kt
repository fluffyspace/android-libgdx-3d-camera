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
import com.badlogic.gdx.Gdx
import com.mygdx.game.arcore.ARCoreBackgroundRenderer
import com.mygdx.game.arcore.ARCoreSessionManager
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.notbaza.Objekt
import com.mygdx.game.overr.AndroidApplicationOverrided
import com.mygdx.game.ui.screens.AROverlayScreen
import com.mygdx.game.ui.theme.MyGdxGameTheme
import com.mygdx.game.network.BuildingCache
import com.mygdx.game.network.OverpassClient
import com.mygdx.game.viewmodel.ARViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidLauncher : AndroidApplicationOverrided(), OnDrawFrame, SensorEventListener {
    // ARCore session manager (replaces HeadTracker)
    private lateinit var arCoreSessionManager: ARCoreSessionManager
    private lateinit var arCoreBackgroundRenderer: ARCoreBackgroundRenderer
    private var arCoreInitialized = false

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
                initializeARCore()
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

        // Initialize ARCore session manager (replaces HeadTracker)
        arCoreSessionManager = ARCoreSessionManager(this)
        arCoreBackgroundRenderer = ARCoreBackgroundRenderer()

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

        // Fetch OSM building footprints asynchronously
        val cameraObj = gson.fromJson(cameraIntentExtra, Objekt::class.java)
        val buildingCache = BuildingCache(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val lat = cameraObj.x.toDouble()
                val lon = cameraObj.y.toDouble()
                val buildings = buildingCache.getCached(lat, lon)
                    ?: OverpassClient.fetchBuildings(lat, lon).also {
                        buildingCache.putCache(lat, lon, it)
                    }
                if (buildings.isNotEmpty()) {
                    Gdx.app.postRunnable { game.setBuildings(buildings) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()

        orientationUpdateJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(50)
                withContext(Dispatchers.Main) {
                    val degrees = arCoreSessionManager.headingDegrees.toFloat() + game.worldRotation + game.worldRotationTmp
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
            // Don't use setZOrderOnTop - it would render GL on top of ComposeView buttons
            // ARCore renders camera background within GL context, so transparency is not needed
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }
        graphics.view.keepScreenOn = true
        origWidth = graphics.width
        origHeight = graphics.height

        if (allPermissionsGranted()) {
            initializeARCore()
        } else {
            requestPermissions()
        }
    }

    /**
     * Initialize ARCore session and background renderer.
     * This replaces the CameraX setup since ARCore handles the camera.
     */
    private fun initializeARCore() {
        if (arCoreSessionManager.checkAvailability()) {
            if (arCoreSessionManager.createSession()) {
                Log.d(TAG, "ARCore session created successfully")
                arCoreInitialized = true

                // Set initial display geometry immediately
                if (graphics.view is SurfaceView) {
                    val glView = graphics.view as SurfaceView
                    val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        display?.rotation ?: 0
                    } else {
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay.rotation
                    }
                    arCoreSessionManager.setDisplayGeometry(rotation, glView.width, glView.height)
                }

                // Set up surface callback to handle display geometry changes
                if (graphics.view is SurfaceView) {
                    val glView = graphics.view as SurfaceView
                    glView.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Initialization is now done lazily in getLastHeadView() on GL thread
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            @Suppress("DEPRECATION")
                            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                display?.rotation ?: 0
                            } else {
                                windowManager.defaultDisplay.rotation
                            }
                            arCoreSessionManager.setDisplayGeometry(rotation, width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // Cleanup handled in onDestroy
                        }
                    })
                }
            } else {
                Log.e(TAG, "Failed to create ARCore session")
                Toast.makeText(this, "ARCore initialization failed", Toast.LENGTH_LONG).show()
            }
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

        // Check ARCore availability and resume session
        if (arCoreSessionManager.checkAvailability()) {
            if (!arCoreInitialized) {
                initializeARCore()
            }
            arCoreSessionManager.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationUpdateJob.cancel()
        arCoreSessionManager.pause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        arCoreBackgroundRenderer.dispose()
        arCoreSessionManager.close()
    }

    override fun getLastHeadView(): FloatArray {
        // Initialize ARCore background renderer on first call (on GL thread)
        // This ensures OpenGL calls happen on the correct thread
        if (!arCoreBackgroundRenderer.isInitialized && arCoreInitialized) {
            arCoreBackgroundRenderer.initialize()
            arCoreSessionManager.setCameraTextureName(arCoreBackgroundRenderer.textureId)
        }

        // Update ARCore session and get the view matrix
        arCoreSessionManager.update()

        // Draw ARCore camera background (this needs to be done on GL thread)
        arCoreSessionManager.frame?.let { frame ->
            arCoreBackgroundRenderer.draw(frame)
        }

        return arCoreSessionManager.getViewMatrix()
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
            arCoreSessionManager.headingDegrees = averageAzimuth
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
