package com.mygdx.game.arcore

import android.app.Activity
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

/**
 * Manages ARCore session lifecycle and provides camera pose/view matrix.
 * Replaces the old HeadTracker + OrientationEKF approach with ARCore's
 * Visual-Inertial Odometry for drift-free tracking.
 */
class ARCoreSessionManager(private val activity: Activity) {

    companion object {
        private const val TAG = "ARCoreSessionManager"
    }

    var session: Session? = null
        private set

    var frame: Frame? = null
        private set

    private var installRequested = false
    private var textureSet = false

    // View matrix from ARCore camera (16 floats)
    private val viewMatrix = FloatArray(16)

    // Transformation matrix to match the old HeadTracker coordinate convention
    // The old HeadTracker applied a -90 degree rotation around X axis
    private val ekfToHeadTracker = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    // Heading offset for calibration (similar to OrientationEKF.headingDegrees)
    var headingDegrees: Double = 0.0

    // Geospatial pose data (when available)
    var geospatialLatitude: Double = 0.0
        private set
    var geospatialLongitude: Double = 0.0
        private set
    var geospatialAltitude: Double = 0.0
        private set
    var geospatialHeading: Double = 0.0
        private set
    var isGeospatialAvailable: Boolean = false
        private set

    init {
        // Initialize the same transform as HeadTracker used
        @Suppress("DEPRECATION")
        Matrix.setRotateEulerM(ekfToHeadTracker, 0, -90f, 0f, 0f)
    }

    /**
     * Check if ARCore is installed and request installation if needed.
     * Should be called in onResume.
     * @return true if ARCore is ready, false if installation is pending
     */
    fun checkAvailability(): Boolean {
        when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                installRequested = true
                return false
            }
            ArCoreApk.InstallStatus.INSTALLED -> {
                return true
            }
        }
    }

    /**
     * Create and configure the ARCore session.
     * Should be called after checkAvailability returns true.
     */
    fun createSession(): Boolean {
        if (session != null) {
            return true
        }

        try {
            session = Session(activity)

            val config = Config(session)

            // Disable Geospatial mode - we use our own GPS-based coordinate system
            // Geospatial mode causes depth-related warnings during initialization
            config.geospatialMode = Config.GeospatialMode.DISABLED
            Log.d(TAG, "Geospatial mode disabled (using custom GPS positioning)")

            // Disable plane detection - we don't need it for AR overlay
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED

            // Disable depth - not needed for our use case
            config.depthMode = Config.DepthMode.DISABLED

            // Use back camera
            config.focusMode = Config.FocusMode.AUTO

            session!!.configure(config)

            Log.d(TAG, "ARCore session created successfully")
            return true

        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "ARCore not installed", e)
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "ARCore APK too old", e)
        } catch (e: UnavailableSdkTooOldException) {
            Log.e(TAG, "SDK too old for ARCore", e)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Device not compatible with ARCore", e)
        } catch (e: UnavailableUserDeclinedInstallationException) {
            Log.e(TAG, "User declined ARCore installation", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ARCore session", e)
        }

        return false
    }

    /**
     * Resume the ARCore session. Should be called in Activity.onResume.
     */
    fun resume() {
        session?.let {
            try {
                it.resume()
                Log.d(TAG, "ARCore session resumed")
            } catch (e: CameraNotAvailableException) {
                Log.e(TAG, "Camera not available", e)
                session = null
            }
        }
    }

    /**
     * Pause the ARCore session. Should be called in Activity.onPause.
     */
    fun pause() {
        session?.pause()
    }

    /**
     * Close the ARCore session. Should be called in Activity.onDestroy.
     */
    fun close() {
        session?.close()
        session = null
    }

    /**
     * Update the session and get the latest frame.
     * Should be called each frame before rendering.
     * @return true if a valid frame was obtained
     */
    fun update(): Boolean {
        val currentSession = session ?: return false

        // Don't call update() until texture has been set
        if (!textureSet) {
            return false
        }

        try {
            frame = currentSession.update()

            // Update Geospatial pose if available
            updateGeospatialPose()

            return frame?.camera?.trackingState == TrackingState.TRACKING

        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during update", e)
            return false
        }
    }

    /**
     * Get the camera view matrix from ARCore.
     * This replaces HeadTracker.getLastHeadView().
     *
     * The matrix is transformed to match the coordinate convention
     * used by the old HeadTracker (with the -90 degree X rotation).
     */
    fun getViewMatrix(): FloatArray {
        val currentFrame = frame

        if (currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
            // Get ARCore's view matrix
            currentFrame.camera.getViewMatrix(tempMatrix, 0)

            // Apply the same transform that HeadTracker used
            // This ensures compatibility with existing coordinate system in MyGdxGame
            Matrix.multiplyMM(viewMatrix, 0, tempMatrix, 0, ekfToHeadTracker, 0)
        } else {
            // Return identity if not tracking
            Matrix.setIdentityM(viewMatrix, 0)
        }

        return viewMatrix
    }

    /**
     * Update Geospatial pose data from ARCore Earth API.
     */
    private fun updateGeospatialPose() {
        val currentFrame = frame ?: return
        val currentSession = session ?: return

        try {
            val earth = currentSession.earth
            if (earth != null && earth.trackingState == TrackingState.TRACKING) {
                val geospatialPose = earth.cameraGeospatialPose

                geospatialLatitude = geospatialPose.latitude
                geospatialLongitude = geospatialPose.longitude
                geospatialAltitude = geospatialPose.altitude
                @Suppress("DEPRECATION")
                geospatialHeading = geospatialPose.heading
                isGeospatialAvailable = true

            } else {
                isGeospatialAvailable = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geospatial pose not available", e)
            isGeospatialAvailable = false
        }
    }

    /**
     * Set the display geometry for ARCore.
     * Should be called when the surface size changes.
     */
    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        session?.setDisplayGeometry(rotation, width, height)
    }

    /**
     * Set the camera texture ID that ARCore renders to.
     * This is needed for the background renderer.
     */
    fun setCameraTextureName(textureId: Int) {
        session?.setCameraTextureName(textureId)
        textureSet = true
        Log.d(TAG, "Camera texture name set: $textureId")
    }

    /**
     * Check if the texture has been set and the session is ready for updates.
     */
    fun isReadyForUpdate(): Boolean = textureSet && session != null
}
