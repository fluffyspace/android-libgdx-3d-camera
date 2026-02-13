package com.mygdx.game.activities

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mygdx.game.AndroidDeviceCameraController
import com.mygdx.game.MyGdxGame
import com.mygdx.game.OnDrawFrame
import com.badlogic.gdx.Gdx
import com.mygdx.game.arcore.ARCoreBackgroundRenderer
import com.mygdx.game.arcore.ARCoreSessionManager
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.UserBuilding
import com.mygdx.game.baza.toBuilding
import com.mygdx.game.baza.toPolygonJson
import com.mygdx.game.notbaza.Building
import com.mygdx.game.notbaza.LatLon
import com.mygdx.game.notbaza.Objekt
import com.mygdx.game.overr.AndroidApplicationOverrided
import com.mygdx.game.ui.screens.AROverlayScreen
import com.mygdx.game.ui.theme.MyGdxGameTheme
import com.mygdx.game.network.BuildingCache
import com.mygdx.game.network.ElevationClient
import com.mygdx.game.network.OverpassClient
import com.mygdx.game.viewmodel.ARObjectInfo
import com.mygdx.game.viewmodel.ARViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class AndroidLauncher : AndroidApplicationOverrided(), OnDrawFrame {
    // ARCore session manager (replaces HeadTracker)
    private lateinit var arCoreSessionManager: ARCoreSessionManager
    private lateinit var arCoreBackgroundRenderer: ARCoreBackgroundRenderer
    private var arCoreInitialized = false

    private var origWidth = 0
    private var origHeight = 0
    var gson = Gson()

    private lateinit var arViewModel: ARViewModel
    private lateinit var orientationUpdateJob: Job


    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                initializeARCore()
            }
        }

    lateinit var cameraControl: AndroidDeviceCameraController

    lateinit var objects: MutableList<Objekt>
    lateinit var game: MyGdxGame
    lateinit var db: AppDatabase
    val vertexLatLons: MutableList<LatLon> = mutableListOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ARCore session manager (replaces HeadTracker)
        arCoreSessionManager = ARCoreSessionManager(this)
        arCoreBackgroundRenderer = ARCoreBackgroundRenderer()

        arViewModel = ViewModelProvider(this)[ARViewModel::class.java]

        val config = AndroidApplicationConfiguration()
        config.useAccelerometer = false
        config.useCompass = false
        config.useGyroscope = false
        cameraControl = AndroidDeviceCameraController(this@AndroidLauncher)
        config.r = 8
        config.g = 8
        config.b = 8
        config.a = 8
        config.useGL30 = false

        val cameraIntentExtra = intent.getStringExtra("camera")
        val objectsIntentExtra = intent.getStringExtra("objects")
        objects = gson.fromJson(objectsIntentExtra, object : TypeToken<ArrayList<Objekt>>() {}.type)
        convertObjectColors(objects)
        deserializePolygons(objects)

        game = MyGdxGame(
            this@AndroidLauncher,
            gson.fromJson(cameraIntentExtra, Objekt::class.java),
            objects,
            cameraControl,
            { editModeEnabled ->
                lifecycleScope.launch(Dispatchers.Main) {
                    arViewModel.buildingSelected = game.selectedBuilding != -1
                    arViewModel.showEditMode(editModeEnabled)
                    if (!editModeEnabled && arViewModel.verticesEditorActive) {
                        vertexLatLons.clear()
                        Gdx.app.postRunnable {
                            game.clearVertexEditor()
                            arCoreSessionManager.disablePlaneDetection()
                        }
                        arViewModel.resetVerticesEditor()
                    }
                }
            },
            { change ->
                lifecycleScope.launch(Dispatchers.Main) {
                    arViewModel.showSaveMenu(change)
                }
            },
            { _ -> }
        )
        initialize(game, config)
        initializeLayouts()

        arViewModel.personalObjectCount = objects.size

        db = AppDatabase.getInstance(applicationContext)

        // Fetch OSM building footprints + user buildings, merge them
        val cameraObj = gson.fromJson(cameraIntentExtra, Objekt::class.java)
        OverpassClient.initMock(this) // TODO: remove mock
        val buildingCache = BuildingCache(applicationContext)
        buildingCache.clearAll() // TODO: remove after stale empty cache is cleared
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val lat = cameraObj.x.toDouble()
                val lon = cameraObj.y.toDouble()
                android.util.Log.d("BuildingFetch", "Starting fetch at lat=$lat, lon=$lon")

                val cached = buildingCache.getCached(lat, lon)
                android.util.Log.d("BuildingFetch", "Cache result: ${cached?.size ?: "null (cache miss)"}")

                val osmBuildings = cached
                    ?: OverpassClient.fetchBuildings(lat, lon).also {
                        android.util.Log.d("BuildingFetch", "Overpass returned ${it.size} buildings")
                        buildingCache.putCache(lat, lon, it)
                    }

                // Load user buildings and filter out overridden OSM buildings
                val userBuildingDao = db.userBuildingDao()
                val userBuildings = userBuildingDao.getAll()
                val overriddenOsmIds = userBuildingDao.getAllOsmIds().toSet()
                val filteredOsm = osmBuildings.filter { it.id !in overriddenOsmIds }
                val userConverted = userBuildings.map { it.toBuilding() }
                val merged = userConverted + filteredOsm

                arViewModel.nearbyBuildingCount = merged.size
                if (merged.isNotEmpty()) {
                    Gdx.app.postRunnable { game.setBuildings(merged) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                arViewModel.buildingFetchError = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        // Wire building change callback — save to Room when height adjusted in AR
        game.onBuildingChange = { building ->
            lifecycleScope.launch(Dispatchers.IO) {
                val userBuildingDao = db.userBuildingDao()
                val osmId = if (building.id > 0) building.id else null
                if (osmId != null) {
                    val existing = userBuildingDao.findByOsmId(osmId)
                    if (existing != null) {
                        userBuildingDao.update(existing.copy(
                            heightMeters = building.heightMeters,
                            polygonJson = building.toPolygonJson()
                        ))
                    } else {
                        userBuildingDao.insert(UserBuilding(
                            osmId = osmId,
                            polygonJson = building.toPolygonJson(),
                            heightMeters = building.heightMeters,
                            minHeightMeters = building.minHeightMeters
                        ))
                    }
                } else {
                    // User-drawn building (negative id = -userBuildingId)
                    val userBuildingId = (-building.id).toInt()
                    val existing = userBuildingDao.findById(userBuildingId)
                    if (existing != null) {
                        userBuildingDao.update(existing.copy(
                            heightMeters = building.heightMeters
                        ))
                    }
                }
            }
        }

        orientationUpdateJob = lifecycleScope.launch(Dispatchers.IO) {
            var objectListCounter = 0
            while (true) {
                delay(50)

                // Update floor height from ARCore (on IO thread, reading ARCore state)
                if (arViewModel.floorGridEnabled) {
                    val fh = arCoreSessionManager.getFloorHeight()
                    if (fh != null) {
                        Gdx.app.postRunnable { game.floorHeight = fh }
                        arViewModel.floorHeightLive = fh
                    }
                }

                // Coordinate viewer: compute center point coordinates
                if (arViewModel.coordinateViewerEnabled) {
                    val hitResult = arCoreSessionManager.hitTestCenter()
                    if (hitResult != null) {
                        // Surface detected via depth or plane
                        val hx = hitResult.x
                        val hy = hitResult.y
                        val hz = hitResult.z
                        val method = hitResult.method
                        // Compute distance from camera to hit point
                        val dist = sqrt((hx * hx + hy * hy + hz * hz).toDouble()).toFloat()
                        Gdx.app.postRunnable {
                            val (lat, lon, alt) = game.arHitToGeo(hx, hy, hz)
                            lifecycleScope.launch(Dispatchers.Main) {
                                arViewModel.centerLat = lat
                                arViewModel.centerLon = lon
                                arViewModel.centerAlt = alt
                                arViewModel.centerDistance = dist
                                arViewModel.distanceMethod = method
                            }
                        }
                    } else {
                        // No surface hit - use manual distance fallback
                        val manualDist = arViewModel.manualDistance
                        Gdx.app.postRunnable {
                            val (lat, lon, alt) = game.getCenterRayGeo(manualDist)
                            lifecycleScope.launch(Dispatchers.Main) {
                                arViewModel.centerLat = lat
                                arViewModel.centerLon = lon
                                arViewModel.centerAlt = alt
                                arViewModel.centerDistance = manualDist
                                arViewModel.distanceMethod = "manual"
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    val degrees = arCoreSessionManager.headingDegrees.toFloat() + game.worldRotation + game.worldRotationTmp
                    arViewModel.updateOrientationDegrees(degrees)

                    // Update object list every ~500ms (every 10 iterations)
                    objectListCounter++
                    if (objectListCounter >= 10 && arViewModel.objectListExpanded) {
                        objectListCounter = 0
                        updateObjectList()
                    }
                }
            }
        }
    }

    fun convertObjectColors(objekti: List<Objekt>) {
        for (objekt in objekti) {
            objekt.libgdxcolor = colorStringToLibgdxColor(Color.valueOf(objekt.color))
        }
    }

    fun deserializePolygons(objekti: List<Objekt>) {
        for (objekt in objekti) {
            val json = objekt.polygonJson
            if (json != null) {
                try {
                    objekt.polygon = gson.fromJson<List<LatLon>>(
                        json, object : TypeToken<List<LatLon>>() {}.type
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun initializeLayouts() {
        // Setup ComposeView
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MyGdxGameTheme {
                    AROverlayScreen(
                        viewModel = arViewModel,
                        onClose = { finish() },
                        onObjectDistanceChanged = { min, max ->
                            arViewModel.minDistanceObjects = min
                            arViewModel.maxDistanceObjects = max
                            game.minDistanceObjects = min
                            game.maxDistanceObjects = max
                        },
                        onBuildingDistanceChanged = { min, max ->
                            arViewModel.minDistanceBuildings = min
                            arViewModel.maxDistanceBuildings = max
                            game.minDistanceBuildings = min
                            game.maxDistanceBuildings = max
                        },
                        onNoDistanceObjectsToggle = { enabled ->
                            arViewModel.noDistanceObjects = enabled
                            game.noDistanceObjects = enabled
                        },
                        onNoDistanceBuildingsToggle = { enabled ->
                            arViewModel.noDistanceBuildings = enabled
                            game.noDistanceBuildings = enabled
                        },
                        onObjectsOnTopToggle = { enabled ->
                            arViewModel.objectsOnTop = enabled
                            game.objectsOnTop = enabled
                        },
                        onMoveClick = {
                            if (game.editMode == MyGdxGame.EditMode.move) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.move
                                arViewModel.selectEditMode(ARViewModel.EditMode.MOVE)
                            }
                        },
                        onMoveVerticalClick = {
                            if (game.editMode == MyGdxGame.EditMode.move_vertical) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.move_vertical
                                arViewModel.selectEditMode(ARViewModel.EditMode.MOVE_VERTICAL)
                            }
                        },
                        onRotateClick = {
                            if (game.editMode == MyGdxGame.EditMode.rotate) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.rotate
                                arViewModel.selectEditMode(ARViewModel.EditMode.ROTATE)
                            }
                        },
                        onScaleClick = {
                            if (game.editMode == MyGdxGame.EditMode.scale) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.scale
                                arViewModel.selectEditMode(ARViewModel.EditMode.SCALE)
                            }
                        },
                        onAdjustHeightClick = {
                            if (game.editMode == MyGdxGame.EditMode.adjust_building_height) {
                                game.editMode = null
                                arViewModel.clearEditMode()
                            } else {
                                game.editMode = MyGdxGame.EditMode.adjust_building_height
                                arViewModel.selectEditMode(ARViewModel.EditMode.ADJUST_HEIGHT)
                            }
                        },
                        onSaveClick = { saveChanges() },
                        onDiscardClick = { discardChanges() },
                        onToggleHidden = { index -> toggleObjectHidden(index) },
                        onEditObject = { index -> selectAndEditObject(index) },
                        onAddVertex = {
                            Gdx.app.postRunnable {
                                if (!game.verticesEditorActive) {
                                    arCoreSessionManager.enablePlaneDetection()
                                    game.verticesEditorActive = true
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        arViewModel.verticesEditorActive = true
                                    }
                                }
                                val hitResult = arCoreSessionManager.hitTestCenterCoords()
                                if (hitResult != null) {
                                    val (lat, lon, _) = game.arHitToGeo(hitResult[0], hitResult[1], hitResult[2])
                                    vertexLatLons.add(LatLon(lat, lon))
                                    game.updateVertexPositions(vertexLatLons)
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        arViewModel.vertexCount = vertexLatLons.size
                                        arViewModel.vertexHitStatus = "Vertex ${vertexLatLons.size} added"
                                    }
                                } else {
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        arViewModel.vertexHitStatus = "No surface detected yet"
                                    }
                                }
                            }
                        },
                        onUndoVertex = {
                            if (vertexLatLons.isNotEmpty()) {
                                vertexLatLons.removeAt(vertexLatLons.lastIndex)
                                Gdx.app.postRunnable {
                                    game.vertexPolygonClosed = false
                                    game.updateVertexPositions(vertexLatLons)
                                }
                                arViewModel.vertexCount = vertexLatLons.size
                                arViewModel.vertexPolygonClosed = false
                                arViewModel.vertexHitStatus = if (vertexLatLons.isEmpty()) "" else "Vertex removed"
                            }
                        },
                        onClosePolygon = {
                            if (vertexLatLons.size >= 3) {
                                Gdx.app.postRunnable {
                                    game.vertexPolygonClosed = true
                                    game.updateVertexPositions(vertexLatLons)
                                }
                                arViewModel.vertexPolygonClosed = true
                                arViewModel.vertexHitStatus = "Polygon closed"
                            }
                        },
                        onVertexHeightChanged = { height ->
                            arViewModel.vertexExtrudeHeight = height
                            Gdx.app.postRunnable {
                                game.vertexExtrudeHeight = height
                                game.updateVertexPositions(vertexLatLons)
                            }
                        },
                        onCancelVertices = {
                            vertexLatLons.clear()
                            Gdx.app.postRunnable {
                                game.clearVertexEditor()
                                arCoreSessionManager.disablePlaneDetection()
                            }
                            arViewModel.resetVerticesEditor()
                        },
                        onSaveVertices = {
                            if (game.selectedObject >= 0 && game.selectedObject < game.objects.size && vertexLatLons.size >= 3) {
                                val objekt = game.objects[game.selectedObject]
                                val polygonJson = gson.toJson(vertexLatLons)
                                val height = arViewModel.vertexExtrudeHeight

                                // Update runtime object
                                objekt.polygon = vertexLatLons.toList()
                                objekt.polygonJson = polygonJson
                                objekt.heightMeters = height
                                objekt.minHeightMeters = 0f

                                // Save to Room DB
                                lifecycleScope.launch(Dispatchers.IO) {
                                    db.objektDao().update(
                                        com.mygdx.game.baza.Objekt(
                                            objekt.id,
                                            objekt.x, objekt.y, objekt.z,
                                            objekt.name, objekt.size,
                                            objekt.rotationX, objekt.rotationY, objekt.rotationZ,
                                            objekt.color,
                                            objekt.osmId,
                                            polygonJson,
                                            height,
                                            0f,
                                            objekt.hidden,
                                            objekt.category
                                        )
                                    )
                                }

                                // Recreate building mesh + clean up on GL thread
                                Gdx.app.postRunnable {
                                    game.clearVertexEditor()
                                    arCoreSessionManager.disablePlaneDetection()
                                    game.createInstances()
                                }

                                vertexLatLons.clear()
                                arViewModel.resetVerticesEditor()
                                arViewModel.selectEditTab(ARViewModel.EditTab.OBJECT_EDITOR)
                            }
                        },
                        onFloorGridToggle = { enabled ->
                            arViewModel.floorGridEnabled = enabled
                            Gdx.app.postRunnable {
                                game.showFloorGrid = enabled
                                if (!enabled) {
                                    game.floorHeight = 0f
                                }
                            }
                            if (enabled) {
                                arCoreSessionManager.enablePlaneDetection()
                            } else {
                                arCoreSessionManager.disablePlaneDetection()
                                arViewModel.floorHeightLive = 0f
                            }
                        },
                        onAutoAdjustAltitude = {
                            arViewModel.isAutoAdjusting = true
                            arViewModel.autoAdjustError = null
                            // Enable plane detection if not already on
                            val needPlanes = !arViewModel.floorGridEnabled
                            if (needPlanes) arCoreSessionManager.enablePlaneDetection()

                            lifecycleScope.launch {
                                try {
                                    // Fetch ground elevation
                                    val lat = game.camera.x.toDouble()
                                    val lon = game.camera.y.toDouble()
                                    val elevation = ElevationClient.fetchElevation(lat, lon)
                                    arViewModel.groundElevation = elevation

                                    // Wait briefly if plane detection was just enabled
                                    if (needPlanes) delay(2000)

                                    // Get phone height from ARCore
                                    val phoneH = arCoreSessionManager.getFloorHeight()
                                    if (phoneH == null) {
                                        arViewModel.autoAdjustError = "No floor detected. Point camera at floor."
                                        arViewModel.isAutoAdjusting = false
                                        if (needPlanes) arCoreSessionManager.disablePlaneDetection()
                                        return@launch
                                    }
                                    arViewModel.phoneHeight = phoneH

                                    // Compute and apply new camera altitude
                                    val newAlt = arViewModel.computeAltitude()
                                    arViewModel.altitudeAutoAdjusted = true
                                    arViewModel.isAutoAdjusting = false

                                    // Update game camera on GL thread
                                    Gdx.app.postRunnable {
                                        game.camera.z = newAlt
                                        game.cameraCartesian = game.geoToCartesian(
                                            game.camera.x.toDouble(),
                                            game.camera.y.toDouble(),
                                            game.camera.z.toDouble()
                                        )
                                        game.createInstances()
                                    }

                                    if (needPlanes) arCoreSessionManager.disablePlaneDetection()
                                } catch (e: Exception) {
                                    arViewModel.autoAdjustError = "${e.javaClass.simpleName}: ${e.message}"
                                    arViewModel.isAutoAdjusting = false
                                    if (needPlanes) arCoreSessionManager.disablePlaneDetection()
                                }
                            }
                        },
                        onHeightOffsetChanged = { offset ->
                            arViewModel.heightOffset = offset
                            if (arViewModel.altitudeAutoAdjusted) {
                                val newAlt = arViewModel.computeAltitude()
                                Gdx.app.postRunnable {
                                    game.camera.z = newAlt
                                    game.cameraCartesian = game.geoToCartesian(
                                        game.camera.x.toDouble(),
                                        game.camera.y.toDouble(),
                                        game.camera.z.toDouble()
                                    )
                                    game.createInstances()
                                }
                            }
                        },
                        onCoordinateViewerToggle = { enabled ->
                            arViewModel.coordinateViewerEnabled = enabled
                            if (enabled) {
                                arCoreSessionManager.enablePlaneDetection()
                                arCoreSessionManager.enableDepth()
                            } else {
                                arCoreSessionManager.disablePlaneDetection()
                                arCoreSessionManager.disableDepth()
                                arViewModel.centerLat = null
                                arViewModel.centerLon = null
                                arViewModel.centerAlt = null
                            }
                        },
                        onManualDistanceChanged = { dist ->
                            arViewModel.manualDistance = dist
                        }
                    )
                }
            }
        }

        if (graphics.view is SurfaceView) {
            Log.d("ingo", "is surface view")
            val glView = graphics.view as SurfaceView
            // Don't use setZOrderOnTop - it would render GL on top of ComposeView buttons
            // ARCore renders camera background within GL context, so transparency is not needed
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }
        graphics.view.keepScreenOn = true
        origWidth = graphics.width
        origHeight = graphics.height

        if (allPermissionsGranted()) {
            initializeARCore()
        } else {
            requestPermissions()
        }
    }

    /**
     * Initialize ARCore session and background renderer.
     * This replaces the CameraX setup since ARCore handles the camera.
     */
    private fun initializeARCore() {
        if (arCoreSessionManager.checkAvailability()) {
            if (arCoreSessionManager.createSession()) {
                Log.d(TAG, "ARCore session created successfully")
                arCoreInitialized = true

                // Set initial display geometry immediately
                if (graphics.view is SurfaceView) {
                    val glView = graphics.view as SurfaceView
                    val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        display?.rotation ?: 0
                    } else {
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay.rotation
                    }
                    arCoreSessionManager.setDisplayGeometry(rotation, glView.width, glView.height)
                }

                // Set up surface callback to handle display geometry changes
                if (graphics.view is SurfaceView) {
                    val glView = graphics.view as SurfaceView
                    glView.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Initialization is now done lazily in getLastHeadView() on GL thread
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            @Suppress("DEPRECATION")
                            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                display?.rotation ?: 0
                            } else {
                                windowManager.defaultDisplay.rotation
                            }
                            arCoreSessionManager.setDisplayGeometry(rotation, width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // Cleanup handled in onDestroy
                        }
                    })
                }
            } else {
                Log.e(TAG, "Failed to create ARCore session")
                Toast.makeText(this, "ARCore initialization failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateObjectList() {
        val camPos = game.camTranslatingVector
        val infos = game.objects.mapIndexed { index, objekt ->
            val objPos = com.badlogic.gdx.math.Vector3(objekt.diffX, objekt.diffY, objekt.diffZ)
            val dx = objPos.x - camPos.x
            val dy = objPos.y - camPos.y
            val dz = objPos.z - camPos.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            ARObjectInfo(
                index = index,
                id = objekt.id,
                name = objekt.name,
                distance = dist,
                hidden = objekt.hidden
            )
        }.sortedBy { it.distance }
        arViewModel.updateObjectList(infos)
    }

    private fun toggleObjectHidden(index: Int) {
        if (index < 0 || index >= game.objects.size) return
        val objekt = game.objects[index]
        objekt.hidden = !objekt.hidden
        // Persist to Room
        lifecycleScope.launch(Dispatchers.IO) {
            db.objektDao().updateHidden(objekt.id, objekt.hidden)
        }
        // Refresh the list immediately
        updateObjectList()
    }

    private fun selectAndEditObject(index: Int) {
        if (index < 0 || index >= game.objects.size) return
        // Close the object list
        arViewModel.objectListExpanded = false
        // Select the object on the GL thread
        Gdx.app.postRunnable {
            game.unselectObject()
            game.unselectBuilding()
            game.selectedObject = index
            game.objects[index].let { objekt ->
                game.instances[index] = com.badlogic.gdx.graphics.g3d.ModelInstance(game.selectedModel)
                game.updateModel(index)
            }
            game.toggleEditMode(true)
        }
    }

    fun discardChanges() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                game.updateObjectsCoordinates()
                arViewModel.showSaveMenu(false)
            }
            game.noRender = false
        }
    }

    fun saveChanges() {
        lifecycleScope.launch(Dispatchers.IO) {
            val objektDao = db.objektDao()

            for (objekt in game.objects) {
                if (objekt.changed) {
                    objektDao.update(
                        com.mygdx.game.baza.Objekt(
                            objekt.id,
                            (-(objekt.diffX / game.scalar) + game.camera.x),
                            (-(objekt.diffZ / game.scalar) + game.camera.z),
                            (-(objekt.diffY / game.scalar) + game.camera.y),
                            objekt.name,
                            objekt.size,
                            objekt.rotationX,
                            objekt.rotationY,
                            objekt.rotationZ,
                            objekt.color,
                            objekt.osmId,
                            objekt.polygonJson,
                            objekt.heightMeters,
                            objekt.minHeightMeters,
                            objekt.hidden,
                            objekt.category
                        )
                    )
                }
            }
            withContext(Dispatchers.Main) {
                arViewModel.showSaveMenu(false)
            }
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    companion object {
        private const val TAG = "CameraXApp"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    fun post(r: Runnable?) {
        handler.post(r!!)
    }

    fun setFixedSize(width: Int, height: Int) {
        if (graphics.view is SurfaceView) {
            val glView = graphics.view as SurfaceView
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setFixedSize(width, height)
        }
    }

    fun restoreFixedSize() {
        if (graphics.view is SurfaceView) {
            val glView = graphics.view as SurfaceView
            glView.holder.setFormat(PixelFormat.TRANSLUCENT)
            glView.holder.setFixedSize(origWidth, origHeight)
        }
    }

    override fun onResume() {
        super.onResume()

        // Check ARCore availability and resume session
        if (arCoreSessionManager.checkAvailability()) {
            if (!arCoreInitialized) {
                initializeARCore()
            }
            arCoreSessionManager.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationUpdateJob.cancel()
        arCoreSessionManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arCoreBackgroundRenderer.dispose()
        arCoreSessionManager.close()
    }

    override fun getLastHeadView(): FloatArray {
        // Initialize ARCore background renderer on first call (on GL thread)
        // This ensures OpenGL calls happen on the correct thread
        if (!arCoreBackgroundRenderer.isInitialized && arCoreInitialized) {
            arCoreBackgroundRenderer.initialize()
            arCoreSessionManager.setCameraTextureName(arCoreBackgroundRenderer.textureId)
        }

        // Update ARCore session and get the view matrix
        arCoreSessionManager.update()

        // Draw ARCore camera background (this needs to be done on GL thread)
        arCoreSessionManager.frame?.let { frame ->
            arCoreBackgroundRenderer.draw(frame)
        }

        return arCoreSessionManager.getViewMatrix()
    }

    override fun getProjectionMatrix(): FloatArray {
        return arCoreSessionManager.getProjectionMatrix()
    }

    override fun getCameraTranslation(): FloatArray {
        return arCoreSessionManager.getCameraTranslation()
    }

    fun colorStringToLibgdxColor(color: Color): com.badlogic.gdx.graphics.Color {
        return com.badlogic.gdx.graphics.Color(color.red(), color.green(), color.blue(), color.alpha())
    }

}
