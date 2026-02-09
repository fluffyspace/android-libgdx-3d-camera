package com.mygdx.game.ui.components

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import com.mygdx.game.notbaza.LatLon
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
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
) : GLSurfaceView(context) {

    private var rotationX = 20f
    private var rotationY = 0f
    private var previousX = 0f
    private var previousY = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(BuildingRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateColor(r: Float, g: Float, b: Float) {
        // Color is set at construction; re-create view for new colors
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
                requestRender()
            }
        }
        return true
    }

    private inner class BuildingRenderer : Renderer {
        private var program = 0
        private var vertexBuffer: FloatBuffer? = null
        private var normalBuffer: FloatBuffer? = null
        private var indexBuffer: ShortBuffer? = null
        private var indexCount = 0
        private var modelExtent = 1f

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

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            program = createProgram(vertexShaderCode, fragmentShaderCode)
            buildMesh()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            if (vertexBuffer == null || indexBuffer == null) return

            GLES20.glUseProgram(program)

            val width = this@BuildingPreviewRenderer.width.toFloat()
            val height = this@BuildingPreviewRenderer.height.toFloat()
            val aspect = if (height > 0) width / height else 1f

            val projMatrix = FloatArray(16)
            Matrix.perspectiveM(projMatrix, 0, 45f, aspect, 0.1f, 100f)

            val viewMatrix = FloatArray(16)
            val camDist = modelExtent * 2.5f
            val camX = camDist * cos(Math.toRadians(rotationX.toDouble())).toFloat() * sin(Math.toRadians(rotationY.toDouble())).toFloat()
            val camY = camDist * sin(Math.toRadians(rotationX.toDouble())).toFloat()
            val camZ = camDist * cos(Math.toRadians(rotationX.toDouble())).toFloat() * cos(Math.toRadians(rotationY.toDouble())).toFloat()
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
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)

            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

            GLES20.glDisableVertexAttribArray(posHandle)
            GLES20.glDisableVertexAttribArray(normalHandle)
        }

        private fun buildMesh() {
            if (polygon.size < 3) return

            // Convert polygon to local XZ coordinates (meters from centroid)
            val centroidLat = polygon.map { it.lat }.average()
            val centroidLon = polygon.map { it.lon }.average()

            val metersPerDegreeLat = 111320.0
            val metersPerDegreeLon = 111320.0 * cos(Math.toRadians(centroidLat))

            val localPoints = polygon.map { ll ->
                floatArrayOf(
                    ((ll.lon - centroidLon) * metersPerDegreeLon).toFloat(),
                    ((ll.lat - centroidLat) * metersPerDegreeLat).toFloat()
                )
            }

            val n = localPoints.size
            val groundY = minHeightMeters
            val roofY = heightMeters

            // Build vertices: ground ring + roof ring
            val vertices = mutableListOf<Float>()
            val normals = mutableListOf<Float>()
            val indices = mutableListOf<Short>()

            // Wall vertices (each wall quad = 4 vertices for proper normals)
            for (i in 0 until n) {
                val j = (i + 1) % n
                val x0 = localPoints[i][0]
                val z0 = localPoints[i][1]
                val x1 = localPoints[j][0]
                val z1 = localPoints[j][1]

                // Wall normal (outward facing)
                val edgeX = x1 - x0
                val edgeZ = z1 - z0
                val nx = -edgeZ
                val nz = edgeX
                val len = kotlin.math.sqrt(nx * nx + nz * nz)
                val nnx = if (len > 0) nx / len else 0f
                val nnz = if (len > 0) nz / len else 0f

                val baseIdx = (vertices.size / 3).toShort()

                // 4 vertices: g0, g1, r1, r0
                vertices.addAll(listOf(x0, groundY, z0))
                normals.addAll(listOf(nnx, 0f, nnz))
                vertices.addAll(listOf(x1, groundY, z1))
                normals.addAll(listOf(nnx, 0f, nnz))
                vertices.addAll(listOf(x1, roofY, z1))
                normals.addAll(listOf(nnx, 0f, nnz))
                vertices.addAll(listOf(x0, roofY, z0))
                normals.addAll(listOf(nnx, 0f, nnz))

                // Two triangles
                indices.addAll(listOf(baseIdx, (baseIdx + 1).toShort(), (baseIdx + 2).toShort()))
                indices.addAll(listOf(baseIdx, (baseIdx + 2).toShort(), (baseIdx + 3).toShort()))
            }

            // Roof vertices (ear-clipping triangulation)
            val roofBaseIdx = (vertices.size / 3).toShort()
            for (i in 0 until n) {
                vertices.addAll(listOf(localPoints[i][0], roofY, localPoints[i][1]))
                normals.addAll(listOf(0f, 1f, 0f))
            }

            // Simple ear-clipping triangulation for the roof
            val roofIndices = earClipTriangulate(localPoints)
            for (idx in roofIndices) {
                indices.add((roofBaseIdx + idx).toShort())
            }

            // Compute model extent for camera distance
            var maxDist = 0f
            for (pt in localPoints) {
                val d = kotlin.math.sqrt(pt[0] * pt[0] + pt[1] * pt[1])
                if (d > maxDist) maxDist = d
            }
            modelExtent = maxOf(maxDist, roofY - groundY, 1f)

            // Create buffers
            indexCount = indices.size

            val vb = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            vb.put(vertices.toFloatArray())
            vb.position(0)
            vertexBuffer = vb

            val nb = ByteBuffer.allocateDirect(normals.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            nb.put(normals.toFloatArray())
            nb.position(0)
            normalBuffer = nb

            val ib = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
            ib.put(indices.toShortArray())
            ib.position(0)
            indexBuffer = ib
        }

        private fun earClipTriangulate(points: List<FloatArray>): List<Int> {
            val result = mutableListOf<Int>()
            val remaining = points.indices.toMutableList()

            // Ensure consistent winding (CCW)
            var area = 0f
            for (i in points.indices) {
                val j = (i + 1) % points.size
                area += points[i][0] * points[j][1]
                area -= points[j][0] * points[i][1]
            }
            if (area < 0) remaining.reverse()

            var safety = remaining.size * remaining.size
            var i = 0
            while (remaining.size > 2 && safety-- > 0) {
                val sz = remaining.size
                val prev = remaining[(i - 1 + sz) % sz]
                val cur = remaining[i % sz]
                val next = remaining[(i + 1) % sz]

                val ax = points[prev][0]; val ay = points[prev][1]
                val bx = points[cur][0]; val by = points[cur][1]
                val cx = points[next][0]; val cy = points[next][1]

                val cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
                if (cross > 0) {
                    // Check no other point inside this triangle
                    var ear = true
                    for (k in remaining) {
                        if (k == prev || k == cur || k == next) continue
                        if (pointInTriangle(points[k][0], points[k][1], ax, ay, bx, by, cx, cy)) {
                            ear = false
                            break
                        }
                    }
                    if (ear) {
                        result.addAll(listOf(prev, cur, next))
                        remaining.removeAt(i % sz)
                        i = 0
                        continue
                    }
                }
                i++
                if (i >= remaining.size) i = 0
            }
            return result
        }

        private fun pointInTriangle(
            px: Float, py: Float,
            ax: Float, ay: Float,
            bx: Float, by: Float,
            cx: Float, cy: Float
        ): Boolean {
            val d1 = sign(px, py, ax, ay, bx, by)
            val d2 = sign(px, py, bx, by, cx, cy)
            val d3 = sign(px, py, cx, cy, ax, ay)
            val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
            val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)
            return !(hasNeg && hasPos)
        }

        private fun sign(
            x1: Float, y1: Float,
            x2: Float, y2: Float,
            x3: Float, y3: Float
        ): Float {
            return (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3)
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
