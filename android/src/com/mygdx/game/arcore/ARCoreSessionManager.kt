package com.mygdx.game.arcore

import android.app.Activity
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.DepthPoint
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

    // Camera translation in ARCore world space (tx, ty, tz)
    private val cameraTranslation = FloatArray(3)

    // Projection matrix from ARCore camera (16 floats)
    private val projMatrix = FloatArray(16)

    // Whether ARCore is currently tracking
    @Volatile
    var isTracking: Boolean = false
        private set

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

            isTracking = frame?.camera?.trackingState == TrackingState.TRACKING
            return isTracking

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
     * Get the camera's position in ARCore world space.
     * At session start this is (0,0,0). As the user physically moves,
     * ARCore tracks the translation with sub-centimeter precision.
     */
    fun getCameraTranslation(): FloatArray {
        val currentFrame = frame

        if (currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
            val pose = currentFrame.camera.pose
            cameraTranslation[0] = pose.tx()
            cameraTranslation[1] = pose.ty()
            cameraTranslation[2] = pose.tz()
        }

        return cameraTranslation
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
            currentFrame.camera.getProjectionMatrix(projMatrix, 0, 0.5f, 10000f)
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
        updateDisplayDimensions(width, height)
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

    // Display dimensions for hit testing
    private var displayWidth = 0
    private var displayHeight = 0

    // Ref-counted plane detection: multiple features (floor grid, vertices editor) can
    // request plane detection simultaneously; ARCore config is only toggled on 0<->1 transitions.
    private var planeDetectionRefCount = 0

    // Ref-counted depth mode
    private var depthRefCount = 0

    /**
     * Enable ARCore plane detection (ref-counted).
     * Multiple callers can request plane detection; it stays on until all disable.
     */
    fun enablePlaneDetection() {
        planeDetectionRefCount++
        if (planeDetectionRefCount == 1) {
            applyConfig()
        }
        Log.d(TAG, "Plane detection enabled (refCount=$planeDetectionRefCount)")
    }

    /**
     * Disable ARCore plane detection (ref-counted).
     * Only actually disables when all callers have released.
     */
    fun disablePlaneDetection() {
        planeDetectionRefCount = (planeDetectionRefCount - 1).coerceAtLeast(0)
        if (planeDetectionRefCount == 0) {
            applyConfig()
        }
        Log.d(TAG, "Plane detection disabled (refCount=$planeDetectionRefCount)")
    }

    /**
     * Enable ARCore depth mode (ref-counted).
     */
    fun enableDepth() {
        depthRefCount++
        if (depthRefCount == 1) {
            applyConfig()
        }
        Log.d(TAG, "Depth enabled (refCount=$depthRefCount)")
    }

    /**
     * Disable ARCore depth mode (ref-counted).
     */
    fun disableDepth() {
        depthRefCount = (depthRefCount - 1).coerceAtLeast(0)
        if (depthRefCount == 0) {
            applyConfig()
        }
        Log.d(TAG, "Depth disabled (refCount=$depthRefCount)")
    }

    private fun applyConfig() {
        val currentSession = session ?: return
        val config = Config(currentSession)
        config.planeFindingMode = if (planeDetectionRefCount > 0) Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            else Config.PlaneFindingMode.DISABLED
        config.depthMode = if (depthRefCount > 0) Config.DepthMode.AUTOMATIC
            else Config.DepthMode.DISABLED
        config.focusMode = Config.FocusMode.AUTO
        config.geospatialMode = Config.GeospatialMode.DISABLED
        currentSession.configure(config)
    }

    /**
     * Get height of camera above detected floor plane.
     * Returns the distance in meters from the camera to the lowest horizontal-upward plane,
     * or null if no floor plane is detected.
     */
    fun getFloorHeight(): Float? {
        val currentSession = session ?: return null
        val currentFrame = frame ?: return null
        if (currentFrame.camera.trackingState != TrackingState.TRACKING) return null

        val cameraPose = currentFrame.camera.pose
        var lowestPlaneY = Float.MAX_VALUE
        var found = false

        for (plane in currentSession.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            val planeY = plane.centerPose.ty()
            if (planeY < lowestPlaneY) {
                lowestPlaneY = planeY
                found = true
            }
        }

        if (!found) return null
        return cameraPose.ty() - lowestPlaneY
    }

    /**
     * Update stored display dimensions (called from setDisplayGeometry).
     */
    fun updateDisplayDimensions(width: Int, height: Int) {
        displayWidth = width
        displayHeight = height
    }

    data class HitTestResult(val x: Float, val y: Float, val z: Float, val method: String)

    /**
     * Perform a hit test at screen center against detected ARCore planes and depth points.
     * @return HitTestResult with coordinates and detection method, or null if nothing was hit.
     */
    fun hitTestCenter(): HitTestResult? {
        val currentFrame = frame ?: return null
        if (currentFrame.camera.trackingState != TrackingState.TRACKING) return null
        if (displayWidth == 0 || displayHeight == 0) return null

        val centerX = displayWidth / 2f
        val centerY = displayHeight / 2f

        try {
            val hitResults = currentFrame.hitTest(centerX, centerY)
            // Prefer depth hits first (more precise for arbitrary surfaces)
            for (hit in hitResults) {
                val trackable = hit.trackable
                if (trackable is DepthPoint) {
                    val pose = hit.hitPose
                    return HitTestResult(pose.tx(), pose.ty(), pose.tz(), "depth")
                }
            }
            // Fall back to plane hits
            for (hit in hitResults) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    val pose = hit.hitPose
                    return HitTestResult(pose.tx(), pose.ty(), pose.tz(), "plane")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hit test failed", e)
        }
        return null
    }

    /**
     * Legacy hit test returning just coordinates (for vertices editor compatibility).
     */
    fun hitTestCenterCoords(): FloatArray? {
        val result = hitTestCenter() ?: return null
        return floatArrayOf(result.x, result.y, result.z)
    }
}
