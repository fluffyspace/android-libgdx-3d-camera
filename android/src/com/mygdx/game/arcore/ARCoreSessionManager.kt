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

    // Projection matrix from ARCore camera (16 floats)
    private val projMatrix = FloatArray(16)

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
        Matrix.setIdentityM(viewMatrix, 0)
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
     * Get the camera rotation matrix from ARCore (orientation only, no translation).
     * This replaces HeadTracker.getLastHeadView().
     *
     * Uses ARCore's built-in getViewMatrix() which handles:
     * - Display orientation
     * - OpenGL coordinate conventions
     * - Proper view matrix calculation (displayOrientedPose.inverse())
     *
     * We extract only the 3x3 rotation portion since the app handles translation
     * via ECEF coordinates.
     */
    fun getViewMatrix(): FloatArray {
        val currentFrame = frame

        if (currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
            // Use ARCore's built-in getViewMatrix which handles display orientation
            val arCoreViewMatrix = FloatArray(16)
            currentFrame.camera.getViewMatrix(arCoreViewMatrix, 0)

            // Copy only the 3x3 rotation portion (no translation)
            viewMatrix[0] = arCoreViewMatrix[0]
            viewMatrix[1] = arCoreViewMatrix[1]
            viewMatrix[2] = arCoreViewMatrix[2]
            viewMatrix[3] = 0f

            viewMatrix[4] = arCoreViewMatrix[4]
            viewMatrix[5] = arCoreViewMatrix[5]
            viewMatrix[6] = arCoreViewMatrix[6]
            viewMatrix[7] = 0f

            viewMatrix[8] = arCoreViewMatrix[8]
            viewMatrix[9] = arCoreViewMatrix[9]
            viewMatrix[10] = arCoreViewMatrix[10]
            viewMatrix[11] = 0f

            viewMatrix[12] = 0f
            viewMatrix[13] = 0f
            viewMatrix[14] = 0f
            viewMatrix[15] = 1f
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
     * Get the camera projection matrix from ARCore.
     * This provides the correct FOV based on the actual camera intrinsics.
     */
    fun getProjectionMatrix(): FloatArray {
        val currentFrame = frame

        if (currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
            currentFrame.camera.getProjectionMatrix(projMatrix, 0, 1f, 300f)
        } else {
            Matrix.setIdentityM(projMatrix, 0)
        }

        return projMatrix
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
