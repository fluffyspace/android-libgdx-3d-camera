package com.mygdx.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.BitmapFontCache
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.mygdx.game.notbaza.Building
import com.mygdx.game.notbaza.LatLon
import com.mygdx.game.notbaza.Objekt
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


class MyGdxGame (
    var onDrawFrame: OnDrawFrame,
    var camera: Objekt,
    var objects: MutableList<Objekt>,
    var deviceCameraControl: DeviceCameraControl,
    var toggleEditMode: (Boolean) -> Unit,
    var onChange: (Boolean) -> Unit,
    var camHeightChange: (Vector3) -> Unit,
) : ApplicationAdapter() {
    private var cam: PerspectiveCamera? = null
    private var batch: SpriteBatch? = null
    private var mapSprite: Sprite? = null
    var selectedModel: Model? = null
    var modelBatch: ModelBatch? = null
    var environment: Environment? = null
    private val viewport: ExtendViewport? = null
    var tp = Vector3()
    var dragging = false
    var draggingHorizontal = false
    var draggingVertical = false
    var selectedObject = -1
    var instances: MutableList<ModelInstance> = mutableListOf()
    var isUserTouching = false
    var startTouch = MyPoint()
    var worldRotation = 0f
    var worldRotationTmp = 0f
    var cameraHeightOffset = 0f      // accumulated vertical offset in meters
    var cameraHeightOffsetTmp = 0f   // temporary offset during drag
    var modelMoving: MyPolar? = null
    var modelMovingVertical: Float = 0f
    var modelRotatingY: Float = 0f
    var modelRotatingX: Float = 0f
    var scalingObject: Float = 0f
    val dragTreshold = 50
    val upVector = Vector3(0f, 1f, 0f)
    val xVector = Vector3(1f, 0f, 0f)
    var font: BitmapFont? = null
    var fontSmall: BitmapFont? = null
    var text: String? = null
    var fontCaches: MutableList<BitmapFontCache> = mutableListOf()
    var noRender: Boolean = false
    private var debugFrameCounter = 0

    // Dual distance controls
    var minDistanceObjects = 0f
    var maxDistanceObjects = 1000f
    var noDistanceObjects = false

    var minDistanceBuildings = 0f
    var maxDistanceBuildings = 1000f
    var noDistanceBuildings = false

    // Nearby (OSM) buildings
    var buildingInstances: MutableList<ModelInstance> = mutableListOf()
    var buildingModels: MutableList<Model> = mutableListOf()
    var buildingData: MutableList<Building> = mutableListOf()
    private var buildingCentroids: MutableList<Vector3> = mutableListOf()
    private val buildingMeshGenerator = BuildingMeshGenerator()
    var buildingsVisible: Boolean = true
    var objectsOnTop: Boolean = true
    var buildingOpacity: Float = 0.35f
    var buildingDarkness: Float = 0.4f
    var selectedBuilding = -1
    var onBuildingChange: ((Building) -> Unit)? = null
    private var buildingHeightDragStart = 0f
    private var buildingHeightOriginal = 0f

    // Personal polygon objects (objects with OSM polygon data)
    var personalBuildingInstances: MutableList<ModelInstance> = mutableListOf()
    var personalBuildingModels: MutableList<Model> = mutableListOf()
    var personalBuildingObjectIndices: MutableList<Int> = mutableListOf()
    private var personalBuildingCentroids: MutableList<Vector3> = mutableListOf()
    private val polygonLineHeight = 30f
    private val clampedArcs = mutableListOf<List<Vector3>>()

    // Off-screen direction indicators
    private data class OffScreenIndicator(
        val name: String,
        val angleDegrees: Float,  // positive = right, negative = left
        val distance: Float
    )
    private val offScreenIndicators = mutableListOf<OffScreenIndicator>()

    // Vertices editor state
    var shapeRenderer: ShapeRenderer? = null
    var vertexWorldPositions: MutableList<Vector3> = mutableListOf()
    var vertexPolygonClosed: Boolean = false
    var vertexExtrudeHeight: Float = 10f
    var verticesEditorActive: Boolean = false
    private var vertexPreviewModel: Model? = null
    private var vertexPreviewInstance: ModelInstance? = null

    // Floor grid
    var showFloorGrid: Boolean = false
    var floorHeight: Float = 0f
    private var floorGridModel: Model? = null
    private var floorGridInstance: ModelInstance? = null
    private var lastFloorGridHeight: Float = -1f

    data class MyPoint(var x: Float = 0f, var y: Float = 0f)
    data class MyPolar(var radius: Float = 0f, var degrees: Float = 0f)
    data class Vector3Double(var x: Double, var y: Double, var z: Double)

    override fun resize(width: Int, height: Int) {
        // viewport must be updated for it to work properly
        viewport?.update(width, height, true)
    }

    enum class EditMode {
        move,
        move_vertical,
        rotate,
        scale,
        adjust_building_height
    }

    var editMode: EditMode? = null
    var cameraCartesian: Vector3 = Vector3()
    var latitude = camera.x
    lateinit var cameraEarthRotMatrix: Matrix4
    lateinit var normalVector: Vector3


    override fun create() {
        mapSprite = Sprite(Texture(Gdx.files.internal("badlogic.jpg")))
        mapSprite!!.setPosition(0f, 0f)
        mapSprite!!.setSize(WORLD_WIDTH.toFloat(), WORLD_HEIGHT.toFloat())
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()

        val generator = FreeTypeFontGenerator(Gdx.files.internal("font.ttf"))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
        parameter.size = 60
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "čćžšđČĆŽŠĐ"
        font = generator.generateFont(parameter)

        val parameterSmall = FreeTypeFontGenerator.FreeTypeFontParameter()
        parameterSmall.size = 36
        parameterSmall.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "čćžšđČĆŽŠĐ"
        fontSmall = generator.generateFont(parameterSmall)

        generator.dispose()

        // Constructs a new OrthographicCamera, using the given viewport width and height
        // Height is multiplied by aspect ratio.
        println("aba ${camera.x} ${camera.x.toDouble()} ${camera.y} ${camera.y.toDouble()} ${camera.z} ${camera.z.toDouble()} ")
        cameraCartesian = geoToCartesian(camera.x.toDouble(), camera.y.toDouble(), camera.z.toDouble())

        normalVector = calculateNormalVector(Vector3(cameraCartesian.x, cameraCartesian.z, cameraCartesian.y))

        // Build rotation matrix from ECEF game coords to local tangent plane (Y-up)
        cameraEarthRotMatrix = buildEarthRotationMatrix(
            Math.toRadians(camera.x.toDouble()),
            Math.toRadians(camera.y.toDouble())
        )

        cam = PerspectiveCamera(81f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        cam!!.position.set(Vector3(0f, 0f, 0f))
        cam!!.lookAt(1f, 0f, 0f)
        cam!!.near = 0.5f
        cam!!.far = 10000f
        cam!!.update()
        environment = Environment()
        environment!!.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment!!.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
        val modelBuilder = ModelBuilder()
        selectedModel = modelBuilder.createSphere(
            1f, 1f, 1f, 20, 20,
            Material(ColorAttribute.createDiffuse(Color.RED)),
            (
                    VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        modelBatch = ModelBatch()
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        updateCamera()
        createInstances()

        val lala = translatePoint(Vector3(10f, 0f, 10f), Vector3(0f, 5f, 0f), 5f)
        println("lala $lala")
    }

    val scalar = 10000f

    fun createInstances(){
        instances = mutableListOf()
        personalBuildingInstances.clear()
        personalBuildingModels.forEach { it.dispose() }
        personalBuildingModels.clear()
        personalBuildingObjectIndices.clear()
        personalBuildingCentroids.clear()
        updateObjectsCoordinates()
        println("ObjectDebug: createInstances() total objects=${objects.size}, camera=(${camera.x}, ${camera.y}, ${camera.z})")
        for((index, objekt) in objects.withIndex()){
            val dist = distance3D(Vector3(), Vector3(objekt.diffX, objekt.diffY, objekt.diffZ))
            val hasPolygon = objekt.polygon != null && objekt.polygon!!.size >= 3
            println("ObjectDebug: [$index] name='${objekt.name}' pos=(${objekt.x},${objekt.y},${objekt.z}) diff=(${objekt.diffX},${objekt.diffY},${objekt.diffZ}) dist=${dist}m polygon=$hasPolygon hidden=${objekt.hidden}")
            fontCaches.add(BitmapFontCache(font, false))
            if (hasPolygon) {
                // Polygon object: create building mesh with user color
                val building = Building(
                    id = objekt.osmId ?: 0L,
                    polygon = objekt.polygon!!,
                    heightMeters = objekt.heightMeters,
                    minHeightMeters = objekt.minHeightMeters
                )
                val color = Color(objekt.libgdxcolor.r, objekt.libgdxcolor.g, objekt.libgdxcolor.b, 0.8f)
                val model = buildingMeshGenerator.generate(building, cameraCartesian, ::geoToCartesian, color, 0.8f)
                if (model != null) {
                    personalBuildingModels.add(model)
                    personalBuildingInstances.add(ModelInstance(model))
                    personalBuildingObjectIndices.add(index)
                    // Compute roof-level centroid for vertical line / label
                    val centroidLat = objekt.polygon!!.map { it.lat }.average()
                    val centroidLon = objekt.polygon!!.map { it.lon }.average()
                    val roofAlt = (objekt.z + objekt.heightMeters).toDouble()
                    val centroidCart = geoToCartesian(centroidLat, centroidLon, roofAlt)
                    personalBuildingCentroids.add(Vector3(
                        centroidCart.x - cameraCartesian.x,
                        centroidCart.z - cameraCartesian.z,  // Y/Z swap
                        centroidCart.y - cameraCartesian.y
                    ))
                    println("ObjectDebug: [$index] polygon mesh generated OK")
                } else {
                    println("ObjectDebug: [$index] polygon mesh generation FAILED")
                }
                // Still add a sphere instance (invisible/placeholder) so indices stay aligned
                instances.add(ModelInstance(generateModelForObject(objekt.libgdxcolor)))
                updateModel(instances.lastIndex)
            } else {
                instances.add(ModelInstance(generateModelForObject(objekt.libgdxcolor)))
                updateModel(instances.lastIndex)
            }
        }
        println("ObjectDebug: createInstances() done: ${instances.size} sphere instances, ${personalBuildingInstances.size} polygon instances")
    }
    fun setBuildings(newBuildings: List<Building>) {
        // Dispose old building models
        for (model in buildingModels) {
            model.dispose()
        }
        buildingModels.clear()
        buildingInstances.clear()
        buildingData.clear()
        buildingCentroids.clear()
        selectedBuilding = -1

        for (building in newBuildings) {
            try {
                val model = buildingMeshGenerator.generate(building, cameraCartesian, ::geoToCartesian)
                if (model != null) {
                    buildingModels.add(model)
                    buildingInstances.add(ModelInstance(model))
                    buildingData.add(building)
                    // Precompute centroid offset for distance filtering
                    val centroidLat = building.polygon.map { it.lat }.average()
                    val centroidLon = building.polygon.map { it.lon }.average()
                    val centroidCart = geoToCartesian(centroidLat, centroidLon, 0.0)
                    buildingCentroids.add(Vector3(
                        centroidCart.x - cameraCartesian.x,
                        centroidCart.z - cameraCartesian.z,
                        centroidCart.y - cameraCartesian.y
                    ))
                }
            } catch (e: Exception) {
                // Skip degenerate buildings
            }
        }
    }

    lateinit var tmpObjectCartesian: Vector3
    fun updateObjectCoordinates(objekt: Objekt){
        tmpObjectCartesian = geoToCartesian(objekt.x.toDouble(), objekt.y.toDouble(), objekt.z.toDouble())
        // i'm replacing y and z because y is vertical axis and z is in real world coordinates.
        objekt.diffX = (tmpObjectCartesian.x - cameraCartesian.x)
        objekt.diffZ = (tmpObjectCartesian.y - cameraCartesian.y)
        objekt.diffY = (tmpObjectCartesian.z - cameraCartesian.z)
    }

    fun fromCartesianToGeo(){

    }

    fun updateObjectsCoordinates(){
        selectedObject = -1
        println("ingo camera $cameraCartesian")
        for((index, objekt) in objects.withIndex()){
            updateObjectCoordinates(objekt)
            //instances[index].add(ModelInstance(generateModelForObject(objekt.libgdxcolor)).apply { transform.setToTranslation(0f, 0f, 0f) })
        }
        toggleEditMode(false)
    }

    fun generateModelForObject(color: Color): Model{
        color.a = 0.4f
        return ModelBuilder().createSphere(
            1f, 1f, 1f, 20, 20,
            Material(ColorAttribute.createDiffuse(color)),
            (
                    VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
    }

    var quat = Quaternion()
    var camTranslatingVector = Vector3()
    fun updateCamera(){
        cam!!.apply {
            // Use ARCore's projection matrix which has correct FOV from camera intrinsics
            val arProjection = onDrawFrame.projectionMatrix
            projection.set(arProjection)

            // Camera position based on GPS coordinates only (no vertical slide adjustment)
            val newCoordinate = geoToCartesian(camera.x.toDouble(), camera.y.toDouble(), camera.z.toDouble())
            val cameraMoved = Vector3( newCoordinate.x-cameraCartesian.x, newCoordinate.z-cameraCartesian.z, newCoordinate.y-cameraCartesian.y)

            Matrix4(onDrawFrame.lastHeadView).getRotation(quat)
            val arTranslation = onDrawFrame.cameraTranslation
            camTranslatingVector = Vector3(
                cameraMoved.x,
                cameraMoved.y,
                cameraMoved.z
            )

            // Build view matrix:
            // Matrix chain order determines point transformation order (reverse).
            // Points are transformed: quat(arTranslate(worldRot(heightShift(earthRot(translate(p))))))
            // 1. translate (GPS camera position offset)
            // 2. cameraEarthRot (earth curvature correction)
            // 3. heightShift (virtual height offset - moves camera up/down in local tangent plane)
            // 4. worldRotation (user's compass correction) - aligns geographic to ARCore world
            // 5. arTranslate (ARCore positional tracking - physical movement from session start)
            // 6. quat (device orientation from ARCore) - transforms to camera space
            val totalHeightOffset = cameraHeightOffset + cameraHeightOffsetTmp
            view.set(
                Matrix4()
                    .rotate(quat)
                    .translate(-arTranslation[0], -arTranslation[1], -arTranslation[2])
                    .rotate(upVector, worldRotation + worldRotationTmp)
                    .translate(0f, -totalHeightOffset, 0f)
                    .mul(cameraEarthRotMatrix)
                    .translate(Vector3(camTranslatingVector.x, camTranslatingVector.y, camTranslatingVector.z))
            )
            combined.set(projection)
            Matrix4.mul(combined.`val`, view.`val`)

            invProjectionView.set(combined)
            Matrix4.inv(invProjectionView.`val`)
            frustum.update(invProjectionView)

            // The earth rotation matrix has det=-1 (maps Z to South instead of North
            // to align with OpenGL/ARCore's -Z forward convention). This reflection
            // flips the frustum planes inside-out. Fix by negating all plane normals.
            for (plane in frustum.planes) {
                plane.normal.scl(-1f)
                plane.d = -plane.d
            }
        }
        camHeightChange(Vector3(camTranslatingVector.x, camTranslatingVector.y, camTranslatingVector.z))
    }

    // Function to calculate the normal vector to the Earth's surface at a given location
    fun calculateNormalVector(cameraPosition: Vector3): Vector3 {
        // Assuming Earth's center is at (0, 0, 0) in ECEF coordinates
        return Vector3(cameraPosition).nor()
    }

    /**
     * Build rotation matrix from ECEF game coords (Y/Z swapped) to local tangent plane.
     * After this rotation: X=East, Y=Up, Z=North.
     *
     * Game coordinate mapping: game-X = ECEF-X, game-Y = ECEF-Z, game-Z = ECEF-Y
     */
    fun buildEarthRotationMatrix(latRad: Double, lonRad: Double): Matrix4 {
        val sinLat = sin(latRad).toFloat()
        val cosLat = cos(latRad).toFloat()
        val sinLon = sin(lonRad).toFloat()
        val cosLon = cos(lonRad).toFloat()

        // Row 0 (East):  local East direction in game coords = (-sinLon, 0, cosLon)
        // Row 1 (Up):    surface normal in game coords = (cosLat*cosLon, sinLat, cosLat*sinLon)
        // Row 2 (South): negated North so +Z = South, aligning with OpenGL/ARCore -Z = forward = North
        val vals = FloatArray(16)
        // Column-major order for LibGDX Matrix4
        vals[Matrix4.M00] = -sinLon;           vals[Matrix4.M01] = 0f;      vals[Matrix4.M02] = cosLon
        vals[Matrix4.M10] = cosLat * cosLon;   vals[Matrix4.M11] = sinLat;  vals[Matrix4.M12] = cosLat * sinLon
        vals[Matrix4.M20] = sinLat * cosLon;   vals[Matrix4.M21] = -cosLat; vals[Matrix4.M22] = sinLat * sinLon
        vals[Matrix4.M03] = 0f; vals[Matrix4.M13] = 0f; vals[Matrix4.M23] = 0f
        vals[Matrix4.M30] = 0f; vals[Matrix4.M31] = 0f; vals[Matrix4.M32] = 0f; vals[Matrix4.M33] = 1f
        return Matrix4(vals)
    }

    fun updateModel(index: Int){
        val objekt = objects[index]
        lateinit var translatingVector: Vector3
        if (modelMoving != null) {
            val myPoint = getObjectsXZAfterRot(objekt)
            translatingVector = Vector3(
                myPoint.x,
                myPoint.z,
                myPoint.y
            )
        } else if(modelMovingVertical != 0f) {
            translatingVector = getObjectsXZAfterVerticalTranslation(objekt)
        } else {
            println("not currently edited")
            translatingVector = Vector3(
                objekt.diffX,
                objekt.diffY,
                objekt.diffZ
            )
        }
        //val normalVector: Vector3 = calculateNormalVector(Vector3(objekt.diffX, objekt.diffZ, objekt.diffY))

        // Align the camera orientation with the normal vector using quaternions

        // Align the camera orientation with the normal vector using quaternions
        //val objEarthRot = alignCameraOrientation(normalVector)
        //instances[index].transform.set(Matrix4())
        instances[index].transform.set(
            Matrix4().translate(translatingVector)
                .rotate(upVector, modelRotatingY + objekt.rotationY)
                .rotate(xVector, modelRotatingX + objekt.rotationX)
                //.rotate(objEarthRot)
                .scale(
                    objekt.size + scalingObject,
                    objekt.size + scalingObject,
                    objekt.size + scalingObject
                )
        )
    }

    // Earth radius in meters
    private val EARTH_RADIUS = 6371000.0

    // Convert latitude, longitude, and altitude to Cartesian coordinates
    fun geoToCartesian(latitude: Double, longitude: Double, altitude: Double): Vector3 {
        //return Vector3(latitude.toFloat(), longitude.toFloat(), altitude.toFloat())
        // Convert latitude and longitude to radians
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)

        // Calculate Earth's radius at given latitude
        val radius = EARTH_RADIUS + altitude

        // Convert to Cartesian coordinates
        val x = radius * cos(latRad) * cos(lonRad)
        val y = radius * cos(latRad) * sin(lonRad)
        val z = radius * sin(latRad)
        //println("abaaba $x ${x.toFloat()} $y ${y.toFloat()} $z ${z.toFloat()}")
        return Vector3(x.toFloat(), y.toFloat(), z.toFloat())
    }

    // Inverse of geoToCartesian: ECEF → lat/lon/alt (spherical approximation)
    fun cartesianToGeo(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        val lon = Math.toDegrees(atan2(y, x))
        val lat = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
        val alt = sqrt(x * x + y * y + z * z) - EARTH_RADIUS
        return Triple(lat, lon, alt)
    }

    /**
     * Convert an ARCore hit-test world point to geographic coordinates.
     * ARCore point is in ARCore world space; we reverse the view matrix transforms
     * (minus the quat/device orientation) to get back to our rendering world space,
     * then convert to ECEF and finally to lat/lon/alt.
     */
    fun arHitToGeo(arX: Float, arY: Float, arZ: Float): Triple<Double, Double, Double> {
        val arPoint = Vector3(arX, arY, arZ)

        // Build inverse of: R_worldRot * R_earthRot * T_cam
        // = T_cam^(-1) * R_earthRot^(-1) * R_worldRot^(-1)
        val invEarthRot = Matrix4(cameraEarthRotMatrix).inv()
        val inverseMatrix = Matrix4()
            .rotate(upVector, -(worldRotation + worldRotationTmp))
            .mul(invEarthRot)
            .translate(-camTranslatingVector.x, -camTranslatingVector.y, -camTranslatingVector.z)

        val worldPoint = Vector3(arPoint)
        worldPoint.mul(inverseMatrix)

        // Convert from world space (diffX/Y/Z) to ECEF
        // diffX = ecefX - camX, diffZ = ecefY - camY, diffY = ecefZ - camZ
        val ecefX = worldPoint.x.toDouble() + cameraCartesian.x.toDouble()
        val ecefY = worldPoint.z.toDouble() + cameraCartesian.y.toDouble()
        val ecefZ = worldPoint.y.toDouble() + cameraCartesian.z.toDouble()

        return cartesianToGeo(ecefX, ecefY, ecefZ)
    }

    /**
     * Project the camera center ray at a given distance and return geographic coordinates.
     * Used as fallback when no ARCore surface hit is detected.
     */
    fun getCenterRayGeo(distanceMeters: Float): Triple<Double, Double, Double> {
        val ray = cam!!.getPickRay(Gdx.graphics.width / 2f, Gdx.graphics.height / 2f)
        // Point along the ray at the given distance
        val worldPoint = Vector3(ray.direction).scl(distanceMeters).add(ray.origin)

        // Convert from rendering world space to ECEF (reverse Y/Z swap + camera offset)
        val ecefX = worldPoint.x.toDouble() + cameraCartesian.x.toDouble()
        val ecefY = worldPoint.z.toDouble() + cameraCartesian.y.toDouble()  // renderZ -> ecefY
        val ecefZ = worldPoint.y.toDouble() + cameraCartesian.z.toDouble()  // renderY -> ecefZ

        return cartesianToGeo(ecefX, ecefY, ecefZ)
    }

    /**
     * Convert a LatLon vertex to rendering world-space position.
     */
    fun geoToWorldPosition(latLon: LatLon, altitude: Double = 0.0): Vector3 {
        val cart = geoToCartesian(latLon.lat, latLon.lon, altitude)
        return Vector3(
            cart.x - cameraCartesian.x,
            cart.z - cameraCartesian.z,  // Y/Z swap
            cart.y - cameraCartesian.y
        )
    }

    fun updateVertexPositions(vertices: List<LatLon>) {
        vertexWorldPositions.clear()
        for (v in vertices) {
            vertexWorldPositions.add(geoToWorldPosition(v))
        }
        updateVertexPreview(vertices)
    }

    private fun updateVertexPreview(vertices: List<LatLon>) {
        vertexPreviewModel?.dispose()
        vertexPreviewModel = null
        vertexPreviewInstance = null

        if (!vertexPolygonClosed || vertices.size < 3) return

        val building = Building(
            id = 0L,
            polygon = vertices,
            heightMeters = vertexExtrudeHeight,
            minHeightMeters = 0f
        )
        try {
            val color = if (selectedObject >= 0 && selectedObject < objects.size)
                Color(objects[selectedObject].libgdxcolor.r, objects[selectedObject].libgdxcolor.g, objects[selectedObject].libgdxcolor.b, 0.5f)
            else Color(0.3f, 0.8f, 0.3f, 0.5f)
            val model = buildingMeshGenerator.generate(building, cameraCartesian, ::geoToCartesian, color, 0.5f)
            if (model != null) {
                vertexPreviewModel = model
                vertexPreviewInstance = ModelInstance(model)
            }
        } catch (_: Exception) {}
    }

    fun clearVertexEditor() {
        vertexWorldPositions.clear()
        vertexPolygonClosed = false
        vertexExtrudeHeight = 10f
        verticesEditorActive = false
        vertexPreviewModel?.dispose()
        vertexPreviewModel = null
        vertexPreviewInstance = null
    }

    private fun updateFloorGrid() {
        if (!showFloorGrid || floorHeight <= 0f) {
            floorGridInstance = null
            return
        }
        // Only regenerate if height changed significantly
        if (abs(floorHeight - lastFloorGridHeight) < 0.05f && floorGridInstance != null) return
        lastFloorGridHeight = floorHeight

        floorGridModel?.dispose()
        floorGridModel = createFloorGridModel(-floorHeight)
        floorGridInstance = ModelInstance(floorGridModel)
    }

    private fun createFloorGridModel(floorY: Float): Model {
        val gridSize = 20f // 20m in each direction
        val spacing = 1f   // 1m spacing
        val gridColor = Color(1f, 1f, 1f, 0.35f)

        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val material = Material(
            ColorAttribute.createDiffuse(gridColor),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )
        val mpb: MeshPartBuilder = modelBuilder.part(
            "grid", GL20.GL_LINES,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
            material
        )

        // Lines along X axis (varying Z)
        var z = -gridSize
        while (z <= gridSize) {
            mpb.line(-gridSize, floorY, z, gridSize, floorY, z)
            z += spacing
        }
        // Lines along Z axis (varying X)
        var x = -gridSize
        while (x <= gridSize) {
            mpb.line(x, floorY, -gridSize, x, floorY, gridSize)
            x += spacing
        }

        return modelBuilder.end()
    }

    private fun isObjectInDistanceRange(objekt: Objekt): Boolean {
        if (noDistanceObjects) return true
        val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
        val dist = distance3D(camTranslatingVector, objektPos)
        return dist in minDistanceObjects..maxDistanceObjects
    }

    private fun isBuildingInDistanceRange(buildingIndex: Int): Boolean {
        if (noDistanceBuildings) return true
        val pos = buildingCentroids[buildingIndex]
        val dist = distance3D(camTranslatingVector, pos)
        return dist in minDistanceBuildings..maxDistanceBuildings
    }

    /**
     * Compute the horizontal angle from camera forward to the given world position.
     * Returns null if the object is on-screen, otherwise returns degrees (positive = right, negative = left).
     */
    private fun computeOffScreenAngle(worldPos: Vector3): Float? {
        val screenPos = Vector3(worldPos)
        cam!!.project(screenPos)
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        if (screenPos.z <= 1f && screenPos.x in 0f..w && screenPos.y in 0f..h) {
            return null // on-screen
        }
        // Transform to camera view space
        val viewSpace = Vector3(worldPos).mul(cam!!.view)
        return Math.toDegrees(atan2(viewSpace.x.toDouble(), (-viewSpace.z).toDouble())).toFloat()
    }

    private fun formatDistance(meters: Float): String {
        return if (meters < 1000f) {
            "%.1f m".format(meters)
        } else {
            "%.2f km".format(meters / 1000f)
        }
    }

    /**
     * Generate a curvature arc from camera toward the clamped object position.
     * Horizontal positions are linearly interpolated from camera to clampedPos,
     * with a downward Y offset at each point representing Earth's surface curvature
     * drop: h = d_actual² / (2R). This makes the curvature visually apparent.
     */
    private fun interpolateGreatCircleArc(
        clampedPos: Vector3,
        segments: Int,
        actualDist: Float
    ): List<Vector3> {
        val points = mutableListOf<Vector3>()
        for (i in 0..segments) {
            val f = i.toFloat() / segments
            // Linear interpolation from camera to clamped position
            val x = camTranslatingVector.x + (clampedPos.x - camTranslatingVector.x) * f
            val y = camTranslatingVector.y + (clampedPos.y - camTranslatingVector.y) * f
            val z = camTranslatingVector.z + (clampedPos.z - camTranslatingVector.z) * f
            // Earth curvature drop: at real-world distance d, surface drops by d²/(2R)
            val dActual = actualDist * f
            val curvatureDrop = (dActual * dActual) / (2f * EARTH_RADIUS.toFloat())
            points.add(Vector3(x, y - curvatureDrop, z))
        }
        return points
    }

    override fun render() {
        if(noRender) return
        touchHandler()

        updateCamera()

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        // Only clear depth buffer - ARCore camera background is already rendered
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)

        // Track which objects are polygon-based (should not render sphere)
        val polygonObjectIndices = personalBuildingObjectIndices.toSet()
        clampedArcs.clear()
        offScreenIndicators.clear()

        for((index, objekt) in objects.withIndex()){
            if (objekt.hidden) {
                objekt.visible = false
                continue
            }
            if (!isObjectInDistanceRange(objekt)) {
                objekt.visible = false
                continue
            }

            val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
            val actualDist = distance3D(camTranslatingVector, objektPos)
            val isFarClamped = actualDist > FAR_CLAMP_RENDER_DISTANCE

            // Polygon objects
            if (index in polygonObjectIndices) {
                if (isFarClamped) {
                    // Far polygon: render as clamped sphere instead of building mesh
                    val direction = Vector3(objektPos).sub(camTranslatingVector).nor()
                    val clampedPos = Vector3(camTranslatingVector).add(
                        Vector3(direction).scl(FAR_CLAMP_RENDER_DISTANCE)
                    )
                    instances[index].transform.set(
                        Matrix4().translate(clampedPos)
                            .scale(objekt.size, objekt.size, objekt.size)
                    )
                    objekt.visible = cam!!.frustum.sphereInFrustum(clampedPos, objekt.size)
                    if (objekt.visible) {
                        // Curvature arc
                        val arc = interpolateGreatCircleArc(clampedPos, CURVATURE_ARC_SEGMENTS, actualDist)
                        if (arc.size >= 2) clampedArcs.add(arc)
                        showObjectsName(index)
                    } else {
                        val angle = computeOffScreenAngle(clampedPos)
                        if (angle != null) {
                            offScreenIndicators.add(OffScreenIndicator(objekt.name, angle, actualDist))
                        }
                    }
                } else {
                    objekt.visible = true
                    showPolygonObjectName(index)
                }
                continue
            }

            // Non-polygon (sphere) objects
            if (isFarClamped) {
                val direction = Vector3(objektPos).sub(camTranslatingVector).nor()
                val clampedPos = Vector3(camTranslatingVector).add(
                    Vector3(direction).scl(FAR_CLAMP_RENDER_DISTANCE)
                )
                instances[index].transform.set(
                    Matrix4().translate(clampedPos)
                        .rotate(upVector, objekt.rotationY)
                        .rotate(xVector, objekt.rotationX)
                        .scale(objekt.size, objekt.size, objekt.size)
                )
                objekt.visible = cam!!.frustum.sphereInFrustum(clampedPos, objekt.size)
                if (!objekt.visible) {
                    val angle = computeOffScreenAngle(clampedPos)
                    if (angle != null) {
                        offScreenIndicators.add(OffScreenIndicator(objekt.name, angle, actualDist))
                    }
                    continue
                }

                // Curvature arc
                val arc = interpolateGreatCircleArc(clampedPos, CURVATURE_ARC_SEGMENTS, actualDist)
                if (arc.size >= 2) clampedArcs.add(arc)
            } else {
                objekt.visible = isVisible(cam!!, index)
                if (!objekt.visible) {
                    val objektPos2 = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
                    val angle = computeOffScreenAngle(objektPos2)
                    if (angle != null) {
                        offScreenIndicators.add(OffScreenIndicator(objekt.name, angle, actualDist))
                    }
                    continue
                }
            }

            try{
                if(index != selectedObject){
                    showObjectsName(index)
                    continue
                }

                var showingTransformInfo = false
                when (editMode) {
                    EditMode.move -> {
                        if (modelMoving != null) {
                            makeTextForObject(index) { pos, rot ->
                                "X: ${pos.x.roundToInt()}, Y: ${pos.y.roundToInt()}"
                            }
                            showingTransformInfo = true
                        }
                    }

                    EditMode.move_vertical -> {
                        if (modelMoving != null) {
                            makeTextForObject(index) { pos, rot ->
                                "Z: ${pos.z.roundToInt()}"
                            }
                            showingTransformInfo = true
                        }
                    }

                    EditMode.rotate -> {
                        if (modelRotatingX != 0f) {
                            makeTextForObject(index) { pos, rot ->
                                "X: ${rot.y.roundToInt()}"
                            }
                            showingTransformInfo = true
                        } else if (modelRotatingY != 0f) {
                            makeTextForObject(index) { pos, rot ->
                                "Y: ${rot.x.roundToInt()}"
                            }
                            showingTransformInfo = true
                        }
                    }

                    EditMode.scale -> {
                        if (scalingObject != 0f) {
                            makeTextForObject(index) { pos, rot ->
                                usingKotlinStringFormat(
                                    (objekt.size + scalingObject),
                                    2
                                )
                            }
                            showingTransformInfo = true
                        }
                    }

                    else -> {}
                }
                if (!showingTransformInfo) {
                    showObjectsName(index)
                }
            } catch (e: java.lang.IndexOutOfBoundsException) {

            }
        }

        // Periodic debug log for object visibility
        debugFrameCounter++
        if (debugFrameCounter % 120 == 0) {
            val visibleCount = objects.count { it.visible }
            val hiddenCount = objects.count { it.hidden }
            val polyCount = personalBuildingObjectIndices.size
            println("ObjectDebug: render frame=$debugFrameCounter visible=$visibleCount/${objects.size} hidden=$hiddenCount polygon=$polyCount noDistLimit=$noDistanceObjects worldRot=$worldRotation")
            for ((index, objekt) in objects.withIndex()) {
                val dist = distance3D(camTranslatingVector, Vector3(objekt.diffX, objekt.diffY, objekt.diffZ))
                val inFrustum = if (index < instances.size) isVisible(cam!!, index) else false
                val inRange = isObjectInDistanceRange(objekt)
                val isPoly = index in polygonObjectIndices
                println("ObjectDebug:   [$index] '${objekt.name}' dist=${dist.toInt()}m frustum=$inFrustum range=$inRange visible=${objekt.visible} poly=$isPoly hidden=${objekt.hidden}")
            }
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // === Pass 1: Nearby OSM buildings, vertex preview, floor grid (normal depth) ===
        modelBatch!!.begin(cam)
        if (buildingsVisible) {
            for ((index, instance) in buildingInstances.withIndex()) {
                if (!isBuildingInDistanceRange(index)) continue
                modelBatch!!.render(instance, environment)
            }
        }
        if (vertexPreviewInstance != null) {
            modelBatch!!.render(vertexPreviewInstance, environment)
        }
        updateFloorGrid()
        if (floorGridInstance != null) {
            modelBatch!!.render(floorGridInstance, environment)
        }
        modelBatch!!.end()

        // Clear depth so personal objects render on top of nearby buildings
        if (objectsOnTop) {
            Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)
        }

        // === Pass 2: Personal objects (spheres + polygon buildings) ===
        modelBatch!!.begin(cam)
        for((index, instance) in instances.withIndex()) {
            if (index < objects.size && !objects[index].visible) continue
            if (index in polygonObjectIndices) {
                // Only render sphere placeholder for far-clamped polygon objects
                val objekt = objects[index]
                val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
                val dist = distance3D(camTranslatingVector, objektPos)
                if (dist > FAR_CLAMP_RENDER_DISTANCE) {
                    modelBatch!!.render(instance, environment)
                }
                continue
            }
            modelBatch!!.render(instance, environment)
        }
        for ((i, instance) in personalBuildingInstances.withIndex()) {
            val objIndex = personalBuildingObjectIndices[i]
            val objekt = objects[objIndex]
            if (objekt.hidden) continue
            if (!isObjectInDistanceRange(objekt)) continue
            // Skip building mesh for far-clamped polygon objects (rendered as sphere above)
            val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
            val dist = distance3D(camTranslatingVector, objektPos)
            if (dist > FAR_CLAMP_RENDER_DISTANCE) continue
            modelBatch!!.render(instance, environment)
        }
        modelBatch!!.end()

        // Draw vertical marker lines above personal polygon buildings (only for near ones)
        if (personalBuildingCentroids.isNotEmpty()) {
            Gdx.gl.glLineWidth(3f)
            shapeRenderer!!.projectionMatrix = cam!!.combined
            shapeRenderer!!.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer!!.color = Color.WHITE
            for ((i, centroid) in personalBuildingCentroids.withIndex()) {
                val objIndex = personalBuildingObjectIndices[i]
                val objekt = objects[objIndex]
                if (!objekt.visible) continue
                // Skip vertical line for far-clamped polygon objects
                val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
                val dist = distance3D(camTranslatingVector, objektPos)
                if (dist > FAR_CLAMP_RENDER_DISTANCE) continue
                val top = Vector3(centroid.x, centroid.y + polygonLineHeight, centroid.z)
                shapeRenderer!!.line(centroid, top)
            }
            shapeRenderer!!.end()
        }

        // Render vertex editor wireframe
        if (verticesEditorActive && vertexWorldPositions.size >= 1) {
            Gdx.gl.glLineWidth(3f)
            shapeRenderer!!.projectionMatrix = cam!!.combined
            shapeRenderer!!.begin(ShapeRenderer.ShapeType.Line)
            // Draw lines between vertices
            shapeRenderer!!.color = Color.GREEN
            for (i in 0 until vertexWorldPositions.size - 1) {
                val a = vertexWorldPositions[i]
                val b = vertexWorldPositions[i + 1]
                shapeRenderer!!.line(a, b)
            }
            // Draw closing line if polygon is closed
            if (vertexPolygonClosed && vertexWorldPositions.size >= 3) {
                val first = vertexWorldPositions.first()
                val last = vertexWorldPositions.last()
                shapeRenderer!!.line(last, first)
            }
            shapeRenderer!!.end()
            // Draw vertex dots
            shapeRenderer!!.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer!!.color = Color.YELLOW
            for (pos in vertexWorldPositions) {
                // Draw a small sphere-like dot by using a circle facing the camera
                val screenPos = Vector3(pos)
                cam!!.project(screenPos)
                // We'll use a simple approach: render a small box at each vertex position
            }
            shapeRenderer!!.end()
        }

        // Draw curvature arcs for far-clamped objects
        if (clampedArcs.isNotEmpty()) {
            Gdx.gl.glLineWidth(2f)
            shapeRenderer!!.projectionMatrix = cam!!.combined
            shapeRenderer!!.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer!!.color = Color.YELLOW
            for (arc in clampedArcs) {
                for (i in 0 until arc.size - 1) {
                    shapeRenderer!!.line(arc[i], arc[i + 1])
                }
            }
            shapeRenderer!!.end()
        }

        batch!!.begin()
        try {
            fontCaches.forEachIndexed { index, fontCache ->
                if (objects[index].visible) {
                    fontCache.draw(batch)
                }
            }
        } catch (e: java.lang.ArrayIndexOutOfBoundsException){

        }
        batch!!.end()

        // Draw virtual height indicator (upper left corner)
        val totalHeight = cameraHeightOffset + cameraHeightOffsetTmp
        if (totalHeight != 0f) {
            batch!!.begin()
            val heightText = "%.1f m".format(totalHeight)
            fontSmall!!.draw(batch, heightText, 20f, Gdx.graphics.height - 20f)
            batch!!.end()
        }

        // Draw off-screen direction indicators
        if (offScreenIndicators.isNotEmpty()) {
            batch!!.begin()
            val screenW = Gdx.graphics.width.toFloat()
            val screenH = Gdx.graphics.height.toFloat()
            val margin = 20f
            val lineSpacing = fontSmall!!.lineHeight * 1.2f

            val leftIndicators = offScreenIndicators.filter { it.angleDegrees < 0 }
                .sortedBy { abs(it.angleDegrees) }
            val rightIndicators = offScreenIndicators.filter { it.angleDegrees >= 0 }
                .sortedBy { abs(it.angleDegrees) }

            // Left side indicators
            var y = screenH / 2f + (leftIndicators.size - 1) * lineSpacing / 2f
            for (indicator in leftIndicators) {
                val angleText = abs(indicator.angleDegrees).roundToInt()
                val distText = formatDistance(indicator.distance)
                val label = "< ${angleText}\u00B0  ${indicator.name}  $distText"
                fontSmall!!.draw(batch, label, margin, y)
                y -= lineSpacing
            }

            // Right side indicators
            val glyphLayout = GlyphLayout()
            y = screenH / 2f + (rightIndicators.size - 1) * lineSpacing / 2f
            for (indicator in rightIndicators) {
                val angleText = abs(indicator.angleDegrees).roundToInt()
                val distText = formatDistance(indicator.distance)
                val label = "${indicator.name}  $distText  ${angleText}\u00B0 >"
                glyphLayout.setText(fontSmall, label)
                fontSmall!!.draw(batch, label, screenW - margin - glyphLayout.width, y)
                y -= lineSpacing
            }
            batch!!.end()
        }
    }

    fun isVisible(cam: Camera, index: Int): Boolean {
        val pos = Vector3()
        instances[index].transform.getTranslation(pos)
        return cam.frustum.sphereInFrustum(pos, objects[index].size)
    }

    fun usingKotlinStringFormat(input: Float, scale: Int) = "%.${scale}f".format(input)

    private fun showObjectsName(index: Int) {
        val objekt = objects[index]
        val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
        val actualDist = distance3D(camTranslatingVector, objektPos)

        makeTextForObject(index){ pos, rot ->
            objekt.name + "\n" + formatDistance(actualDist)
        }
    }

    private fun showPolygonObjectName(objectIndex: Int) {
        val objekt = objects[objectIndex]
        // Find the matching personal building centroid
        val pbIdx = personalBuildingObjectIndices.indexOf(objectIndex)
        if (pbIdx < 0 || pbIdx >= personalBuildingCentroids.size) return

        val centroid = personalBuildingCentroids[pbIdx]
        // Top of the vertical marker line
        val lineTop = Vector3(centroid.x, centroid.y + polygonLineHeight, centroid.z)
        val screenPos = Vector3(lineTop)
        cam!!.project(screenPos)

        // Behind camera check
        if (screenPos.z > 1f) return

        val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
        val dist = distance3D(camTranslatingVector, objektPos)
        val label = objekt.name + "\n" + formatDistance(dist)

        // Offset Y so the label bottom sits at the building top (setText draws downward from y)
        val textHeight = font!!.lineHeight * 2  // 2 lines: name + distance
        try {
            fontCaches[objectIndex].setText(label, screenPos.x, screenPos.y + textHeight, 0f, Align.center, false)
        } catch (_: Exception) {}
    }

    fun projectObject(model: ModelInstance): MyPoint{
        var worldPosition: Vector3 = Vector3()
        model.transform.getTranslation(worldPosition)
        cam!!.project(worldPosition).let{
            return MyPoint(it.x, it.y)
        }
    }

    fun translatePoint(
        sourcePoint: Vector3,
        targetPoint: Vector3,
        distance: Float
    ): Vector3 {
        // Calculate the vector from source to target
        val direction: Vector3 = normalizeVector(Vector3(targetPoint).sub(sourcePoint))
        val newdi = Vector3(direction.x*distance, direction.y*distance, direction.z*distance)
        println("$newdi $distance")
        println("${sourcePoint.x + newdi.x},${sourcePoint.y + newdi.y},${sourcePoint.z + newdi.z}")

        // Add the scaled direction vector to the source point to get the new position
        return Vector3(sourcePoint.x + newdi.x,sourcePoint.y + newdi.y,sourcePoint.z + newdi.z)
    }

    fun translatePointDouble(
        sourcePoint: Vector3,
        targetPoint: Vector3,
        distance: Float
    ): Vector3Double {
        // Calculate the vector from source to target
        val direction: Vector3 = normalizeVector(Vector3(targetPoint).sub(sourcePoint))
        val newdi = Vector3(direction.x*distance, direction.y*distance, direction.z*distance)
        println("$newdi $distance")
        println("${sourcePoint.x + newdi.x},${sourcePoint.y + newdi.y},${sourcePoint.z + newdi.z}")

        // Add the scaled direction vector to the source point to get the new position
        return Vector3Double((sourcePoint.x.toDouble() + newdi.x.toDouble()),sourcePoint.y.toDouble() + newdi.y.toDouble(),sourcePoint.z.toDouble() + newdi.z.toDouble())
    }

    fun normalizeVector(vector: Vector3): Vector3 {
        val magnitude = kotlin.math.sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)
        //println("$magnitude $vector")
        return if (magnitude != 0f) {
            Vector3(vector.x / magnitude, vector.y / magnitude, vector.z / magnitude)
        } else {
            Vector3(0f, 0f, 0f) // Return zero vector if the magnitude is zero to avoid division by zero
        }
    }

    fun makeTextForObject(index: Int, toText: (Vector3, Vector3) -> String){
        val model = instances[index]
        val screenPosition = projectObject(model)

        var worldPosition: Vector3 = Vector3()
        var worldRotation: Quaternion = Quaternion()
        worldPosition = Vector3(objects[index].diffX,objects[index].diffY,objects[index].diffZ)
        //model.transform.getTranslation(worldPosition)
        model.transform.getRotation(worldRotation)
        val rotationInAngles = Vector3(worldRotation.yaw, worldRotation.roll, worldRotation.pitch)

        try{
            fontCaches[index].setText(toText(worldPosition, rotationInAngles), screenPosition.x, screenPosition.y, 0f, Align.center, false);
        } catch (e: java.lang.ArrayIndexOutOfBoundsException){

        }
        //this.text = toText(worldPosition)
    }

    fun getObjectsWithLimitedDistance(objekt: Objekt): Vector3{
        val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
        var dist = distance3D(camTranslatingVector, objektPos)
        if(dist <= 50f) return objektPos
        val subtractedDistance = -50 + dist
        println("getObjectsWithLimitedDistance $objektPos $camTranslatingVector $subtractedDistance")
        return translatePoint(objektPos, camTranslatingVector, subtractedDistance)
    }

    fun getObjectsXZAfterVerticalTranslation(objekt: Objekt): Vector3{

        var newpoint2 = Vector3(
            cameraCartesian.x + objekt.diffX,
            cameraCartesian.y + objekt.diffZ,
            cameraCartesian.z + objekt.diffY,
        )
        val newpoint = translatePointDouble(newpoint2, Vector3(), modelMovingVertical)
        //println("$newpoint")
        return Vector3(
            (newpoint.x - cameraCartesian.x.toDouble()).toFloat(),
            (newpoint.z - cameraCartesian.z.toDouble()).toFloat(),
            (newpoint.y - cameraCartesian.y.toDouble()).toFloat(),
        )
    }

    fun getObjectsXZAfterRot(objekt: Objekt): Vector3{
        val geo = Vector3(objekt.diffX, objekt.diffZ, objekt.diffY)

        val newpoint = translatePoint(geo, camTranslatingVector, -modelMoving!!.radius)
        //return newpoint
        return rotatePointAroundPoint(newpoint, camTranslatingVector, modelMoving!!.degrees)
    }

    fun rotatePointAroundPoint(
        pointToRotate: Vector3,
        rotationCenter: Vector3,
        angleDegrees: Float
    ): Vector3 {
        val quaternion: Quaternion = Quaternion().set(Vector3(normalVector.x, normalVector.z, normalVector.y), angleDegrees)
        return quaternion.transform(pointToRotate.sub(rotationCenter)).add(rotationCenter);
    }

    fun dragTresholdOnAxisCheck(){
        if(abs(Gdx.input.x - startTouch.x) >= dragTreshold){
            draggingHorizontal = true
            dragging = true
            startTouch = MyPoint(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        } else if(abs(Gdx.input.y - startTouch.y) >= dragTreshold){
            draggingVertical = true
            dragging = true
            startTouch = MyPoint(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        }
    }

    fun panAround(){
        if(!dragging){
            dragTresholdOnAxisCheck()
        }
        if(draggingHorizontal) {
            // Scale rotation so a full-screen swipe = 90 degrees (consistent across resolutions)
            worldRotationTmp = -(Gdx.input.x - startTouch.x) * 90f / Gdx.graphics.width
        } else if(draggingVertical) {
            // Vertical slide changes virtual camera height (swipe up = go higher)
            // Full-screen swipe = 50 meters
            cameraHeightOffsetTmp = -(Gdx.input.y - startTouch.y) * 50f / Gdx.graphics.height
        }
    }

    fun dragTresholdReached(): Boolean{
        return abs(Gdx.input.x - startTouch.x) >= dragTreshold || abs(Gdx.input.y - startTouch.y) >= dragTreshold
    }

    fun rotateObject(){
        if(!dragging){
            dragTresholdOnAxisCheck()
        }
        if(draggingHorizontal) {
            modelRotatingY = (Gdx.input.x - startTouch.x)
        } else if(draggingVertical){
            modelRotatingX = (Gdx.input.y - startTouch.y)
        }
    }

    fun scaleObject(){
        if(!dragging){
            dragTresholdOnAxisCheck()
        }
        if(draggingHorizontal) {
            scalingObject = (Gdx.input.x - startTouch.x)/100
        } else if(draggingVertical){
            scalingObject = -(Gdx.input.y - startTouch.y)/100
        }
        if(objects[selectedObject].size+scalingObject < 0) scalingObject = -objects[selectedObject].size
    }

    fun moveObjectVertical(){
        if(!dragging){
            dragTresholdOnAxisCheck()
            return
        }
        if(draggingVertical) {
            modelMovingVertical = -(Gdx.input.y - startTouch.y) / 10f
        }
    }

    fun moveObjectAround(){
        if(!dragging){
            if(dragTresholdReached()) {
                dragging = true
                startTouch = MyPoint(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
                modelMoving = MyPolar()
            }
            return
        }
        modelMoving!!.radius = -(Gdx.input.y - startTouch.y)/10f
        modelMoving!!.degrees = -(Gdx.input.x - startTouch.x)/10f
    }

    fun adjustBuildingHeight() {
        if (!dragging) {
            dragTresholdOnAxisCheck()
            return
        }
        if (draggingVertical) {
            val heightDelta = -(Gdx.input.y - startTouch.y) / 5f
            val newHeight = (buildingHeightOriginal + heightDelta).coerceAtLeast(1f)
            // Visual preview: scale Y axis of the building instance
            val scale = newHeight / buildingHeightOriginal
            buildingInstances[selectedBuilding].transform.set(
                Matrix4().scale(1f, scale, 1f)
            )
        }
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1))
    }
    fun distance3D(pos1: Vector3, pos2: Vector3): Float {
        return sqrt((pos2.x - pos1.x) * (pos2.x - pos1.x) + (pos2.y - pos1.y) * (pos2.y - pos1.y) + (pos2.z - pos1.z) * (pos2.z - pos1.z))
    }

    fun unselectObject(){
        if(selectedObject != -1){
            objects[selectedObject].let { objekt ->
                instances[selectedObject] =
                    ModelInstance(generateModelForObject(objekt.libgdxcolor))
                updateModel(selectedObject)
            }
            selectedObject = -1
        }
    }

    fun onTouch(){
        if(!isUserTouching){
            startTouch = MyPoint(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            isUserTouching = true
            if (selectedBuilding != -1 && editMode == EditMode.adjust_building_height) {
                buildingHeightOriginal = buildingData[selectedBuilding].heightMeters
            }
        }
        if(selectedBuilding != -1 && editMode == EditMode.adjust_building_height) {
            adjustBuildingHeight()
        } else if(selectedObject != -1){
            if(editMode == EditMode.move) {
                moveObjectAround()
            } else if(editMode == EditMode.rotate){
                rotateObject()
            } else if(editMode == EditMode.scale){
                scaleObject()
            } else if(editMode == EditMode.move_vertical){
                moveObjectVertical()
            }
            updateModel(selectedObject)
        } else {
            panAround()
        }
    }

    fun onUntouch(){
        isUserTouching = false
        if(!dragging){ // a click
            System.out.println("Pokušavam uzeti objekt")
            val oldSelectedObject = selectedObject
            val oldSelectedBuilding = selectedBuilding
            unselectObject()
            unselectBuilding()
            val newObject = getObject(Gdx.input.x, Gdx.input.y)
            selectedObject = if (newObject != oldSelectedObject) newObject else -1
            System.out.println("Objekt je $selectedObject");
            if (selectedObject != -1) {
                objects[selectedObject].let { objekt ->
                    instances[selectedObject] = ModelInstance(selectedModel)
                    updateModel(selectedObject)
                }
            } else {
                // Try selecting a building if no user object was hit
                val newBuilding = getBuildingAtRay(Gdx.input.x, Gdx.input.y)
                selectedBuilding = if (newBuilding != oldSelectedBuilding) newBuilding else -1
            }
            toggleEditMode(selectedObject != -1 || selectedBuilding != -1)
        } else { // a drag
            if (selectedBuilding != -1 && editMode == EditMode.adjust_building_height && draggingVertical) {
                // Commit building height change
                val heightDelta = -(Gdx.input.y - startTouch.y) / 5f
                val newHeight = (buildingHeightOriginal + heightDelta).coerceAtLeast(1f)
                buildingData[selectedBuilding] = buildingData[selectedBuilding].copy(heightMeters = newHeight)
                regenerateBuildingMesh(selectedBuilding)
                onBuildingChange?.invoke(buildingData[selectedBuilding])
                dragging = false
                draggingHorizontal = false
                draggingVertical = false
                return
            }
            if(worldRotationTmp != 0f) {
                worldRotation = ((worldRotation + worldRotationTmp) % 360f + 360f) % 360f
                worldRotationTmp = 0f
            } else if(cameraHeightOffsetTmp != 0f) {
                cameraHeightOffset += cameraHeightOffsetTmp
                cameraHeightOffsetTmp = 0f
            } else if(selectedObject != -1 && modelMoving != null){
                val myPoint = getObjectsXZAfterRot(objects[selectedObject])
                objects[selectedObject].diffX = myPoint.x
                objects[selectedObject].diffY = myPoint.z
                objects[selectedObject].diffZ = myPoint.y
                objects[selectedObject].changed = true
                //updateObjectCoordinates(objects[selectedObject])
                onChange(true)
                modelMoving = null
            } else if(modelRotatingY != 0f){
                objects[selectedObject].rotationY += modelRotatingY
                objects[selectedObject].changed = true
                onChange(true)
                modelRotatingY = 0f
            } else if(modelRotatingX != 0f){
                objects[selectedObject].rotationX += modelRotatingX
                objects[selectedObject].changed = true
                onChange(true)
                modelRotatingX = 0f
            } else if(scalingObject != 0f){
                objects[selectedObject].size = objects[selectedObject].size+scalingObject
                objects[selectedObject].changed = true
                onChange(true)
                scalingObject = 0f
            } else if(modelMovingVertical != 0f){
                val translatingVector = getObjectsXZAfterVerticalTranslation(objects[selectedObject])
                objects[selectedObject].diffX = translatingVector.x
                objects[selectedObject].diffY = translatingVector.y
                objects[selectedObject].diffZ = translatingVector.z
                objects[selectedObject].changed = true
                modelMovingVertical = 0f
                onChange(true)
                modelMoving = null
            }
        }
        dragging = false
        draggingHorizontal = false
        draggingVertical = false
    }

    fun touchHandler(){
        if (Gdx.input.isTouched){
            onTouch()
        } else if(isUserTouching){
            onUntouch()
        }
    }

    private val position = Vector3()
    fun getObject(screenX: Int, screenY: Int): Int {
        val ray = cam!!.getPickRay(screenX.toFloat(), screenY.toFloat())
        println("$screenX $screenY")
        var distance = -1f
        for (i in 0 until instances.size) {
            val instance: ModelInstance = instances[i]
            instance.transform.getTranslation(position)
            val dist2 = ray.origin.dst2(position)
            val scale = Vector3()
            instance.transform.getScale(scale)
            //if (distance >= 0f && dist2 > distance) continue
            if (Intersector.intersectRaySphere(ray, position, 10f, null)) {
                return i
                //distance = dist2
            }
        }
        return -1
    }

    private val tmpBoundingBox = BoundingBox()
    fun getBuildingAtRay(screenX: Int, screenY: Int): Int {
        val ray = cam!!.getPickRay(screenX.toFloat(), screenY.toFloat())
        var closestDist = Float.MAX_VALUE
        var closestIndex = -1
        for (i in 0 until buildingInstances.size) {
            val instance = buildingInstances[i]
            instance.calculateBoundingBox(tmpBoundingBox)
            val center = Vector3()
            tmpBoundingBox.getCenter(center)
            val dims = Vector3()
            tmpBoundingBox.getDimensions(dims)
            if (Intersector.intersectRayBounds(ray, tmpBoundingBox, null)) {
                val dist = ray.origin.dst2(center)
                if (dist < closestDist) {
                    closestDist = dist
                    closestIndex = i
                }
            }
        }
        return closestIndex
    }

    fun regenerateBuildingMesh(index: Int) {
        if (index < 0 || index >= buildingData.size) return
        val building = buildingData[index]
        try {
            val newModel = buildingMeshGenerator.generate(building, cameraCartesian, ::geoToCartesian) ?: return
            buildingModels[index].dispose()
            buildingModels[index] = newModel
            buildingInstances[index] = ModelInstance(newModel)
        } catch (e: Exception) {
            // Skip if regeneration fails
        }
    }

    fun updateBuildingAppearance() {
        val brightness = 1f - buildingDarkness
        val color = Color(brightness, brightness, brightness, buildingOpacity)
        for (instance in buildingInstances) {
            for (material in instance.materials) {
                val colorAttr = material.get(ColorAttribute.Diffuse) as? ColorAttribute
                colorAttr?.color?.set(color)
                val blendAttr = material.get(BlendingAttribute.Type) as? BlendingAttribute
                blendAttr?.opacity = buildingOpacity
            }
        }
    }

    fun unselectBuilding() {
        selectedBuilding = -1
    }

    override fun dispose() {
        batch!!.dispose()
        mapSprite!!.texture.dispose()
        modelBatch!!.dispose()
        fontSmall?.dispose()
        shapeRenderer?.dispose()
        vertexPreviewModel?.dispose()
        floorGridModel?.dispose()
        for(instance in instances){
            instance.model.dispose()
        }
        for(model in buildingModels){
            model.dispose()
        }
        for(model in personalBuildingModels){
            model.dispose()
        }
    }

    companion object {
        const val WORLD_WIDTH = 100
        const val WORLD_HEIGHT = 100
        const val SCALE = 32f
        const val INV_SCALE = 1f / SCALE

        // this is our "target" resolution, note that the window can be any size, it is not bound to this one
        const val VP_WIDTH = 1280 * INV_SCALE
        const val VP_HEIGHT = 720 * INV_SCALE

        const val FAR_CLAMP_RENDER_DISTANCE = 300f
        const val CURVATURE_ARC_SEGMENTS = 40
    }
}
