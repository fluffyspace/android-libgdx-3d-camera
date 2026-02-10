package com.mygdx.game.ui.components

import com.mygdx.game.notbaza.LatLon
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sqrt

class BuildingPreviewMesh(
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val indexBuffer: ShortBuffer,
    val indexCount: Int,
    val modelExtent: Float,
    val centerY: Float
) {
    companion object {
        fun build(
            polygon: List<LatLon>,
            heightMeters: Float,
            minHeightMeters: Float
        ): BuildingPreviewMesh? {
            if (polygon.size < 3) return null

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
            val centerY = (groundY + roofY) / 2f

            val vertices = mutableListOf<Float>()
            val normals = mutableListOf<Float>()
            val indices = mutableListOf<Short>()

            // Wall vertices (each quad gets its own 4 verts for flat normals)
            for (i in 0 until n) {
                val j = (i + 1) % n
                val x0 = localPoints[i][0]
                val z0 = localPoints[i][1]
                val x1 = localPoints[j][0]
                val z1 = localPoints[j][1]

                val edgeX = x1 - x0
                val edgeZ = z1 - z0
                val nx = -edgeZ
                val nz = edgeX
                val len = sqrt(nx * nx + nz * nz)
                val nnx = if (len > 0) nx / len else 0f
                val nnz = if (len > 0) nz / len else 0f

                val baseIdx = (vertices.size / 3).toShort()

                // 4 vertices: ground0, ground1, roof1, roof0
                vertices.addAll(listOf(x0, groundY - centerY, z0))
                normals.addAll(listOf(nnx, 0f, nnz))
                vertices.addAll(listOf(x1, groundY - centerY, z1))
                normals.addAll(listOf(nnx, 0f, nnz))
                vertices.addAll(listOf(x1, roofY - centerY, z1))
                normals.addAll(listOf(nnx, 0f, nnz))
                vertices.addAll(listOf(x0, roofY - centerY, z0))
                normals.addAll(listOf(nnx, 0f, nnz))

                indices.addAll(listOf(baseIdx, (baseIdx + 1).toShort(), (baseIdx + 2).toShort()))
                indices.addAll(listOf(baseIdx, (baseIdx + 2).toShort(), (baseIdx + 3).toShort()))
            }

            // Roof vertices
            val roofBaseIdx = (vertices.size / 3).toShort()
            for (i in 0 until n) {
                vertices.addAll(listOf(localPoints[i][0], roofY - centerY, localPoints[i][1]))
                normals.addAll(listOf(0f, 1f, 0f))
            }

            val roofIndices = earClipTriangulate(localPoints)
            for (idx in roofIndices) {
                indices.add((roofBaseIdx + idx).toShort())
            }

            // Model extent = max of horizontal radius and half-height
            var maxDist = 0f
            for (pt in localPoints) {
                val d = sqrt(pt[0] * pt[0] + pt[1] * pt[1])
                if (d > maxDist) maxDist = d
            }
            val halfHeight = (roofY - groundY) / 2f
            val modelExtent = maxOf(maxDist, halfHeight, 1f)

            // Create NIO buffers
            val vb = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            vb.put(vertices.toFloatArray())
            vb.position(0)

            val nb = ByteBuffer.allocateDirect(normals.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            nb.put(normals.toFloatArray())
            nb.position(0)

            val ib = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
            ib.put(indices.toShortArray())
            ib.position(0)

            return BuildingPreviewMesh(
                vertexBuffer = vb,
                normalBuffer = nb,
                indexBuffer = ib,
                indexCount = indices.size,
                modelExtent = modelExtent,
                centerY = centerY
            )
        }

        private fun earClipTriangulate(points: List<FloatArray>): List<Int> {
            val result = mutableListOf<Int>()
            val remaining = points.indices.toMutableList()

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
    }
}
