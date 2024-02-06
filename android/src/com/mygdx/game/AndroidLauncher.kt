package com.mygdx.game

import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.google.gson.Gson
import com.mygdx.game.googlecardboard.HeadTracker
import com.mygdx.game.overr.AndroidApplicationOverrided
import java.lang.reflect.Type

class AndroidLauncher() : AndroidApplicationOverrided(), OnDrawFrame {
    private var mHeadTracker: HeadTracker? = null
    private var origWidth = 0
    private var origHeight = 0
    var fov: Int = 50
    var gson = Gson()
    lateinit var fovTv: TextView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mHeadTracker = HeadTracker(this)
        val config = AndroidApplicationConfiguration()
        config.useAccelerometer = false
        config.useCompass = false
        config.useGyroscope = false
        cameraControl = AndroidDeviceCameraController(this)
        //config.useGL30 = true;
        // we need to change the default pixel format - since it does not include an alpha channel
        // we need the alpha channel so the camera preview will be seen behind the GL scene
        config.r = 8
        config.g = 8
        config.b = 8
        config.a = 8
        config.useGL30 = false
        val intent = intent
        val camera_coordinates = intent.getStringExtra("camera_coordinates")
        val object_coordinates = intent.getStringExtra("object_coordinates")
        val game = MyGdxGame(
            this,
            gson.fromJson(camera_coordinates, DoubleArray::class.java as Type),
            gson.fromJson(object_coordinates, DoubleArray::class.java as Type),
            cameraControl
        )
        initialize(game, config)
        fovTv = this.fieldOfViewLayout.findViewById(R.id.fovValueTv)
        this.fieldOfViewLayout.findViewById<Button>(R.id.fovUp).setOnClickListener {
            fov++
            fovTv.setText(fov.toString())
            game.fov = fov
        }
        this.fieldOfViewLayout.findViewById<Button>(R.id.fovDown).setOnClickListener {
            fov--
            fovTv.setText(fov.toString())
            game.fov = fov
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
        mHeadTracker!!.stopTracking()
    }

    override fun getLastHeadView(): FloatArray {
        val floats1 = FloatArray(16)
        mHeadTracker!!.getLastHeadView(floats1, 0)
        return floats1
    }
}
