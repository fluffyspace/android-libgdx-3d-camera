package com.mygdx.game

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.rxjava3.RxDataStore
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.mygdx.game.baza.AppDatabase
import com.mygdx.game.baza.Objekt
import com.mygdx.game.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class MainActivity : AppCompatActivity(), CoordinateAddListener {
    lateinit var binding: ActivityMainBinding
    var fusedLocationClient: FusedLocationProviderClient? = null
    var dataStore: RxDataStore<Preferences>? = null
    var camera: Objekt? = null
    lateinit var objects: List<Objekt>
    //val cameraDataStoreKey: Preferences.Key<String> = stringPreferencesKey("camera_coordinates")

    lateinit var db: AppDatabase
    val locationPermissionRequest: ActivityResultLauncher<Array<String>> by lazy {
        registerForActivityResult<Array<String>, Map<String, Boolean>>(
            ActivityResultContracts.RequestMultiplePermissions(),
            ActivityResultCallback<Map<String, Boolean>> { result: Map<String, Boolean> ->
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
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        binding.openViewer.setOnClickListener {
            Log.d("ingo", "open viewer")
            val intent = Intent(this@MainActivity, AndroidLauncher::class.java)
            intent.putExtra("camera", Gson().toJson(camera))
            startActivity(intent)
        }
        binding.cameraSetCoordinates.setOnClickListener {
            updateCoordinates(){ objekt ->
                updateEditTexts()
                /*updateDataStore(
                    cameraDataStoreKey,
                    Gson().toJson(objekt)
                )*/
            }
        }
        binding.cameraSetCoordinatesFromEt.setOnClickListener(View.OnClickListener {
            val coordinates = binding.cameraCoordinatesEt.text.toString().split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (coordinates.size != 3) return@OnClickListener
            try {
                camera = Objekt(0,
                    coordinates[0].toFloat(),
                    coordinates[1].toFloat(),
                    coordinates[2].toFloat()
                )
                //updateDataStore(cameraDataStoreKey, Gson().toJson(camera))
                updateEditTexts()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        })

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()

        lifecycleScope.launch(Dispatchers.IO) {
            val objektDao = db.objektDao()
            objects = objektDao.getAll()
        }



        //dataStore = RxPreferenceDataStoreBuilder(this,  /*name=*/"settings").build()

        // Get intent, action and MIME type
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

        binding.refresh.setOnClickListener { updateEditTexts() }
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
        if(!hasLocationPermission()) return;
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
                        Toast.makeText(
                            this@MainActivity,
                            "Lokacija postavljena.",
                            Toast.LENGTH_SHORT
                        ).show()
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
            binding.cameraCoordinates.text = text
        }
    }

    /*fun readFromDataStore() {
        val cameraFlow = dataStore!!.data().map{prefs: Preferences -> prefs[cameraDataStoreKey] ?: "" }
        cameraFlow.subscribe(object : Subscriber<String?> {
            override fun onSubscribe(s: Subscription) {
                s.request(Long.MAX_VALUE) // Request all items
            }

            override fun onNext(s: String?) {
                camera = Gson().fromJson(s, Objekt::class.java)
                updateEditTexts()
            }

            override fun onError(t: Throwable) {}
            override fun onComplete() {}
        })
    }

    fun updateDataStore(INTEGER_KEY: Preferences.Key<String>, value: String) {
        val updateResult = dataStore!!.updateDataAsync { prefsIn: Preferences ->
            val mutablePreferences = prefsIn.toMutablePreferences()
            mutablePreferences.set(INTEGER_KEY, value)
            Single.just<Preferences>(mutablePreferences)
        }
        // The update is completed once updateResult is completed.
    }*/

    override fun onResume() {
        super.onResume()
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
}
