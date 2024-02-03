package com.mygdx.game;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Arrays;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public class MainActivity extends AppCompatActivity {

    TextView camera_coordinates_tv;
    TextView object_coordinates_tv;
    Button open_view;
    Button camera_coordinates_button;
    Button object_coordinates_button;
    FusedLocationProviderClient fusedLocationClient;
    RxDataStore<Preferences> dataStore;
    double[] camera_coordinates = new double[3];
    double[] object_coordinates = new double[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        camera_coordinates_tv = findViewById(R.id.camera_coordinates);
        object_coordinates_tv = findViewById(R.id.object_coordinates);
        open_view = findViewById(R.id.open_viewer);
        open_view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this,AndroidLauncher.class);
                startActivity(intent);
            }
        });
        camera_coordinates_button = findViewById(R.id.camera_set_coordinates);
        object_coordinates_button = findViewById(R.id.object_set_coordinates);
        object_coordinates_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateCoordinates(new Preferences.Key<String>("object_coordinates"));
            }
        });
        camera_coordinates_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateCoordinates(new Preferences.Key<String>("camera_coordinates"));
            }
        });
        dataStore =
                new RxPreferenceDataStoreBuilder(this, /*name=*/ "settings").build();
        //
        ActivityResultLauncher<String[]> locationPermissionRequest =
                registerForActivityResult(new ActivityResultContracts
                                .RequestMultiplePermissions(), result -> {
                            Boolean fineLocationGranted = result.getOrDefault(
                                    Manifest.permission.ACCESS_FINE_LOCATION, false);
                            Boolean coarseLocationGranted = result.getOrDefault(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,false);
                            if (fineLocationGranted != null && fineLocationGranted) {
                                // Precise location access granted.
                            } else if (coarseLocationGranted != null && coarseLocationGranted) {
                                // Only approximate location access granted.
                            } else {
                                // No location access granted.
                            }
                        }
                );

// ...

// Before you perform the actual permission request, check whether your app
// already has the permissions, and whether your app needs to show a permission
// rationale dialog. For more details, see Request permissions.
        locationPermissionRequest.launch(new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });

    }

    void updateCoordinates(Preferences.Key<String> INTEGER_KEY){
        Log.d("ingo", "updateCoordinates");
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d("ingo", "has permission");
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(MainActivity.this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            Log.d("ingo", "onSuccess");
                            if (location != null) {
                                Log.d("ingo", "not null");
                                // Logic to handle location object
                                if(INTEGER_KEY.getName().equals("object_coordinates")){
                                    object_coordinates = new double[]{location.getLatitude(), location.getLongitude(), 0};
                                    object_coordinates_tv.setText(Arrays.toString(object_coordinates));
                                } else {
                                    camera_coordinates = new double[]{location.getLatitude(), location.getLongitude(), 0};
                                    camera_coordinates_tv.setText(Arrays.toString(camera_coordinates));
                                }
                                updateDataStore(INTEGER_KEY, new Gson().toJson(new double[]{location.getLatitude(), location.getLongitude(), 0}));
                                Toast.makeText(MainActivity.this, "Lokacija postavljena.", Toast.LENGTH_SHORT).show();
                            }

                        }

                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d("ingo", "onFailure");
                            e.printStackTrace();
                        }
                    });
        }
    }

    void readFromDataStore(){
        Preferences.Key<String> object_key = PreferencesKeys.stringKey("object_coordinates");
        Preferences.Key<String> camera_key = PreferencesKeys.stringKey("camera_coordinates");

        Flowable<String> exampleCounterFlow =
                dataStore.data().map(prefs -> prefs.get(object_key));
        exampleCounterFlow.subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE); // Request all items
            }

            @Override
            public void onNext(String s) {
                camera_coordinates = new Gson().fromJson(s, double[].class);
                camera_coordinates_tv.setText(Arrays.toString(camera_coordinates));
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });
        Flowable<String> exampleCounterFlow2 =
                dataStore.data().map(prefs -> prefs.get(camera_key));
        exampleCounterFlow2.subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE); // Request all items
            }

            @Override
            public void onNext(String s) {
                object_coordinates = new Gson().fromJson(s, double[].class);
                object_coordinates_tv.setText(Arrays.toString(object_coordinates));
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    void updateDataStore(Preferences.Key<String> INTEGER_KEY, String value){
        Single<Preferences> updateResult =  dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(INTEGER_KEY, value);
            return Single.just(mutablePreferences);
        });
// The update is completed once updateResult is completed.

    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
    }
}
