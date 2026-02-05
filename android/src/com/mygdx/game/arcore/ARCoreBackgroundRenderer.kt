package com.mygdx.game.arcore

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders the ARCore camera feed as a background for the LibGDX overlay.
 * Uses OpenGL ES to draw a full-screen quad textured with the camera image.
 */
class ARCoreBackgroundRenderer {

    companion object {
        private const val TAG = "ARCoreBackgroundRenderer"

        private const val COORDS_PER_VERTEX = 2
        private const val FLOAT_SIZE = 4
        private const val VERTICES_COUNT = 4

        // Vertex shader for drawing the camera background
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        // Fragment shader for external OES texture (camera)
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        // Quad vertices covering the full screen in NDC
        private val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f,  // Bottom-left
            -1.0f, +1.0f,  // Top-left
            +1.0f, -1.0f,  // Bottom-right
            +1.0f, +1.0f   // Top-right
        )
    }

    private var quadVertices: FloatBuffer
    private var quadTexCoords: FloatBuffer

    private var program: Int = 0
    private var positionAttrib: Int = 0
    private var texCoordAttrib: Int = 0
    private var textureUniform: Int = 0

    var textureId: Int = -1
        private set

    var isInitialized = false
        private set

    private var texCoordsInitialized = false

    init {
        // Initialize vertex buffer
        quadVertices = ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_COORDS)
                position(0)
            }

        // Initialize texture coordinate buffer with sensible defaults
        // (will be updated by ARCore when display geometry changes)
        quadTexCoords = ByteBuffer.allocateDirect(VERTICES_COUNT * COORDS_PER_VERTEX * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(
                    0.0f, 1.0f,  // Bottom-left
                    0.0f, 0.0f,  // Top-left
                    1.0f, 1.0f,  // Bottom-right
                    1.0f, 0.0f   // Top-right
                ))
                position(0)
            }
    }

    /**
     * Initialize OpenGL resources. Must be called on the GL thread.
     */
    fun initialize() {
        if (isInitialized) return

        // Create external texture for camera
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        // Create shader program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Error linking program: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            program = 0
            return
        }

        // Get attribute and uniform locations
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")

        // Delete shaders (they're linked to the program now)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        isInitialized = true
        Log.d(TAG, "ARCore background renderer initialized, textureId=$textureId")
    }

    /**
     * Draw the camera background.
     * Must be called before rendering 3D content.
     *
     * @param frame The current ARCore frame
     */
    fun draw(frame: Frame) {
        if (!isInitialized || program == 0) {
            return
        }

        // Update texture coords on first frame OR when geometry changes
        if (!texCoordsInitialized || frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadVertices,
                Coordinates2d.TEXTURE_NORMALIZED,
                quadTexCoords
            )
            texCoordsInitialized = true
        }

        // Skip if no timestamp (no valid camera image)
        if (frame.timestamp == 0L) {
            return
        }

        // Save OpenGL state
        val depthTestEnabled = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        val depthMaskEnabled = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_DEPTH_WRITEMASK, depthMaskEnabled, 0)

        // Disable depth for background
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        // Use shader program
        GLES20.glUseProgram(program)

        // Bind camera texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        // Set vertex attributes
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)

        quadVertices.position(0)
        GLES20.glVertexAttribPointer(
            positionAttrib,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadVertices
        )

        quadTexCoords.position(0)
        GLES20.glVertexAttribPointer(
            texCoordAttrib,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadTexCoords
        )

        // Draw the quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTICES_COUNT)

        // Disable vertex attributes
        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)

        // Restore OpenGL state
        if (depthTestEnabled) {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }
        GLES20.glDepthMask(depthMaskEnabled[0] != 0)

        // Unbind texture
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    /**
     * Clean up OpenGL resources.
     */
    fun dispose() {
        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = -1
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        isInitialized = false
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }
}
