package com.mygdx.game.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.Data
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.mygdx.game.AddCoordinateFragment
import com.mygdx.game.AddOrEditObjectDialog
import com.mygdx.game.CoordinateAddListener
import com.mygdx.game.DatastoreRepository
import com.mygdx.game.EmptyDataObserver
import com.mygdx.game.ObjectAddEditListener
import com.mygdx.game.ObjectAddEditListener.Companion.textToObject
import com.mygdx.game.ObjectsAdapter
import com.mygdx.game.R
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import com.mygdx.game.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class MainActivity : AppCompatActivity(), CoordinateAddListener,
    ObjectsAdapter.ObjectClickListener {
    lateinit var binding: ActivityMainBinding
    var fusedLocationClient: FusedLocationProviderClient? = null

    var camera: Objekt? = null
    lateinit var objects: MutableList<Objekt>
    var objectsAdapter: ObjectsAdapter? = null
    val cameraDataStoreKey: Preferences.Key<String> = stringPreferencesKey("camera_coordinates")

    lateinit var listener: ObjectAddEditListener

    lateinit var db: AppDatabase
    val locationPermissionRequest: ActivityResultLauncher<Array<String>> by lazy {
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result: Map<String, Boolean> ->
            val fineLocationGranted = result.getOrDefault(
                Manifest.permission.ACCESS_FINE_LOCATION, false
            )
            val coarseLocationGranted = result.getOrDefault(
                Manifest.permission.ACCESS_COARSE_LOCATION, false
            )
            if (fineLocationGranted) {
                // Precise location access granted.
            } else if (coarseLocationGranted) {
                // Only approximate location access granted.
            } else {
                // No location access granted.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        binding.map.setOnClickListener {
            updateCoordinates() { objekt ->
                val intent = Intent(this@MainActivity, MapViewer::class.java)
                intent.putExtra("coordinates", Gson().toJson(objekt))
                startActivity(intent)
            }
        }
        binding.openViewer.setOnClickListener {
            Log.d("ingo", "open viewer")

            val success = setFromET()
            if(!success){
                Toast.makeText(this, "Invalid coordinates.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this@MainActivity, AndroidLauncher::class.java)
            intent.putExtra("camera", Gson().toJson(camera))
            intent.putExtra("objects", Gson().toJson(objects))
            startActivity(intent)
        }
        binding.cameraSetCoordinates.setOnClickListener {
            updateCoordinates(){ objekt ->
                camera = objekt
                updateEditTexts()
                DatastoreRepository.updateDataStore(
                    this,
                    cameraDataStoreKey,
                    Gson().toJson(objekt)
                )
                Toast.makeText(
                    this@MainActivity,
                    "Lokacija postavljena.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        camera = Objekt(0, 0f, 0f, 0f)
        updateEditTexts()
        binding.addObject.setOnClickListener {
            val addNewObjectDialog = AddOrEditObjectDialog(null)
            addNewObjectDialog.show(supportFragmentManager, "AddNewObjectDialog")
        }

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()

        // Get text passed to app for adding new objects
        val intent = intent
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                Log.d("ingo", sharedText!!)
                Thread {
                    val expandedURL1 = expandGoogleMapsUrl(sharedText)
                    Log.d("Expanded", "run: $expandedURL1")
                }.start()
                AddCoordinateFragment(this).show(supportFragmentManager, "GAME_DIALOG")
            }
        }
        readFromDataStore()
        hasLocationPermission()

        listener = ObjectAddEditListener({objekt ->
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("ingo", "dodan objekt")
                db.objektDao().insertAll(objekt)
                objects.add(objekt)
                withContext(Dispatchers.Main) {
                    objectsAdapter?.dataSet?.size?.let { size ->
                        objectsAdapter?.notifyItemInserted(size - 1)
                    }
                }
            }
        }, {objekt ->
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("ingo", "edited objekt")
                db.objektDao().update(objekt)
                val adapterItemIndex = objects.indexOfFirst { it.id == objekt.id }
                objects[adapterItemIndex] = objekt
                withContext(Dispatchers.Main) {
                    objectsAdapter?.dataSet?.size?.let { size ->
                        objectsAdapter?.notifyItemChanged(adapterItemIndex)
                    }
                }
            }
        })
    }

    fun setFromET(): Boolean{
        val tmp = textToObject(binding.cameraCoordinatesEt.text.toString()) ?: return false
        camera = tmp
        updateEditTexts()
        DatastoreRepository.updateDataStore(
            this,
            cameraDataStoreKey,
            Gson().toJson(camera)
        )
        return true
    }

    fun getObjectsFromDatabase(){
        lifecycleScope.launch(Dispatchers.IO) {
            val objektDao = db.objektDao()
            objects = objektDao.getAll().toMutableList()
            withContext(Dispatchers.Main){
                if(objectsAdapter == null) {
                    objectsAdapter = ObjectsAdapter(objects, this@MainActivity)

                    val divider = DividerItemDecoration(
                        binding.recyclerView.context, DividerItemDecoration.VERTICAL
                    )
                    divider.setDrawable(
                        ContextCompat.getDrawable(baseContext, R.drawable.line_divider)!!
                    )

                    binding.recyclerView.addItemDecoration(divider)

                    binding.recyclerView.adapter = objectsAdapter
                    binding.recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)

                    // Here
                    val emptyDataObserver = EmptyDataObserver(binding.recyclerView, binding.emptyDataParent.root)
                    objectsAdapter!!.registerAdapterDataObserver(emptyDataObserver)

                } else {
                    objectsAdapter!!.dataSet = objects
                    objectsAdapter!!.notifyDataSetChanged()
                }
            }
        }
    }



    fun hasLocationPermission(): Boolean{
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return false
        }
        return true
    }

    fun updateCoordinates(onLocationResult: (Objekt) -> Unit) {
        Log.d("ingo", "updateCoordinates")
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("ingo", "has permission")
            fusedLocationClient!!.lastLocation
                .addOnSuccessListener(this@MainActivity) { location ->
                    // Got last known location. In some rare situations this can be null.
                    Log.d("ingo", "onSuccess")
                    if (location != null) {
                        Log.d("ingo", "not null")
                        // Logic to handle location object
                        onLocationResult(Objekt(0,
                            location.latitude.toFloat(), location.longitude.toFloat(), 0f))

                    }
                }.addOnFailureListener { e ->
                    Log.d("ingo", "onFailure")
                    e.printStackTrace()
                }
        }
    }

    fun updateEditTexts() {
        if (camera != null) {
            Log.d("ingo", "camera coordinates da")
            val text =
                camera!!.x.toString() + ", " + camera!!.y + ", " + camera!!.z
            binding.cameraCoordinatesEt.setText(text)
        }
    }

    fun readFromDataStore() {
        DatastoreRepository.readFromDataStore(this){
            camera = it
            Log.d("ingo", Gson().toJson(camera))
            lifecycleScope.launch(Dispatchers.Main){
                updateEditTexts()
            }
        }
    }



    override fun onResume() {
        super.onResume()
        getObjectsFromDatabase()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCoordinatesPassed(isCamera: Boolean) {
        //updateCoordinates(type)
    }

    companion object {

        const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        fun expandGoogleMapsUrl(url: String?): String {
            var s3 = ""
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                val s = connection.inputStream.read()
                val s2 = connection.url
                s3 = s2.toString()
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return s3
        }
    }



    override fun onClickOnObject(objekt: Objekt) {
        val addNewObjectDialog = AddOrEditObjectDialog(objekt)
        addNewObjectDialog.setListener(listener)
        addNewObjectDialog.show(supportFragmentManager, "AddNewObjectDialog")
    }

    override fun onLongClickOnObject(objekt: Objekt) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.objektDao().delete(objekt)
            withContext(Dispatchers.Main){
                objectsAdapter?.dataSet?.indexOf(objekt)?.let {index ->
                    objectsAdapter?.notifyItemRemoved(index)
                    objects.remove(objekt)
                }
            }
        }
    }
}
