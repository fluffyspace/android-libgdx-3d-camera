package com.mygdx.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.EarClippingTriangulator
import com.badlogic.gdx.math.Vector3
import com.mygdx.game.notbaza.Building
import kotlin.math.cos
import kotlin.math.sin

class BuildingMeshGenerator {

    private val triangulator = EarClippingTriangulator()

    fun generate(
        building: Building,
        cameraCartesian: Vector3,
        geoToCartesian: (Double, Double, Double) -> Vector3,
        color: Color = Color(0.6f, 0.6f, 0.6f, 0.35f),
        alpha: Float = color.a
    ): Model? {
        val polygon = building.polygon
        if (polygon.size < 3) return null

        return try {
            generateMesh(building, cameraCartesian, geoToCartesian, Color(color.r, color.g, color.b, alpha))
        } catch (e: Exception) {
            null
        }
    }

    private fun generateMesh(
        building: Building,
        cameraCartesian: Vector3,
        geoToCartesian: (Double, Double, Double) -> Vector3,
        color: Color = Color(0.6f, 0.6f, 0.6f, 0.35f)
    ): Model {
        val polygon = building.polygon

        // Compute ground and roof vertex positions as ECEF offsets from camera
        // Using Double for intermediate math to avoid float precision loss
        val groundPositions = mutableListOf<Vector3>()
        val roofPositions = mutableListOf<Vector3>()

        for (vertex in polygon) {
            val groundCart = geoToCartesian(vertex.lat, vertex.lon, building.minHeightMeters.toDouble())
            val roofCart = geoToCartesian(vertex.lat, vertex.lon, building.heightMeters.toDouble())

            // Y/Z swap matches existing convention in MyGdxGame.kt:164-166
            groundPositions.add(
                Vector3(
                    groundCart.x - cameraCartesian.x,
                    groundCart.z - cameraCartesian.z,
                    groundCart.y - cameraCartesian.y
                )
            )
            roofPositions.add(
                Vector3(
                    roofCart.x - cameraCartesian.x,
                    roofCart.z - cameraCartesian.z,
                    roofCart.y - cameraCartesian.y
                )
            )
        }

        val material = Material(
            ColorAttribute.createDiffuse(color),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        // --- Walls ---
        val wallBuilder: MeshPartBuilder = modelBuilder.part("walls", GL20.GL_TRIANGLES, attrs, material)

        val n = polygon.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val g0 = groundPositions[i]
            val g1 = groundPositions[j]
            val r0 = roofPositions[i]
            val r1 = roofPositions[j]

            // Compute outward-facing normal for this wall quad
            val edge = Vector3(g1).sub(g0)
            val up = Vector3(r0).sub(g0)
            val normal = Vector3(edge).crs(up).nor()

            // Two triangles: g0-g1-r1 and g0-r1-r0
            wallBuilder.triangle(
                tmpVert(g0, normal), tmpVert(g1, normal), tmpVert(r1, normal)
            )
            wallBuilder.triangle(
                tmpVert(g0, normal), tmpVert(r1, normal), tmpVert(r0, normal)
            )
        }

        // --- Roof ---
        val roofBuilder: MeshPartBuilder = modelBuilder.part("roof", GL20.GL_TRIANGLES, attrs, material)

        // Project roof vertices to local 2D for ear-clipping triangulation
        // Use first vertex as origin, compute local X/Z axes on the tangent plane
        val origin = roofPositions[0]
        val localX = Vector3(roofPositions[1]).sub(origin).nor()
        // Approximate up vector from the roof positions (normal of the polygon plane)
        val roofEdge1 = Vector3(roofPositions[1]).sub(origin)
        val roofEdge2 = Vector3(roofPositions[polygon.size - 1]).sub(origin)
        val roofNormal = Vector3(roofEdge1).crs(roofEdge2).nor()
        val localZ = Vector3(roofNormal).crs(localX).nor()

        val flatVertices = FloatArray(polygon.size * 2)
        for (i in polygon.indices) {
            val rel = Vector3(roofPositions[i]).sub(origin)
            flatVertices[i * 2] = rel.dot(localX)
            flatVertices[i * 2 + 1] = rel.dot(localZ)
        }

        val indices = triangulator.computeTriangles(flatVertices)
        var k = 0
        while (k < indices.size) {
            val i0 = indices[k].toInt()
            val i1 = indices[k + 1].toInt()
            val i2 = indices[k + 2].toInt()
            roofBuilder.triangle(
                tmpVert(roofPositions[i0], roofNormal),
                tmpVert(roofPositions[i1], roofNormal),
                tmpVert(roofPositions[i2], roofNormal)
            )
            k += 3
        }

        return modelBuilder.end()
    }

    private val tmpInfo = MeshPartBuilder.VertexInfo()
    private val tmpInfo2 = MeshPartBuilder.VertexInfo()
    private val tmpInfo3 = MeshPartBuilder.VertexInfo()

    private fun tmpVert(pos: Vector3, normal: Vector3): MeshPartBuilder.VertexInfo {
        return MeshPartBuilder.VertexInfo().set(pos, normal, null, null)
    }
}
