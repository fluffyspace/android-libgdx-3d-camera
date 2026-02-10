package com.mygdx.game.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.TextureView
import com.mygdx.game.notbaza.LatLon
import kotlin.math.cos
import kotlin.math.sin

class BuildingPreviewRenderer(
    context: Context,
    private val polygon: List<LatLon>,
    private val heightMeters: Float,
    private val minHeightMeters: Float = 0f,
    private val colorR: Float = 0.5f,
    private val colorG: Float = 0.5f,
    private val colorB: Float = 1.0f
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var rotationX = 20f
    private var rotationY = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var renderThread: RenderThread? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread = RenderThread(surface, width, height).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.updateSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderThread?.stopRendering()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onDetachedFromWindow() {
        renderThread?.stopRendering()
        renderThread = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - previousX
                val dy = event.y - previousY
                rotationY += dx * 0.5f
                rotationX += dy * 0.5f
                rotationX = rotationX.coerceIn(-89f, 89f)
                previousX = event.x
                previousY = event.y
                renderThread?.requestRender()
            }
        }
        return true
    }

    private inner class RenderThread(
        private val surfaceTexture: SurfaceTexture,
        @Volatile private var viewWidth: Int,
        @Volatile private var viewHeight: Int
    ) : Thread("BuildingPreviewGL") {

        @Volatile private var running = true
        @Volatile private var renderRequested = true
        @Volatile private var sizeChanged = false
        private val lock = Object()

        private var mesh: BuildingPreviewMesh? = null
        private var program = 0

        private val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            attribute vec4 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vPosition = (uModelMatrix * aPosition).xyz;
                vNormal = mat3(uModelMatrix) * aNormal;
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            precision mediump float;
            uniform vec3 uColor;
            uniform vec3 uLightDir;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                vec3 normal = normalize(vNormal);
                float ambient = 0.35;
                float diffuse = max(dot(normal, normalize(uLightDir)), 0.0) * 0.65;
                float light = ambient + diffuse;
                gl_FragColor = vec4(uColor * light, 0.85);
            }
        """.trimIndent()

        fun updateSize(w: Int, h: Int) {
            synchronized(lock) {
                viewWidth = w
                viewHeight = h
                sizeChanged = true
                renderRequested = true
                lock.notify()
            }
        }

        fun requestRender() {
            synchronized(lock) {
                renderRequested = true
                lock.notify()
            }
        }

        fun stopRendering() {
            running = false
            synchronized(lock) { lock.notify() }
            try { join(2000) } catch (_: InterruptedException) {}
        }

        override fun run() {
            var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
            var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT

            try {
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                val version = IntArray(2)
                EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

                val configAttribs = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
                val eglConfig = configs[0] ?: return

                val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, surfaceAttribs, 0)

                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glEnable(GLES20.GL_DEPTH_TEST)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                program = createProgram(vertexShaderCode, fragmentShaderCode)
                mesh = BuildingPreviewMesh.build(polygon, heightMeters, minHeightMeters)

                GLES20.glViewport(0, 0, viewWidth, viewHeight)

                while (running) {
                    synchronized(lock) {
                        while (!renderRequested && running) {
                            try { lock.wait() } catch (_: InterruptedException) { return }
                        }
                        renderRequested = false
                        if (sizeChanged) {
                            GLES20.glViewport(0, 0, viewWidth, viewHeight)
                            sizeChanged = false
                        }
                    }

                    if (!running) break

                    drawFrame()
                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                }
            } finally {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(eglDisplay)
            }
        }

        private fun drawFrame() {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            val m = mesh ?: return

            GLES20.glUseProgram(program)

            val aspect = if (viewHeight > 0) viewWidth.toFloat() / viewHeight.toFloat() else 1f
            val camDist = m.modelExtent * 2.5f
            val nearPlane = camDist * 0.01f
            val farPlane = camDist * 4f

            val projMatrix = FloatArray(16)
            Matrix.perspectiveM(projMatrix, 0, 45f, aspect, nearPlane, farPlane)

            // Camera orbits around the origin (mesh is already centered vertically)
            val viewMatrix = FloatArray(16)
            val camX = camDist * cos(Math.toRadians(rotationX.toDouble())).toFloat() *
                    sin(Math.toRadians(rotationY.toDouble())).toFloat()
            val camY = camDist * sin(Math.toRadians(rotationX.toDouble())).toFloat()
            val camZ = camDist * cos(Math.toRadians(rotationX.toDouble())).toFloat() *
                    cos(Math.toRadians(rotationY.toDouble())).toFloat()
            Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, 0f, 0f, 0f, 0f, 1f, 0f)

            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)

            val mvpMatrix = FloatArray(16)
            val tempMatrix = FloatArray(16)
            Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)

            val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

            val modelHandle = GLES20.glGetUniformLocation(program, "uModelMatrix")
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)

            val colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            GLES20.glUniform3f(colorHandle, colorR, colorG, colorB)

            val lightHandle = GLES20.glGetUniformLocation(program, "uLightDir")
            GLES20.glUniform3f(lightHandle, 0.5f, 1.0f, 0.3f)

            val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, m.vertexBuffer)

            val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, m.normalBuffer)

            GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.indexCount, GLES20.GL_UNSIGNED_SHORT, m.indexBuffer)

            GLES20.glDisableVertexAttribArray(posHandle)
            GLES20.glDisableVertexAttribArray(normalHandle)
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
            return prog
        }

        private fun loadShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
