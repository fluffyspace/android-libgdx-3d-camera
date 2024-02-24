package com.mygdx.game

import android.app.ProgressDialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.StrictMode
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.gson.Gson
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.infowindow.InfoWindow


class MapViewer : AppCompatActivity(), AddOrEditObjectDialog.AddOrEditObjectDialogListener {

    lateinit var map: MapView
    lateinit var mapController: IMapController
    lateinit var listener: ObjectAddEditListener

    private val TAG = "OsmActivity"
    lateinit var kmlLoader: KmlLoader

    private val PERMISSION_REQUEST_CODE = 1

    lateinit var coordinates: Objekt
    lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_viewer)

        val policy: StrictMode.ThreadPolicy = StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        val ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        val coordinatesstring = intent.extras?.getString("coordinates")
        coordinates = Gson().fromJson<Objekt>(coordinatesstring, Objekt::class.java)
        map = findViewById<MapView>(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setBuiltInZoomControls(true)
        map.setMultiTouchControls(true)
        mapController = map.controller
        mapController.setZoom(15)
        val startPoint = GeoPoint(coordinates.x.toDouble(), coordinates.y.toDouble())
        mapController.setCenter(startPoint)
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()
        listener = ObjectAddEditListener({objekt ->
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("ingo", "dodan objekt")
                db.objektDao().insertAll(objekt)
                withContext(Dispatchers.Main) {
                    kmlLoader.addMarker(GeoPoint(objekt.x.toDouble(), objekt.y.toDouble()), objekt.name, objekt.id.toString())
                }
            }
        }, {objekt ->
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("ingo", "edited objekt")
                db.objektDao().update(objekt)

                /*val adapterItemIndex = objects.indexOfFirst { it.id == objekt.id }
                objects[adapterItemIndex] = objekt
                withContext(Dispatchers.Main) {
                    objectsAdapter?.dataSet?.size?.let { size ->
                        objectsAdapter?.notifyItemChanged(adapterItemIndex)
                    }
                }*/
            }
        })
    }

    override fun onResume() {
        super.onResume()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume() //needed for compass, my location overlays, v6.0.0 and up
        loadKml()
    }

    override fun onPause() {
        super.onPause()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause() //needed for compass, my location overlays, v6.0.0 and up
    }

    fun loadKml() {
        kmlLoader = KmlLoader()
        kmlLoader.showProgressDialog()
        lifecycleScope.launch(Dispatchers.IO) {
            kmlLoader.doInBackground()
            withContext(Dispatchers.Main){
                kmlLoader.onPostExecute()
                getObjectsFromDatabase(kmlLoader)
            }
        }
    }

    fun addObject(geoPoint: GeoPoint){
        val addNewObjectDialog = AddOrEditObjectDialog(null)
        addNewObjectDialog.setListener(listener)
        addNewObjectDialog.setCoordinates(geoPoint)
        addNewObjectDialog.show(supportFragmentManager, "AddNewObjectDialog")
    }

    fun getObjectsFromDatabase(kmlLoader: KmlLoader) {
        lifecycleScope.launch(Dispatchers.IO) {
            val objektDao = db.objektDao()
            val objects = objektDao.getAll().toMutableList()
            withContext(Dispatchers.Main) {
                for(objekt in objects) {
                    kmlLoader.addMarker(GeoPoint(objekt.x.toDouble(), objekt.y.toDouble()), objekt.name, objekt.id.toString())
                }
            }
        }
    }



    inner class KmlLoader {
        val progressDialog = ProgressDialog(this@MapViewer);
        lateinit var mOverlay: ItemizedOverlayWithFocus<OverlayItem>
        fun showProgressDialog(){
            progressDialog.setMessage("Loading Project...");
            progressDialog.show();
        }

        fun doInBackground(){
            /*kmlDocument = KmlDocument();
            kmlDocument.parseKMLStream(resources.openRawResource(R.raw.study_areas), null);
            val kmlOverlay = kmlDocument.mKmlRoot.buildOverlay(map, null, null, kmlDocument) as FolderOverlay;*/

            val startPoint = GeoPoint(coordinates.x.toDouble(), coordinates.y.toDouble())
            val startMarker = MarkerOverriden(map)
            startMarker.position = startPoint
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            //your items
            //your items
            val items = ArrayList<OverlayItem>()
            items.add(
                OverlayItem(
                    "Title",
                    "Description",
                    GeoPoint(coordinates.x.toDouble(), coordinates.y.toDouble())
                )
            ) // Lat/Lon decimal degrees


            //the overlay
            mOverlay = ItemizedOverlayWithFocus(items,
                    object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                        override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                            //do something
                            Toast.makeText(this@MapViewer, item.title, Toast.LENGTH_SHORT).show()
                            //showMarkerInfoWindow(item)
                            return true
                        }

                        override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                            //items.removeAt(index)
                            mOverlay.removeItem(index)
                            Toast.makeText(this@MapViewer,"Removed",Toast.LENGTH_LONG).show();
                            lifecycleScope.launch(Dispatchers.IO) {
                                Log.d("ingo", "dodan objekt")
                                db.objektDao().deleteById(item.snippet.toInt())
                            }
                            map.invalidate()
                            return true
                        }
                    }, this@MapViewer)
            mOverlay.setFocusItemsOnTap(true)
            map.overlays.add(mOverlay);

            // Add a map events overlay to detect map long press
            // Add a map events overlay to detect map long press
            val mapEventsReceiver: MapEventsReceiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(geoPoint: GeoPoint): Boolean {
                    //Toast.makeText(this@MapViewer,geoPoint?.latitude.toString() + " - "+geoPoint?.longitude.toString(),Toast.LENGTH_LONG).show();
                    addObject(geoPoint)
                    //addMarker(geoPoint!!, "ha")
                    //Toast.makeText(this@MapViewer, "Added", Toast.LENGTH_SHORT).show()
                    return true
                }

                override fun longPressHelper(geoPoint: GeoPoint): Boolean {
                    Toast.makeText(this@MapViewer,geoPoint?.latitude.toString() + ", "+geoPoint?.longitude.toString(),Toast.LENGTH_LONG).show();
                    Log.d("ingo", "novi marker")
                    //addMarker(geoPoint)
                    return true
                }
            }
            val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
            map.overlays.add(0, mapEventsOverlay)
        }

        fun addMarker(point: GeoPoint, name: String, id: String) {
            // Create marker
            val marker: Drawable = resources.getDrawable(R.drawable.deployed_code_fill0_wght400_grad0_opsz24) // Your marker icon
            val overlayItem = OverlayItem(name, id, point)
            overlayItem.setMarker(marker)


            // Add marker to overlay
            mOverlay.addItem(overlayItem)


            // Refresh map view
            map.invalidate()
        }

        fun onPostExecute(){
            progressDialog.dismiss();
            map.invalidate();
        }

        private fun showMarkerInfoWindow(item: OverlayItem) {
            val infoWindow: InfoWindow = MyInfoWindow(R.layout.markerinfo, map, item)
            val marker = Marker(map)
            marker.position = GeoPoint(item.point.latitude, item.point.longitude)
            map.overlayManager.add(marker)

            infoWindow.open(marker, GeoPoint(item.point.latitude, item.point.longitude), 0, 0)
        }
    }

    override fun onClickAddObject(coordinates: String, name: String, color: String) {

    }

    override fun onClickEditObject(
        objekt: Objekt,
        coordinates: String,
        name: String,
        color: String
    ) {

    }

}