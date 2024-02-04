package com.mygdx.game

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder
import androidx.datastore.rxjava3.RxDataStore
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.mygdx.game.databinding.ActivityMainBinding
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Function
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class MainActivity : AppCompatActivity(), CoordinateAddListener {
    lateinit var binding: ActivityMainBinding
    var fusedLocationClient: FusedLocationProviderClient? = null
    var dataStore: RxDataStore<Preferences>? = null
    var camera_coordinates = DoubleArray(3)
    var object_coordinates = DoubleArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        binding.openViewer.setOnClickListener {
            Log.d("ingo", "open viewer")
            val intent = Intent(this@MainActivity, AndroidLauncher::class.java)
            intent.putExtra("object_coordinates", Gson().toJson(object_coordinates))
            intent.putExtra("camera_coordinates", Gson().toJson(camera_coordinates))
            startActivity(intent)
        }
        binding.objectSetCoordinates.setOnClickListener(View.OnClickListener { updateCoordinates("object") })
        binding.cameraSetCoordinates.setOnClickListener(View.OnClickListener { updateCoordinates("camera") })
        binding.cameraSetCoordinatesFromEt.setOnClickListener(View.OnClickListener {
            val coordinates = binding.cameraCoordinatesEt.getText().toString().split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (coordinates.size != 3) return@OnClickListener
            try {
                camera_coordinates = doubleArrayOf(
                    coordinates[0].toDouble(),
                    coordinates[1].toDouble(),
                    coordinates[2].toDouble()
                )
                updateDataStore("camera", Gson().toJson(camera_coordinates))
                updateEditTexts()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        })
        binding.objectSetCoordinatesFromEt.setOnClickListener(View.OnClickListener {
            val coordinates = binding.objectCoordinatesEt.getText().toString().split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (coordinates.size != 3) return@OnClickListener
            try {
                object_coordinates = doubleArrayOf(
                    coordinates[0].toDouble(),
                    coordinates[1].toDouble(),
                    coordinates[2].toDouble()
                )
                updateDataStore("object", Gson().toJson(object_coordinates))
                updateEditTexts()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        })
        dataStore = RxPreferenceDataStoreBuilder(this,  /*name=*/"settings").build()
        //
        val locationPermissionRequest =
            registerForActivityResult<Array<String>, Map<String, Boolean>>(
                ActivityResultContracts.RequestMultiplePermissions(),
                ActivityResultCallback<Map<String, Boolean>> { result: Map<String, Boolean> ->
                    val fineLocationGranted = result.getOrDefault(
                        Manifest.permission.ACCESS_FINE_LOCATION, false
                    )
                    val coarseLocationGranted = result.getOrDefault(
                        Manifest.permission.ACCESS_COARSE_LOCATION, false
                    )
                    if (fineLocationGranted != null && fineLocationGranted) {
                        // Precise location access granted.
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        // Only approximate location access granted.
                    } else {
                        // No location access granted.
                    }
                }
            )
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
        }


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
        readFromDataStore()
        binding.refresh.setOnClickListener { updateEditTexts() }
    }

    fun updateCoordinates(type: String) {
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
                        if (type == "object") {
                            object_coordinates =
                                doubleArrayOf(location.latitude, location.longitude, 0.0)
                            updateEditTexts()
                        } else {
                            camera_coordinates =
                                doubleArrayOf(location.latitude, location.longitude, 0.0)
                            updateEditTexts()
                        }
                        updateDataStore(
                            type,
                            Gson().toJson(doubleArrayOf(location.latitude, location.longitude, 0.0))
                        )
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
        if (object_coordinates.size == 3) {
            Log.d("ingo", "object coordinates da")
            val text =
                object_coordinates[0].toString() + ", " + object_coordinates[1] + ", " + object_coordinates[2]
            binding.objectCoordinatesEt!!.setText(text)
            binding.objectCoordinates!!.text = text
        }
        if (camera_coordinates.size == 3) {
            Log.d("ingo", "camera coordinates da")
            val text =
                camera_coordinates[0].toString() + ", " + camera_coordinates[1] + ", " + camera_coordinates[2]
            binding.cameraCoordinatesEt!!.setText(text)
            binding.cameraCoordinates!!.text = text
        }
    }

    fun readFromDataStore() {
        val object_key: Preferences.Key<String> = stringPreferencesKey("object_coordinates")
        val camera_key: Preferences.Key<String> = stringPreferencesKey("camera_coordinates")
        val object_flow = this.dataStore!!.data().map{prefs: Preferences -> prefs[object_key] ?: "" }
        object_flow.subscribe(object : Subscriber<String> {
            override fun onSubscribe(s: Subscription) {
                s.request(Long.MAX_VALUE) // Request all items
            }

            override fun onNext(s: String?) {
                object_coordinates = Gson().fromJson(s, DoubleArray::class.java)
                updateEditTexts()
            }

            override fun onError(t: Throwable) {}
            override fun onComplete() {}
        })
        val cameraFlow = dataStore!!.data().map{prefs: Preferences -> prefs[camera_key] ?: "" }
        cameraFlow.subscribe(object : Subscriber<String?> {
            override fun onSubscribe(s: Subscription) {
                s.request(Long.MAX_VALUE) // Request all items
            }

            override fun onNext(s: String?) {
                camera_coordinates = Gson().fromJson(s, DoubleArray::class.java)
                updateEditTexts()
            }

            override fun onError(t: Throwable) {}
            override fun onComplete() {}
        })
    }

    fun updateDataStore(type: String, value: String) {
        val INTEGER_KEY: Preferences.Key<String>
        if (type == "object") {
            INTEGER_KEY = stringPreferencesKey("object_coordinates")
        } else {
            INTEGER_KEY = stringPreferencesKey("camera_coordinates")
        }
        val updateResult = dataStore!!.updateDataAsync { prefsIn: Preferences ->
            val mutablePreferences = prefsIn.toMutablePreferences()
            mutablePreferences.set(INTEGER_KEY, value)
            Single.just<Preferences>(mutablePreferences)
        }
        // The update is completed once updateResult is completed.
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun add(type: String) {
        updateCoordinates(type)
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
