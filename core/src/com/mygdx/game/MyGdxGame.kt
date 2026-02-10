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
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
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
    var modelMoving: MyPolar? = null
    var modelMovingVertical: Float = 0f
    var modelRotatingY: Float = 0f
    var modelRotatingX: Float = 0f
    var scalingObject: Float = 0f
    val dragTreshold = 50
    val upVector = Vector3(0f, 1f, 0f)
    val xVector = Vector3(1f, 0f, 0f)
    var font: BitmapFont? = null
    var text: String? = null
    var fontCaches: MutableList<BitmapFontCache> = mutableListOf()
    var noRender: Boolean = false

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
    private val buildingMeshGenerator = BuildingMeshGenerator()
    var buildingsVisible: Boolean = true
    var selectedBuilding = -1
    var onBuildingChange: ((Building) -> Unit)? = null
    private var buildingHeightDragStart = 0f
    private var buildingHeightOriginal = 0f

    // Personal polygon objects (objects with OSM polygon data)
    var personalBuildingInstances: MutableList<ModelInstance> = mutableListOf()
    var personalBuildingModels: MutableList<Model> = mutableListOf()
    var personalBuildingObjectIndices: MutableList<Int> = mutableListOf()

    // Vertices editor state
    var shapeRenderer: ShapeRenderer? = null
    var vertexWorldPositions: MutableList<Vector3> = mutableListOf()
    var vertexPolygonClosed: Boolean = false
    var vertexExtrudeHeight: Float = 10f
    var verticesEditorActive: Boolean = false
    private var vertexPreviewModel: Model? = null
    private var vertexPreviewInstance: ModelInstance? = null

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
    lateinit var cameraEarthRot: Quaternion
    lateinit var normalVector: Vector3


    override fun create() {
        mapSprite = Sprite(Texture(Gdx.files.internal("badlogic.jpg")))
        mapSprite!!.setPosition(0f, 0f)
        mapSprite!!.setSize(WORLD_WIDTH.toFloat(), WORLD_HEIGHT.toFloat())
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()

        font = BitmapFont(Gdx.files.internal("arial_normal.fnt"), false)

        // Constructs a new OrthographicCamera, using the given viewport width and height
        // Height is multiplied by aspect ratio.
        println("aba ${camera.x} ${camera.x.toDouble()} ${camera.y} ${camera.y.toDouble()} ${camera.z} ${camera.z.toDouble()} ")
        cameraCartesian = geoToCartesian(camera.x.toDouble(), camera.y.toDouble(), camera.z.toDouble())

        normalVector = calculateNormalVector(Vector3(cameraCartesian.x, cameraCartesian.z, cameraCartesian.y))

        // Align the camera orientation with the normal vector using quaternions

        // Align the camera orientation with the normal vector using quaternions
        cameraEarthRot = alignCameraOrientation(normalVector)

        cam = PerspectiveCamera(81f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        cam!!.position.set(Vector3(0f, 0f, 0f))
        cam!!.lookAt(1f, 0f, 0f)
        cam!!.near = 1f
        cam!!.far = 300f
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
        updateObjectsCoordinates()
        for((index, objekt) in objects.withIndex()){
            println("ingo Dobio sam $objekt i ")
            fontCaches.add(BitmapFontCache(font, false))
            if (objekt.polygon != null && objekt.polygon!!.size >= 3) {
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
                }
                // Still add a sphere instance (invisible/placeholder) so indices stay aligned
                instances.add(ModelInstance(generateModelForObject(objekt.libgdxcolor)))
                updateModel(instances.lastIndex)
            } else {
                instances.add(ModelInstance(generateModelForObject(objekt.libgdxcolor)))
                updateModel(instances.lastIndex)
            }
        }
    }
    fun setBuildings(newBuildings: List<Building>) {
        // Dispose old building models
        for (model in buildingModels) {
            model.dispose()
        }
        buildingModels.clear()
        buildingInstances.clear()
        buildingData.clear()
        selectedBuilding = -1

        for (building in newBuildings) {
            try {
                val model = buildingMeshGenerator.generate(building, cameraCartesian, ::geoToCartesian)
                if (model != null) {
                    buildingModels.add(model)
                    buildingInstances.add(ModelInstance(model))
                    buildingData.add(building)
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
            camTranslatingVector = Vector3(
                cameraMoved.x,
                cameraMoved.y,
                cameraMoved.z
            )

            // Build view matrix:
            // Matrix chain order determines point transformation order (reverse).
            // Points are transformed: quat(worldRot(earthRot(translate(p))))
            // 1. translate (camera position)
            // 2. cameraEarthRot (earth curvature correction)
            // 3. worldRotation (user's compass correction) - applied in world-aligned frame
            // 4. quat (device orientation from ARCore) - transforms to camera space
            view.set(
                Matrix4()
                    .rotate(quat)
                    .rotate(upVector, worldRotation + worldRotationTmp)
                    .rotate(cameraEarthRot)
                    .translate(Vector3(camTranslatingVector.x, camTranslatingVector.y, camTranslatingVector.z))
            )
            combined.set(projection)
            Matrix4.mul(combined.`val`, view.`val`)

            invProjectionView.set(combined)
            Matrix4.inv(invProjectionView.`val`)
            frustum.update(invProjectionView)
        }
        camHeightChange(Vector3(camTranslatingVector.x, camTranslatingVector.y, camTranslatingVector.z))
    }

    // Function to calculate the normal vector to the Earth's surface at a given location
    fun calculateNormalVector(cameraPosition: Vector3): Vector3 {
        // Assuming Earth's center is at (0, 0, 0) in ECEF coordinates
        return Vector3(cameraPosition).nor()
    }

    // Function to align camera orientation with the normal vector using quaternions
    fun alignCameraOrientation(normalVector: Vector3): Quaternion {
        // Calculate the rotation axis perpendicular to the normal vector and the camera's current "up" direction
        val rotationAxis: Vector3 = Vector3(normalVector).crs(Vector3.Y).nor()

        // Calculate the angle between the normal vector and the camera's current "up" direction
        val angle = acos((Vector3.Y).dot(normalVector))

        // Create a quaternion representing the rotation
        return Quaternion().setFromAxisRad(rotationAxis, (angle+Math.PI).toFloat())
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
    fun arHitToGeo(arX: Float, arY: Float, arZ: Float): LatLon {
        val arPoint = Vector3(arX, arY, arZ)

        // Build inverse of: R_worldRot * R_earthRot * T_cam
        // = T_cam^(-1) * R_earthRot^(-1) * R_worldRot^(-1)
        val invEarthRot = Quaternion(cameraEarthRot).conjugate()
        val inverseMatrix = Matrix4()
            .rotate(upVector, -(worldRotation + worldRotationTmp))
            .rotate(invEarthRot)
            .translate(-camTranslatingVector.x, -camTranslatingVector.y, -camTranslatingVector.z)

        val worldPoint = Vector3(arPoint)
        worldPoint.mul(inverseMatrix)

        // Convert from world space (diffX/Y/Z) to ECEF
        // diffX = ecefX - camX, diffZ = ecefY - camY, diffY = ecefZ - camZ
        val ecefX = worldPoint.x.toDouble() + cameraCartesian.x.toDouble()
        val ecefY = worldPoint.z.toDouble() + cameraCartesian.y.toDouble()
        val ecefZ = worldPoint.y.toDouble() + cameraCartesian.z.toDouble()

        val (lat, lon, _) = cartesianToGeo(ecefX, ecefY, ecefZ)
        return LatLon(lat, lon)
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

    private fun isObjectInDistanceRange(objekt: Objekt): Boolean {
        if (noDistanceObjects) return true
        val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
        val dist = distance3D(camTranslatingVector, objektPos)
        return dist in minDistanceObjects..maxDistanceObjects
    }

    private fun isBuildingInDistanceRange(instance: ModelInstance): Boolean {
        if (noDistanceBuildings) return true
        val pos = Vector3()
        instance.transform.getTranslation(pos)
        val dist = distance3D(camTranslatingVector, pos)
        return dist in minDistanceBuildings..maxDistanceBuildings
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

        for((index, objekt) in objects.withIndex()){
            // Skip sphere rendering for polygon objects
            if (index in polygonObjectIndices) {
                objekt.visible = false
                continue
            }
            if (objekt.hidden) {
                objekt.visible = false
                continue
            }
            objekt.visible = isVisible(cam!!, index) && isObjectInDistanceRange(objekt)
            if (!objekt.visible) {
                continue
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

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        modelBatch!!.begin(cam)
        // Render sphere objects (non-polygon)
        for((index, instance) in instances.withIndex()) {
            if (index in polygonObjectIndices) continue
            if (index < objects.size && !objects[index].visible) continue
            modelBatch!!.render(instance, environment)
        }
        // Render personal polygon objects with distance filtering
        for ((i, instance) in personalBuildingInstances.withIndex()) {
            val objIndex = personalBuildingObjectIndices[i]
            val objekt = objects[objIndex]
            if (objekt.hidden) continue
            val objektPos = Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
            val dist = distance3D(camTranslatingVector, objektPos)
            if (!noDistanceObjects && dist !in minDistanceObjects..maxDistanceObjects) continue
            modelBatch!!.render(instance, environment)
        }
        // Render nearby buildings with distance filtering
        if (buildingsVisible) {
            for (instance in buildingInstances) {
                if (!isBuildingInDistanceRange(instance)) continue
                modelBatch!!.render(instance, environment)
            }
        }
        // Render vertex editor extruded preview
        if (vertexPreviewInstance != null) {
            modelBatch!!.render(vertexPreviewInstance, environment)
        }
        modelBatch!!.end()

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
    }

    fun isVisible(cam: Camera, index: Int): Boolean {
        val pos = Vector3()
        instances[index].transform.getTranslation(pos)
        return cam.frustum.sphereInFrustum(pos, objects[index].size)
    }

    fun usingKotlinStringFormat(input: Float, scale: Int) = "%.${scale}f".format(input)

    private fun showObjectsName(index: Int) {
        val objekt = objects[index]
        /*makeTextForObject(index) { pos, rot ->
            objects[index].name + "\n" + usingKotlinStringFormat(distance3D(Vector3(0f,0f,0f), Vector3(objects[index].x, objects[index].y, objects[index].z)), 2)
        }*/

        makeTextForObject(index){ pos, rot ->
            objekt.name + "\n" + "%.2f".format(distance3D(camTranslatingVector, pos)) + " m"//\n%.2f, %.2f, %.2f".format(pos.x, pos.y, pos.z)
        }
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
            worldRotationTmp = -(Gdx.input.x - startTouch.x) / 10f
        }
        // Vertical slide disabled - distance is determined by GPS coordinates only
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
                worldRotation += worldRotationTmp
                worldRotationTmp = 0f
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

    fun unselectBuilding() {
        selectedBuilding = -1
    }

    override fun dispose() {
        batch!!.dispose()
        mapSprite!!.texture.dispose()
        modelBatch!!.dispose()
        shapeRenderer?.dispose()
        vertexPreviewModel?.dispose()
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
    }
}
