package com.mygdx.game

import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import com.mygdx.game.activities.AndroidLauncher

class AndroidDeviceCameraController(private val activity: AndroidLauncher) : DeviceCameraControl,
    SurfaceTexture.OnFrameAvailableListener {
    var previewView: GLSurfaceView? = null
    var openGLCameraRenderer: OpenGLCameraRenderer? = null
    @Synchronized
    override fun prepareCamera() {
        //activity.setFixedSize(960,640);
        if (previewView == null) {
            previewView = GLSurfaceView(activity)
            previewView!!.setEGLContextClientVersion(2) // OpenGL ES 2.0
            openGLCameraRenderer = OpenGLCameraRenderer(activity)
            previewView!!.setRenderer(openGLCameraRenderer)
            previewView!!.id = R.id.previewview
            Log.d("ingo", "creating previewView")
        }
        val frameLayout = activity.frameLayout
        previewView!!.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        Log.d("ingo", "before " + frameLayout.childCount)
        frameLayout.removeAllViews()
        frameLayout.addView(previewView, 0)
        Log.d("ingo", "after " + frameLayout.childCount)
        for (index in 0 until frameLayout.childCount) {
            val nextChild = frameLayout.getChildAt(index)
            Log.d("ingo", nextChild.id.toString())
        }
        Log.d("ingo", "previewview " + R.id.previewview + ", graphicsview " + R.id.graphicsview)
        //activity.addContentView(previewView, new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );
        //previewView.setVisibility(View.INVISIBLE);
    }

    companion object {
        private const val ONE_SECOND_IN_MILI = 1000
    }

    override fun onFrameAvailable(p0: SurfaceTexture?) {
        TODO("Not yet implemented")
    }
}