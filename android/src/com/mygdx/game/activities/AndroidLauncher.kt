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
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mygdx.game.AndroidDeviceCameraController
import com.mygdx.game.MyGdxGame
import com.mygdx.game.OnDrawFrame
import com.mygdx.game.OrientationIndicator
import com.mygdx.game.R
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.googlecardboard.HeadTracker
import com.mygdx.game.notbaza.Objekt
import com.mygdx.game.overr.AndroidApplicationOverrided
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.StringBuilder
import kotlin.math.abs
import kotlin.math.roundToInt

class AndroidLauncher : AndroidApplicationOverrided(), OnDrawFrame, SensorEventListener {
    private var mHeadTracker: HeadTracker? = null
    private var origWidth = 0
    private var origHeight = 0
    var fov: Int = 34
    var gson = Gson()
    lateinit var camHeight: TextView
    lateinit var fovTv: TextView
    lateinit var compassResult: TextView
    lateinit var travelExplore: ImageView
    lateinit var compassButton: ImageView
    lateinit var calibrationNotification: TextView
    //lateinit var seekbar_orientationekf: LinearProgressIndicator
    lateinit var orientation_indicator: OrientationIndicator
    lateinit var orientationSeekbarJob: Job
    lateinit var moveButton: ImageView
    lateinit var moveVerticalButton: ImageView
    lateinit var rotateButton: ImageView
    lateinit var scaleButton: ImageView
    var transformButtons: List<ImageView> = listOf()

    var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    lateinit var cameraControl: AndroidDeviceCameraController
    lateinit var editModeLayout: LinearLayout
    lateinit var saveMenuLayout: LinearLayout

    lateinit var objects: MutableList<Objekt>
    lateinit var game: MyGdxGame
    lateinit var db: AppDatabase

    var calibrationCounter = 0
    var calibrationList = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mHeadTracker = HeadTracker(this)

        lateinit var config: AndroidApplicationConfiguration
        config = AndroidApplicationConfiguration()
        config.useAccelerometer = false
        config.useCompass = false
        config.useGyroscope = false
        cameraControl = AndroidDeviceCameraController(this@AndroidLauncher)
        //config.useGL30 = true;
        // we need to change the default pixel format - since it does not include an alpha channel
        // we need the alpha channel so the camera preview will be seen behind the GL scene
        config.r = 8
        config.g = 8
        config.b = 8
        config.a = 8
        config.useGL30 = false

        val intent = intent
        val cameraIntentExtra = intent.getStringExtra("camera")
        val objectsIntentExtra = intent.getStringExtra("objects")
        objects = gson.fromJson(objectsIntentExtra, object : TypeToken<ArrayList<Objekt>>() {}.type)
        convertObjectColors(objects)

        game = MyGdxGame(
            this@AndroidLauncher,
            gson.fromJson(cameraIntentExtra, Objekt::class.java),
            objects,
            cameraControl,
            fov, { editModeEnabled ->
                lifecycleScope.launch {
                    withContext(Dispatchers.Main){
                        editModeLayout.visibility = if(editModeEnabled) View.VISIBLE else View.GONE
                    }
                }
            },
            { change ->
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        saveMenuLayout.visibility = if (change) View.VISIBLE else View.GONE
                    }
                }
            },
            { pos ->
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        camHeight.text = "Camera: %.2f, %.2f, %.2f".format(pos.x, pos.y, pos.z)
                    }
                }
            }
        )
        initialize(game, config)
        initializeLayouts()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()
        orientationSeekbarJob = lifecycleScope.launch(Dispatchers.IO){
            while(true) {
                delay(50)
                withContext(Dispatchers.Main) {
                    //seekbar_orientationekf.progress = mHeadTracker?.mTracker?.headingDegrees?.toInt() ?: 0
                    orientation_indicator.degrees = (mHeadTracker?.mTracker?.headingDegrees?.toFloat() ?: 0f) + game.worldRotation + game.worldRotationTmp
                    orientation_indicator.invalidate()
                }
            }
        }
    }

    fun startCalibration(){
        calibrationCounter = 0
        calibrationList.clear()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accelerometer != null && magnetometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // Handle sensors not available
        }
    }

    fun convertObjectColors(objekti: List<Objekt>){
        for(objekt in objekti){
            objekt.libgdxcolor = colorStringToLibgdxColor(Color.valueOf(objekt.color))
        }
    }

    fun initializeLayouts(){
        fovTv = this.fieldOfViewLayout.findViewById(R.id.fovValueTv)
        camHeight = this.fieldOfViewLayout.findViewById(R.id.cam_height)
        calibrationNotification = this.fieldOfViewLayout.findViewById(R.id.calibration_notification)
        //seekbar_orientationekf = this.fieldOfViewLayout.findViewById(R.id.seekbar_orientationekf)
        orientation_indicator = this.fieldOfViewLayout.findViewById(R.id.orientation_indicator)
        compassButton = this.fieldOfViewLayout.findViewById(R.id.compass)
        compassButton.setOnClickListener {
            calibrationNotification.visibility = View.VISIBLE
            startCalibration()
        }
        compassResult = this.fieldOfViewLayout.findViewById(R.id.compass_result)
        travelExplore = this.fieldOfViewLayout.findViewById(R.id.no_distance)
        travelExplore.setOnClickListener {
            game.noDistance = !game.noDistance
            game.noDistanceChanged()
            it.setBackgroundColor(if(game.noDistance) Color.YELLOW else Color.WHITE)
        }
        this.fieldOfViewLayout.findViewById<TextView>(R.id.fovUp).setOnClickListener {
            fov++
            fovTv.text = "FOV: $fov"
            game.fov = fov
        }
        this.fieldOfViewLayout.findViewById<TextView>(R.id.fovDown).setOnClickListener {
            fov--
            fovTv.text = "FOV: $fov"
            game.fov = fov
        }
        fovTv.text = "FOV: $fov"
        this.fieldOfViewLayout.findViewById<ImageView>(R.id.close).setOnClickListener {
            finish()
        }
        saveMenuLayout = this.fieldOfViewLayout.findViewById<LinearLayout>(R.id.save_menu)
        editModeLayout = this.fieldOfViewLayout.findViewById<LinearLayout>(R.id.edit_mode)
        saveMenuLayout.visibility = View.GONE
        editModeLayout.visibility = View.GONE
        moveButton = editModeLayout.findViewById<ImageView>(R.id.move)
        moveVerticalButton = editModeLayout.findViewById<ImageView>(R.id.move_up_down)
        rotateButton = editModeLayout.findViewById<ImageView>(R.id.rotate)
        scaleButton = editModeLayout.findViewById<ImageView>(R.id.scale)
        transformButtons = listOf(moveButton, moveVerticalButton, rotateButton, scaleButton)
        moveButton.setOnClickListener {
            if(game.editMode == MyGdxGame.EditMode.move){
                unselectTransformButtons()
                return@setOnClickListener
            }
            game.editMode = MyGdxGame.EditMode.move
            pickedTransformButton(it)
        }
        moveVerticalButton.setOnClickListener {
            if(game.editMode == MyGdxGame.EditMode.move_vertical){
                unselectTransformButtons()
                return@setOnClickListener
            }
            game.editMode = MyGdxGame.EditMode.move_vertical
            pickedTransformButton(it)
        }
        rotateButton.setOnClickListener {
            if(game.editMode == MyGdxGame.EditMode.rotate){
                unselectTransformButtons()
                return@setOnClickListener
            }
            game.editMode = MyGdxGame.EditMode.rotate
            pickedTransformButton(it)
        }
        scaleButton.setOnClickListener {
            if(game.editMode == MyGdxGame.EditMode.scale){
                unselectTransformButtons()
                return@setOnClickListener
            }
            game.editMode = MyGdxGame.EditMode.scale
            pickedTransformButton(it)
        }
        saveMenuLayout.findViewById<ImageView>(R.id.save).setOnClickListener {
            saveChanges()
        }
        saveMenuLayout.findViewById<ImageView>(R.id.discard_changes).setOnClickListener {
            discardChanges()
        }
        if(this.frameLayout is FrameLayout){
            Log.d("ingo", "framelayout")
            Log.d("ingo", this.frameLayout.childCount.toString())
        }
        if (graphics.view is SurfaceView) {
            Log.d("ingo", "is surface view")
            val glView = graphics.view as SurfaceView
            // force alpha channel - I'm not sure we need this as the GL surface is already using alpha channel
            glView.setZOrderOnTop(true)
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
            //getHolder().setType(  );
        }
        // we don't want the screen to turn off during the long image saving process
        graphics.view.keepScreenOn = true
        // keep the original screen size
        origWidth = graphics.width
        origHeight = graphics.height

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    fun discardChanges(){
        lifecycleScope.launch(Dispatchers.IO) {
            /*val objektDao = db.objektDao()
            val objekti = objektDao.getAll().toMutableList()

            val tmpObjects = gson.fromJson<ArrayList<Objekt>>(gson.toJson(objekti), object : TypeToken<ArrayList<Objekt>>() {}.type)
            convertObjectColors(tmpObjects)
            game.noRender = true
            delay(100)*/

            withContext(Dispatchers.Main){
                //objects = tmpObjects
                game.updateObjectsCoordinates()
                saveMenuLayout.visibility = View.GONE
            }
            game.noRender = false
        }
    }

    fun saveChanges(){
        lifecycleScope.launch(Dispatchers.IO) {
            val objektDao = db.objektDao()

            for(objekt in game.objects){
                if(objekt.changed){
                    objektDao.update(
                        com.mygdx.game.baza.Objekt(
                            objekt.id,
                            (-(objekt.diffX/game.scalar)+game.camera.x),
                            (-(objekt.diffZ/game.scalar)+game.camera.z),
                            (-(objekt.diffY/game.scalar)+game.camera.y),
                            objekt.name,
                            objekt.size,
                            objekt.rotationX,
                            objekt.rotationY,
                            objekt.rotationZ,
                            objekt.color)
                    )
                }
            }
            withContext(Dispatchers.Main){
                saveMenuLayout.visibility = View.GONE
            }
        }
    }

    fun unselectTransformButtons(){
        for(button in transformButtons){
            button.setBackgroundColor(Color.WHITE)
            game.editMode = null
        }
    }

    fun pickedTransformButton(view: View){
        for(button in transformButtons){
            button.setBackgroundColor(if(view != button as View) Color.WHITE else Color.YELLOW)
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    cameraControl.prepareCamera()
                    it.setSurfaceProvider(cameraControl.previewView.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview)

            } catch(exc: Exception) {
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
        orientationSeekbarJob.cancel()
        mHeadTracker!!.stopTracking()
        sensorManager?.unregisterListener(this);
    }

    override fun getLastHeadView(): FloatArray {
        val floats1 = FloatArray(16)
        mHeadTracker!!.getLastHeadView(floats1, 0)
        return floats1
    }

    fun colorStringToLibgdxColor(color: Color): com.badlogic.gdx.graphics.Color{
        return com.badlogic.gdx.graphics.Color(color.red(), color.green(), color.blue(), color.alpha())
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size);
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size);
        }

        updateOrientation();
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

        // OrientationAngles[0] contains the azimuth (yaw) angle in radians.
        // Convert radians to degrees
        var azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

        // Ensure azimuthInDegrees is between 0 and 360
        azimuthInDegrees = azimuthInDegrees//((azimuthInDegrees + 360) % 360)

        if(calibrationCounter % 200 == 0) {
            compassResult.text = azimuthInDegrees.toInt().toString()
        }

        if(calibrationCounter < 1000) return
        //if(azimuthInDegrees < 0) azimuthInDegrees = 180+abs(azimuthInDegrees)
        calibrationList.add(azimuthInDegrees.toInt())

        println(azimuthInDegrees)
        //

        if(calibrationCounter > 5000){
            calibrationNotification.visibility = View.GONE
            val averageAzimuth = (calibrationList.sum()/calibrationList.size.toDouble())
            mHeadTracker?.mTracker?.headingDegrees = averageAzimuth
            println("Average azimuth: $averageAzimuth, $calibrationList")
            sensorManager?.unregisterListener(this);
            compassResult.text = StringBuilder(averageAzimuth.toInt().toString() + "\n+-" + (calibrationList.max() - calibrationList.min()).toString())
        }
        // Now azimuthInDegrees contains the heading (direction) value.
        // Use it as needed.
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        //TODO("Not yet implemented")
    }
}
