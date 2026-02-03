package com.mygdx.game.ui.screens

import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.mygdx.game.DatastoreRepository
import com.mygdx.game.R
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.OverlayItem

@Composable
fun MapViewerScreen(
    initialCoordinates: Objekt,
    db: AppDatabase,
    onAddObject: (GeoPoint) -> Unit,
    onDeleteObject: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var overlay by remember { mutableStateOf<ItemizedOverlayWithFocus<OverlayItem>?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setBuiltInZoomControls(true)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(
                    GeoPoint(
                        initialCoordinates.x.toDouble(),
                        initialCoordinates.y.toDouble()
                    )
                )

                // Create overlay
                val items = ArrayList<OverlayItem>()
                val mOverlay = ItemizedOverlayWithFocus(
                    items,
                    object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                        override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                            Toast.makeText(ctx, item.title, Toast.LENGTH_SHORT).show()
                            return true
                        }

                        override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                            overlay?.removeItem(index)
                            Toast.makeText(ctx, "Removed", Toast.LENGTH_LONG).show()
                            onDeleteObject(item.snippet.toInt())
                            invalidate()
                            return true
                        }
                    },
                    ctx
                )
                mOverlay.setFocusItemsOnTap(true)
                overlays.add(mOverlay)
                overlay = mOverlay

                // Map events receiver
                val mapEventsReceiver = object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(geoPoint: GeoPoint): Boolean {
                        onAddObject(geoPoint)
                        return true
                    }

                    override fun longPressHelper(geoPoint: GeoPoint): Boolean {
                        Toast.makeText(
                            ctx,
                            "${geoPoint.latitude}, ${geoPoint.longitude}",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("ingo", "novi marker")
                        mOverlay.removeItem(0)
                        mOverlay.addItem(
                            0, OverlayItem(
                                "Title",
                                "Description",
                                geoPoint
                            )
                        )
                        invalidate()
                        DatastoreRepository.updateDataStore(
                            ctx,
                            DatastoreRepository.cameraDataStoreKey,
                            Gson().toJson(
                                Objekt(
                                    0,
                                    geoPoint.latitude.toFloat(),
                                    geoPoint.longitude.toFloat(),
                                    0f
                                )
                            )
                        )
                        return true
                    }
                }
                val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
                overlays.add(0, mapEventsOverlay)

                // Load camera position from datastore
                DatastoreRepository.readFromDataStore(ctx) { objekt ->
                    mOverlay.addItem(
                        0, OverlayItem(
                            "Title",
                            "Description",
                            GeoPoint(objekt.x.toDouble(), objekt.y.toDouble())
                        )
                    )
                }

                // Load objects from database
                scope.launch(Dispatchers.IO) {
                    val objects = db.objektDao().getAll()
                    withContext(Dispatchers.Main) {
                        for (objekt in objects) {
                            val marker: Drawable = ctx.resources.getDrawable(
                                R.drawable.deployed_code_fill0_wght400_grad0_opsz24,
                                ctx.theme
                            )
                            val overlayItem = OverlayItem(
                                objekt.name,
                                objekt.id.toString(),
                                GeoPoint(objekt.x.toDouble(), objekt.y.toDouble())
                            )
                            overlayItem.setMarker(marker)
                            mOverlay.addItem(overlayItem)
                        }
                        invalidate()
                    }
                }

                mapView = this
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            // Updates if needed
        }
    )
}

fun addMarkerToOverlay(
    overlay: ItemizedOverlayWithFocus<OverlayItem>,
    mapView: MapView,
    point: GeoPoint,
    name: String,
    id: String,
    markerDrawable: Drawable
) {
    val overlayItem = OverlayItem(name, id, point)
    overlayItem.setMarker(markerDrawable)
    overlay.addItem(overlayItem)
    mapView.invalidate()
}
