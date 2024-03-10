package com.mygdx.game.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.ImageFormat
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mygdx.game.AndroidDeviceCameraController
import com.mygdx.game.BackgroundRenderer
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
import java.util.Arrays
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean


class AndroidLauncher : AndroidApplicationOverrided(), OnDrawFrame, SensorEventListener,
    ImageReader.OnImageAvailableListener {
    val shouldUpdateSurfaceTexture = AtomicBoolean(false)
    private var arcoreActive: Boolean = false
    private var arMode: Boolean = true
    private var previewCaptureRequestBuilder: android.hardware.camera2.CaptureRequest.Builder? = null
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
    lateinit var arButton: ImageView
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

    // requestInstall(Activity, true) will triggers installation of
    // Google Play Services for AR if necessary.
    var mUserRequestedInstall = true
    var mSession: Session? = null
    var sharedCamera: com.google.ar.core.SharedCamera? = null
    var captureSession: CameraCaptureSession? = null
    var cameraId: String = ""
    var cameraDevice: CameraDevice? = null
    var backgroundHandler: Handler? = null

    var backgroundThread: HandlerThread? = null

    val backgroundRenderer: BackgroundRenderer = BackgroundRenderer()

    private var cpuImageReader: ImageReader? = null

    // A check mechanism to ensure that the camera closed properly so that the app can safely exit.
    private val safeToExitApp = ConditionVariable()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mHeadTracker = HeadTracker(this)

        var config = AndroidApplicationConfiguration()
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
        maybeEnableArButton()
    }

    fun openCamera(){
        createSession()
        // Wrap the callback in a shared camera callback.
        val wrappedCallback = sharedCamera!!.createARDeviceStateCallback(cameraDeviceCallback, backgroundHandler)

        // Store a reference to the camera system service.
        val cameraManager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Open the camera device using the ARCore wrapped callback.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        cameraManager.openCamera(cameraId, wrappedCallback, backgroundHandler)

    }

    // Define cameraDeviceCallback
    val cameraDeviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            // This method is called when the camera device is opened.
            // You can start camera preview or other operations here.
            this@AndroidLauncher.onOpened(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            // This method is called when the camera device is disconnected.
            // Clean up resources and handle the disconnection.
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            cameraDevice = null
            safeToExitApp.open();
        }

        override fun onError(camera: CameraDevice, error: Int) {
            // This method is called when an error occurs with the camera device.
            // Handle the error appropriately.
        }
    }


    fun onOpened(cameraDevice: CameraDevice) {
        Log.d(TAG, "Camera device ID " + cameraDevice.id + " opened.")
        this.cameraDevice = cameraDevice
        createCameraPreviewSession()

    }

    fun createCameraPreviewSession() {
        try {
            // Create an ARCore-compatible capture request using `TEMPLATE_RECORD`.
            mSession?.setCameraTextureName(backgroundRenderer.textureId);
            sharedCamera?.surfaceTexture?.setOnFrameAvailableListener(cameraControl);

            previewCaptureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

            // Build a list of surfaces, starting with ARCore provided surfaces.
            val surfaceList: MutableList<Surface> = sharedCamera!!.arCoreSurfaces

            surfaceList.add(cpuImageReader!!.surface);

            // Add ARCore surfaces and CPU image surface targets.
            for (surface in surfaceList) {
                previewCaptureRequestBuilder!!.addTarget(surface)
            }

            // Wrap the callback in a shared camera callback.
            val wrappedCallback = sharedCamera!!.createARSessionStateCallback(cameraSessionStateCallback, backgroundHandler)

            // Create a camera capture session for camera preview using an ARCore wrapped callback.
            cameraDevice!!.createCaptureSession(surfaceList, wrappedCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "CameraAccessException", e)
        }
    }

    // Start background handler thread, used to run callbacks without blocking UI thread.
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("sharedCameraBackground")
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    // Stop background handler thread.
    private fun stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread!!.quitSafely()
            try {
                backgroundThread!!.join()
                backgroundThread = null
                backgroundHandler = null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Interrupted while trying to join background handler thread", e)
            }
        }
    }


    val cameraSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        // Called when ARCore first configures the camera capture session after
        // initializing the app, and again each time the activity resumes.
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d("ingo", "onConfigured")
            captureSession = session
            setRepeatingCaptureRequest()
        }

        override fun onConfigureFailed(p0: CameraCaptureSession) {
            //TODO("Not yet implemented")
        }

        override fun onActive(session: CameraCaptureSession) {
            if (arMode && !arcoreActive) {
                resumeARCore()
            }
        }
    }

    val cameraCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            shouldUpdateSurfaceTexture.set(true)
        }
    }

    fun setRepeatingCaptureRequest() {
        captureSession!!.setRepeatingRequest(
            previewCaptureRequestBuilder!!.build(), cameraCaptureCallback, backgroundHandler
        )
    }

    fun resumeARCore() {
        if (!arcoreActive) {
            try {
                // Resume ARCore.
                backgroundRenderer.suppressTimestampZeroRendering(false);
                mSession?.resume()
                arcoreActive = true

                // Set the capture session callback while in AR mode.
                sharedCamera!!.setCaptureCallback(cameraCaptureCallback, backgroundHandler)
            } catch (e: CameraNotAvailableException) {
                Log.e(TAG, "Failed to resume ARCore session", e);
                return;
            }
        }
    }

    private fun closeCamera() {
        if (captureSession != null) {
            captureSession!!.close()
            captureSession = null
        }
        if (cameraDevice != null) {
            //waitUntilCameraCaptureSessionIsActive()
            safeToExitApp.close()
            cameraDevice!!.close()
            safeToExitApp.block()
        }
        if (cpuImageReader != null) {
            cpuImageReader!!.close()
            cpuImageReader = null
        }
    }

    fun maybeEnableArButton() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isSupported) {
            arButton.visibility = View.VISIBLE
            arButton.isEnabled = true
        } else { // The device is unsupported or unknown.
            arButton.visibility = View.INVISIBLE
            arButton.isEnabled = false
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
        arButton = this.fieldOfViewLayout.findViewById<ImageView>(R.id.ar)
        arButton.setOnClickListener {
            //createSession()
            //openCamera()
            runOnUiThread{
                cameraControl.prepareCamera()
            }
        }
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

    }

    override fun onResume() {
        super.onResume()
        mHeadTracker!!.startTracking()
        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        mSession?.resume()
        startBackgroundThread();
        try {
            if (mSession == null) {
                when (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        // Success: Safe to create the AR session.
                        //mSession = Session(this)
                    }
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        // When this method returns `INSTALL_REQUESTED`:
                        // 1. ARCore pauses this activity.
                        // 2. ARCore prompts the user to install or update Google Play
                        //    Services for AR (market://details?id=com.google.ar.core).
                        // 3. ARCore downloads the latest device profile data.
                        // 4. ARCore resumes this activity. The next invocation of
                        //    requestInstall() will either return `INSTALLED` or throw an
                        //    exception if the installation or update did not succeed.
                        mUserRequestedInstall = false
                        return
                    }
                }
            }
        } catch (e: UnavailableUserDeclinedInstallationException) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "TODO: handle exception " + e, Toast.LENGTH_LONG)
                .show()
            return
        } catch (e: Exception) {

            return  // mSession remains null, since session creation has failed.
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        mSession?.close()
    }

    override fun onPause() {
        super.onPause()
        orientationSeekbarJob.cancel()
        mHeadTracker!!.stopTracking()
        sensorManager?.unregisterListener(this);
        mSession!!.pause()
        closeCamera()
        stopBackgroundThread();
    }

    fun createSession() {
        // Create a new ARCore session.
        if (mSession == null) {
            mSession = Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA))

            // Create a camera config filter for the session.
            val filter = CameraConfigFilter(mSession)

            // Return only camera configs that target 30 fps camera capture frame rate.
            filter.targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)

            // Return only camera configs that will not use the depth sensor.
            filter.depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE)

            // Get list of configs that match filter settings.
            // In this case, this list is guaranteed to contain at least one element,
            // because both TargetFps.TARGET_FPS_30 and DepthSensorUsage.DO_NOT_USE
            // are supported on all ARCore supported devices.
            val cameraConfigList = mSession!!.getSupportedCameraConfigs(filter)

            // Use element 0 from the list of returned camera configs. This is because
            // it contains the camera config that best matches the specified filter
            // settings.
            mSession!!.cameraConfig = cameraConfigList[0]

            /*// Create a session config.
            val config = Config(mSession)

            // Do feature-specific operations here, such as enabling depth or turning on
            // support for Augmented Faces.
            config.setFocusMode(Config.FocusMode.AUTO);

            // Configure the session.
            mSession!!.configure(config)*/
        }

        // Store the ARCore shared camera reference.
        sharedCamera = mSession!!.sharedCamera

        // Store the ID of the camera that ARCore uses.
        cameraId = mSession!!.cameraConfig.cameraId

        // Use the currently configured CPU image size.
        // Use the currently configured CPU image size.
        val desiredCpuImageSize: Size = mSession!!.cameraConfig.imageSize
        cpuImageReader = ImageReader.newInstance(
            desiredCpuImageSize.width,
            desiredCpuImageSize.height,
            android.graphics.ImageFormat.YUV_420_888,
            2
        )
        cpuImageReader!!.setOnImageAvailableListener(this, backgroundHandler)
        // When ARCore is running, make sure it also updates our CPU image surface.
        sharedCamera!!.setAppSurfaces(this.cameraId, listOf(cpuImageReader!!.surface));
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

    var cpuImagesProcessed: Int = 0

    override fun onImageAvailable(imageReader: ImageReader?) {
        val image: Image? = imageReader?.acquireLatestImage()
        if (image == null) {
            Log.w(TAG, "onImageAvailable: Skipping null image.")
            return
        }

        image.close()
        cpuImagesProcessed++

        // Reduce the screen update to once every two seconds with 30fps if running as automated test.
    }
}
