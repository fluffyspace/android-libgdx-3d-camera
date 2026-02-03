package com.mygdx.game.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.mygdx.game.AddCoordinateFragment
import com.mygdx.game.CoordinateAddListener
import com.mygdx.game.baza.Objekt
import com.mygdx.game.ui.screens.MainScreen
import com.mygdx.game.ui.theme.MyGdxGameTheme
import com.mygdx.game.viewmodel.MainViewModel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class MainActivity : ComponentActivity(), CoordinateAddListener {
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var viewModel: MainViewModel

    private val locationPermissionRequest: ActivityResultLauncher<Array<String>> by lazy {
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

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get text passed to app for adding new objects
        val intentAction = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == intentAction && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                Log.d("ingo", sharedText!!)
                Thread {
                    val expandedURL1 = expandGoogleMapsUrl(sharedText)
                    Log.d("Expanded", "run: $expandedURL1")
                }.start()
                // Note: AddCoordinateFragment still uses supportFragmentManager
                // This will need to be migrated or handled differently
            }
        }

        hasLocationPermission()

        setContent {
            MyGdxGameTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenMap = { objekt ->
                        val mapIntent = Intent(this@MainActivity, MapViewer::class.java)
                        mapIntent.putExtra("coordinates", Gson().toJson(objekt))
                        startActivity(mapIntent)
                    },
                    onOpenViewer = { camera, objects ->
                        Log.d("ingo", "open viewer")
                        val viewerIntent = Intent(this@MainActivity, AndroidLauncher::class.java)
                        viewerIntent.putExtra("camera", Gson().toJson(camera))
                        viewerIntent.putExtra("objects", Gson().toJson(objects))
                        startActivity(viewerIntent)
                    },
                    onUpdateLocation = { callback ->
                        updateCoordinates(callback)
                    },
                    onShowInvalidCoordinatesToast = {
                        Toast.makeText(this, "Invalid coordinates.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }



    override fun onResume() {
        super.onResume()
        viewModel.loadObjects()
    }

    private fun hasLocationPermission(): Boolean {
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

    private fun updateCoordinates(onLocationResult: (Objekt) -> Unit) {
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
                    Log.d("ingo", "onSuccess")
                    if (location != null) {
                        Log.d("ingo", "not null")
                        onLocationResult(
                            Objekt(
                                0,
                                location.latitude.toFloat(),
                                location.longitude.toFloat(),
                                0f
                            )
                        )
                    }
                }.addOnFailureListener { e ->
                    Log.d("ingo", "onFailure")
                    e.printStackTrace()
                }
        }
    }

    override fun onCoordinatesPassed(isCamera: Boolean) {
        // Legacy callback - no longer used with Compose
    }

    companion object {
        const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
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
