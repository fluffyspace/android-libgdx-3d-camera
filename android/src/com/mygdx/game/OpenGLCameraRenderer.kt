package com.mygdx.game

import android.app.Activity
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Frame
import com.mygdx.game.activities.AndroidLauncher
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2


class OpenGLCameraRenderer(var activity: AndroidLauncher) : GLSurfaceView.Renderer {
    private var cameraTextureId: Int = 0

    override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        // Generate texture ID for the camera feed
        GLES20.glClearColor(0f, 0f, 0f, 1.0f);
        activity.backgroundRenderer.createOnGlThread(activity);
        activity.openCamera()
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]

        // Bind the camera texture
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)

        // Set texture parameters
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    }

    override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height);
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear the screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Draw the camera feed
        drawCameraFrame()
    }

    private fun drawCameraFrame() {
        // Update camera texture with the latest camera frame
        updateCameraTexture()

        // Draw camera frame using OpenGL
        // (You can render a fullscreen quad or any other geometry to display the camera frame)
    }

    private fun updateCameraTexture() {
        if (!activity.shouldUpdateSurfaceTexture.get()) {
            // Not ready to draw.
            Log.d("ingo", "not ready")
            return;
        }
        Log.d("ingo", "ready")
        val frame: Frame = activity.mSession!!.update()
        activity.backgroundRenderer.draw(frame);
        // Update the camera texture with the latest camera frame from ARCore
        // You'll need to obtain the camera texture from ARCore's Camera object and bind it to the OpenGL texture
        // Here's a simplified example assuming you have access to the ARCore Camera object
        /*val cameraTexture = (activity as AndroidLauncher).mSession!!.sharedCamera.surfaceTexture
        cameraTexture?.let {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
            GLES20.glTexImage2D(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0, GLES20.GL_RGBA,
                cameraTexture., cameraTexture.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, cameraTexture.buffer
            )
        }*/
    }
}