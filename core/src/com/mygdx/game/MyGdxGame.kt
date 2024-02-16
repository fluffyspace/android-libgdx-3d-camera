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
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
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
    var fov: Int,
    var toggleEditMode: (Boolean) -> Unit,
    var onChange: (Boolean) -> Unit,
    var camHeightChange: (Float) -> Unit,
) : ApplicationAdapter() {
    private var cam: PerspectiveCamera? = null
    private var batch: SpriteBatch? = null
    private var mapSprite: Sprite? = null
    private var rotationMatrix: Matrix4? = null
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
    var worldUpDownTmp = 0f
    var worldUpDown = 0f
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
    var fontCaches: MutableList<BitmapFontCache?> = mutableListOf()
    var noRender: Boolean = false
    var noDistance: Boolean = false

    data class MyPoint(var x: Float = 0f, var y: Float = 0f)
    data class MyPolar(var radius: Float = 0f, var degrees: Float = 0f)

    override fun resize(width: Int, height: Int) {
        // viewport must be updated for it to work properly
        viewport?.update(width, height, true)
    }

    enum class EditMode {
        move,
        move_vertical,
        rotate,
        scale
    }

    var editMode: EditMode? = null

    override fun create() {
        mapSprite = Sprite(Texture(Gdx.files.internal("badlogic.jpg")))
        mapSprite!!.setPosition(0f, 0f)
        mapSprite!!.setSize(WORLD_WIDTH.toFloat(), WORLD_HEIGHT.toFloat())
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        rotationMatrix = Matrix4()

        font = BitmapFont(Gdx.files.internal("arial_normal.fnt"), false)

        // Constructs a new OrthographicCamera, using the given viewport width and height
        // Height is multiplied by aspect ratio.
        cam = PerspectiveCamera(81f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        cam!!.position[0f, 0f] = 0f
        cam!!.lookAt(1f, 0f, 0f)
        cam!!.near = 1f
        cam!!.far = 300f
        cam!!.update()
        environment = Environment()
        environment!!.set(ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f))
        environment!!.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
        val modelBuilder = ModelBuilder()
        selectedModel = modelBuilder.createBox(
            5f, 5f, 5f,
            Material(ColorAttribute.createDiffuse(Color.RED)),
            (
                    VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
        modelBatch = ModelBatch()
        batch = SpriteBatch()
        createInstances()
    }

    val scalar = 10000f

    fun createInstances(){
        selectedObject = -1
        instances = mutableListOf()

        val cameraCartesian = geoToCartesian(camera.x.toDouble(), camera.y.toDouble(), camera.z.toDouble())
        lateinit var objectCartesian: Vector3
        for(objekt in objects){
            objectCartesian = geoToCartesian(objekt.x.toDouble(), objekt.y.toDouble(), objekt.z.toDouble())
            objekt.diffX = (cameraCartesian.x - objectCartesian.x)
            objekt.diffZ = (cameraCartesian.y - objectCartesian.y)
            objekt.diffY = (cameraCartesian.z - objectCartesian.z)
            println("Dobio sam $objekt i ")
            toggleEditMode(false)
            fontCaches.add(BitmapFontCache(font, false))
            instances.add(ModelInstance(generateModelForObject(objekt.libgdxcolor)))
            updateModel(instances.lastIndex)
        }
        camHeightChange(camera.y)
    }

    fun objectsUpdated(){
        selectedObject = -1
        for((index, objekt) in objects.withIndex()){
            objekt.diffX = (camera.x - objekt.x) * scalar
            objekt.diffZ = (camera.y - objekt.y) * scalar
            objekt.diffY = (camera.z - objekt.z)
            println("Dobio sam $objekt i ")
            toggleEditMode(false)
            //instances[index].add(ModelInstance(generateModelForObject(objekt.libgdxcolor)).apply { transform.setToTranslation(0f, 0f, 0f) })
        }
    }

    fun generateModelForObject(color: Color): Model{
        return ModelBuilder().createBox(
            5f, 5f, 5f,
            Material(ColorAttribute.createDiffuse(color)),
            (
                    VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
    }
    val cameraTmp = Vector3()
    var quat = Quaternion()
    var translatingVector = Vector3()
    fun updateCamera(){
        cam!!.apply {
            val aspect: Float = viewportWidth / viewportHeight
            projection.setToProjection(abs(near), abs(far), fieldOfView, aspect)

            Matrix4(onDrawFrame.lastHeadView).getRotation(quat)
            translatingVector = Vector3(
                0f,
                worldUpDown + worldUpDownTmp,
                0f
            )

            //view.setToLookAt(position, cameraTmp.set(position).add(direction), up)
            view.set(
                Matrix4()
                    .rotate(quat)
                    .rotate(upVector, worldRotation + worldRotationTmp)
                    .translate(translatingVector)
            )
            combined.set(projection)
            Matrix4.mul(combined.`val`, view.`val`)

            invProjectionView.set(combined)
            Matrix4.inv(invProjectionView.`val`)
            frustum.update(invProjectionView)
        }
    }

    fun updateModel(index: Int){
        val objekt = objects[index]
        lateinit var translatingVector: Vector3
        if (modelMoving != null) {
            val myPoint = getObjectsXZAfterRot(objekt)
            translatingVector = Vector3(
                myPoint.x,
                objekt.diffY,
                myPoint.y
            )
        } else {
            translatingVector = Vector3(
                objekt.diffX,
                objekt.diffY + modelMovingVertical,
                objekt.diffZ
            )
        }
        //instances[index].transform.set(Matrix4())
        instances[index].transform.set(
            Matrix4().translate(translatingVector)
                .rotate(upVector, modelRotatingY + objekt.rotationY)
                .rotate(xVector, modelRotatingX + objekt.rotationX)
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
        // Convert latitude and longitude to radians
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)

        // Calculate Earth's radius at given latitude
        val radius = EARTH_RADIUS + altitude

        // Convert to Cartesian coordinates
        val x = radius * cos(latRad) * cos(lonRad)
        val y = radius * cos(latRad) * sin(lonRad)
        val z = radius * sin(latRad)
        return Vector3(x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun noDistanceChanged(){
        unselectObject()
        toggleEditMode(false)
        for((index, objekt) in objects.withIndex()){
            if(noDistance) {
                instances[index].transform.set(Matrix4())
                val myPoint = getObjectsWithLimitedDistance(objekt)
                instances[index].transform.mul(
                    Matrix4().translate(myPoint)
                        .rotate(upVector, objekt.rotationY)
                        .rotate(xVector, objekt.rotationX)
                        .scale(
                            objekt.size,
                            objekt.size,
                            objekt.size
                        )
                )
                makeTextForObject(index){ pos, rot ->
                    objekt.name + "\n" + "%.2f".format(distance3D(cam!!.position, pos)) + " m"
                }
            } else {
                updateModel(index)
            }
        }
    }

    override fun render() {
        if(noRender) return
        touchHandler()

        cam!!.fieldOfView = fov.toFloat()

        updateCamera()

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        for((index, objekt) in objects.withIndex()){
            objekt.visible = isVisible(cam!!, index)
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

        modelBatch!!.begin(cam)
        for(instance in instances) {
            modelBatch!!.render(instance, environment)
        }
        modelBatch!!.end()

        batch!!.begin()
        fontCaches.forEachIndexed {index, fontCache ->
            if (objects[index].visible) {
                fontCache?.draw(batch)
            }
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
        makeTextForObject(index) { pos, rot ->
            objects[index].name + "\n" + usingKotlinStringFormat(distance3D(Vector3(0f,0f,0f), Vector3(objects[index].diffX, objects[index].diffY, objects[index].diffZ)), 2)
        }
    }

    fun projectObject(model: ModelInstance): MyPoint{
        var worldPosition: Vector3 = Vector3()
        model.transform.getTranslation(worldPosition)
        cam!!.project(worldPosition).let{
            return MyPoint(it.x, it.y)
        }
    }

    fun makeTextForObject(index: Int, toText: (Vector3, Vector3) -> String){
        val model = instances[index]
        val screenPosition = projectObject(model)

        var worldPosition: Vector3 = Vector3()
        var worldRotation: Quaternion = Quaternion()
        model.transform.getTranslation(worldPosition)
        model.transform.getRotation(worldRotation)
        val rotationInAngles = Vector3(worldRotation.yaw, worldRotation.roll, worldRotation.pitch)
        fontCaches[index]!!.setText(toText(worldPosition, rotationInAngles), screenPosition.x, screenPosition.y, 0f, Align.center, false);
        //this.text = toText(worldPosition)
    }

    fun getObjectsWithLimitedDistance(objekt: Objekt): Vector3{
        var dist = distance3D(cam!!.position, Vector3(objekt.diffX, objekt.diffY, objekt.diffZ))
        //var dist = distance(cam!!.position.x, cam!!.position.z, objekt.diffX, objekt.diffZ)
        val degree = atan2(objekt.diffX, objekt.diffZ)
        //val degreeY = atan2(objekt.diffY, sqrt(objekt.x * objekt.x + objekt.z * objekt.z))
        val phi = acos(objekt.diffY / dist)
        if(dist > 50f) dist = 50f

        println(degree)

        val x = (dist) * sin(degree)
        val y = (dist) * cos(phi)
        val z = (dist) * cos(degree)

        return Vector3(x, y, z)
    }

    fun getObjectsXZAfterRot(objekt: Objekt): MyPoint{
        val dist =
            distance(cam!!.position.x, cam!!.position.z, objekt.diffX, objekt.diffZ)
        val degree = atan2(objekt.diffX, objekt.diffZ)

        println(degree)

        val x = (dist + modelMoving!!.radius) * sin(degree + modelMoving!!.degrees)
        val z = (dist + modelMoving!!.radius) * cos(degree + modelMoving!!.degrees)
        return MyPoint(x,z)
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
        } else if(draggingVertical){
            worldUpDownTmp = -(Gdx.input.y - startTouch.y) / 10f
            camHeightChange(camera.y - worldUpDown - worldUpDownTmp)
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
        modelMoving!!.degrees = -(Gdx.input.x - startTouch.x)/700f
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
        }
        if(selectedObject != -1){
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
            unselectObject()
            if(!noDistance) {
                val newObject = getObject(Gdx.input.x, Gdx.input.y)
                selectedObject = if (newObject != oldSelectedObject) newObject else -1
                System.out.println("Objekt je $selectedObject");
                if (selectedObject != -1) {
                    objects[selectedObject].let { objekt ->
                        instances[selectedObject] = ModelInstance(selectedModel)
                        updateModel(selectedObject)
                    }
                }
            }
            toggleEditMode(selectedObject != -1)
        } else { // a drag
            if(worldRotationTmp != 0f) {
                worldRotation += worldRotationTmp
                worldRotationTmp = 0f
            } else if(worldUpDownTmp != 0f){
                worldUpDown += worldUpDownTmp
                worldUpDownTmp = 0f
            } else if(selectedObject != -1 && modelMoving != null){
                val myPoint = getObjectsXZAfterRot(objects[selectedObject])
                objects[selectedObject].diffX = myPoint.x
                objects[selectedObject].diffZ = myPoint.y
                objects[selectedObject].changed = true
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
                objects[selectedObject].diffY += modelMovingVertical
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


    override fun dispose() {
        batch!!.dispose()
        mapSprite!!.texture.dispose()
        modelBatch!!.dispose()
        for(instance in instances){
            instance.model.dispose()
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
