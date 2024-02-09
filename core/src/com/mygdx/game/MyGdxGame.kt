package com.mygdx.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
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
import kotlin.math.abs
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
    var toggleEditMode: (Boolean) -> Unit
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
    var modelRotatingY: Float = 0f
    var modelRotatingX: Float = 0f
    val dragTreshold = 50
    val upVector = Vector3(0f, 1f, 0f)
    val xVector = Vector3(1f, 0f, 0f)
    var font: BitmapFont? = null
    var textXY: MyPoint? = null
    var text: String? = null
    var fontCache: BitmapFontCache? = null

    data class MyPoint(var x: Float = 0f, var y: Float = 0f)
    data class MyPolar(var radius: Float = 0f, var degrees: Float = 0f)

    override fun resize(width: Int, height: Int) {
        // viewport must be updated for it to work properly
        viewport?.update(width, height, true)
    }

    enum class EditMode {
        move,
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
        fontCache = BitmapFontCache(font, false)

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
        objectsUpdated()
    }

    val scalar = 10000f

    fun objectsUpdated(){
        instances = mutableListOf()
        for(objekt in objects){
            objekt.diffX = (camera.x - objekt.x) * scalar
            objekt.diffZ = (camera.y - objekt.y) * scalar
            objekt.diffY = (camera.z - objekt.z) * scalar
            println("Dobio sam $objekt i ")
            selectedObject = -1
            toggleEditMode(false)
            instances.add(ModelInstance(generateModelForObject(objekt.libgdxcolor)).apply { transform.setToTranslation(0f, 0f, 0f) })
        }
    }

    fun generateModelForObject(color: Color): Model{
        val modelBuilder = ModelBuilder()
        return modelBuilder.createBox(
            5f, 5f, 5f,
            Material(ColorAttribute.createDiffuse(color)),
            (
                    VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        )
    }

    override fun render() {
        touchHandler()

        cam!!.fieldOfView = fov.toFloat()

        cam!!.update()
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        var quat = Quaternion()
        Matrix4(onDrawFrame.lastHeadView).getRotation(quat)
        quat = Quaternion(-quat.z, quat.y, quat.x, quat.w)

        for((index, objekt) in objects.withIndex()){
            try {
                instances[index].transform.set(Matrix4())
                if(selectedObject != index) {
                    instances[index].transform.mul(
                        Matrix4().rotate(quat)
                            .rotate(upVector, worldRotation+worldRotationTmp)
                            .translate(objekt.diffX, objekt.diffY+worldUpDown+worldUpDownTmp, objekt.diffZ)
                            .rotate(upVector, objekt.rotationY)
                            .rotate(xVector, objekt.rotationX)
                    )
                } else {
                    lateinit var translatingVector: Vector3
                    if(modelMoving != null) {
                        val myPoint = getObjectsXZAfterRot(objekt)
                        translatingVector = Vector3(
                            myPoint.x,
                            objekt.diffY + worldUpDown + worldUpDownTmp,
                            myPoint.y
                        )
                    } else {
                        translatingVector = Vector3(
                            objekt.diffX,
                            objekt.diffY + worldUpDown + worldUpDownTmp,
                            objekt.diffZ
                        )
                    }
                    instances[index].transform.mul(
                        Matrix4().rotate(quat)
                            .rotate(upVector, worldRotation + worldRotationTmp)
                            .translate(translatingVector)
                            .rotate(upVector, modelRotatingY+objekt.rotationY)
                            .rotate(xVector, modelRotatingX+objekt.rotationX)
                    )
                    var showingTransformInfo = false
                    when(editMode){
                        EditMode.move -> {
                            if(modelMoving != null){
                                makeTextForObject(instances[index]){ pos, rot ->
                                    "X: ${pos.x.roundToInt()}, Y: ${pos.y.roundToInt()}"
                                }
                                showingTransformInfo = true
                            }
                        }
                        EditMode.rotate -> {
                            if(modelRotatingX != 0f){
                                makeTextForObject(instances[index]){ pos, rot ->
                                    "X: ${rot.y.roundToInt()}"
                                }
                                showingTransformInfo = true
                            } else if(modelRotatingY != 0f){
                                makeTextForObject(instances[index]){ pos, rot ->
                                    "Y: ${rot.x.roundToInt()}"
                                }
                                showingTransformInfo = true
                            }
                        }
                        else -> {}
                    }
                    if(!showingTransformInfo){
                        showObjectsName(index)
                    }
                }
            } catch (e: java.lang.IndexOutOfBoundsException){

            }
        }

        modelBatch!!.begin(cam)
        for(instance in instances) {
            modelBatch!!.render(instance, environment)
        }
        modelBatch!!.end()
        textXY?.let{
            batch!!.begin()
            fontCache!!.draw(batch)
            batch!!.end()
        }
    }

    private fun showObjectsName(index: Int) {
        makeTextForObject(instances[index]) { pos, rot ->
            objects[index].name
        }
    }

    fun makeTextForObject(model: ModelInstance, toText: (Vector3, Vector3) -> String){
        var worldPosition: Vector3 = Vector3()
        var worldRotation: Quaternion = Quaternion()
        model.transform.getTranslation(worldPosition)
        model.transform.getRotation(worldRotation)
        val rotationInAngles = Vector3(worldRotation.yaw, worldRotation.roll, worldRotation.pitch)
        val screenPosition: Vector3 = cam!!.project(worldPosition)
        textXY = MyPoint(screenPosition.x, screenPosition.y)
        fontCache!!.setText(toText(worldPosition, rotationInAngles), screenPosition.x, screenPosition.y, 0f, Align.center, false);



        //this.text = toText(worldPosition)
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
            }
        } else {
            panAround()
        }
    }

    fun onUntouch(){
        isUserTouching = false
        if(!dragging){ // a click
            System.out.println("Pokušavam uzeti objekt")
            if(selectedObject != -1){
                instances[selectedObject] = ModelInstance(generateModelForObject(objects[selectedObject].libgdxcolor))
            }
            val newObject = getObject(Gdx.input.x, Gdx.input.y)
            selectedObject = if(newObject != selectedObject) newObject else -1
            System.out.println("Objekt je $selectedObject");
            if(selectedObject != -1) {
                instances[selectedObject] = ModelInstance(selectedModel)
                toggleEditMode(true)
            } else {
                toggleEditMode(false)
            }
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
                modelMoving = null
            } else if(modelRotatingY != 0f){
                objects[selectedObject].rotationY += modelRotatingY
                objects[selectedObject].changed = true
                modelRotatingY = 0f
            } else if(modelRotatingX != 0f){
                objects[selectedObject].rotationX += modelRotatingX
                objects[selectedObject].changed = true
                modelRotatingX = 0f
            }
        }
        dragging = false
        draggingHorizontal = false
        draggingVertical = false
        textXY = null
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
