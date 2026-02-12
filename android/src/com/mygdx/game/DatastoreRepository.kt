package com.mygdx.game

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.mygdx.game.baza.Objekt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatastoreRepository {



    companion object {
        val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        val cameraDataStoreKey: Preferences.Key<String> = stringPreferencesKey("camera_coordinates")
        val disabledCategoriesKey: Preferences.Key<String> = stringPreferencesKey("disabled_categories")

        fun updateDataStore(context: Context, INTEGER_KEY: Preferences.Key<String>, value: String) {
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { settings ->
                    settings[INTEGER_KEY] = value
                }
            }
        }

        fun readFromDataStore(context: Context, afterwards: (Objekt) -> Unit) {
            val cameraFlow: Flow<Objekt?> = context.dataStore.data.map{ prefs: Preferences ->
                val objekt = prefs[cameraDataStoreKey] ?: ""
                try {
                    Log.d("ingo", "dohvaćam ju mmmm $objekt")
                    Gson().fromJson(objekt, Objekt::class.java)
                }catch (e: JsonSyntaxException){
                    Log.d("ingo", "nemoguće dohvatiti kameru")
                    null
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                cameraFlow.first()?.let{
                    afterwards(it)
                }
            }
        }

        fun readDisabledCategories(context: Context, callback: (Set<String>) -> Unit) {
            val flow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
                val json = prefs[disabledCategoriesKey] ?: ""
                if (json.isBlank()) emptySet()
                else try {
                    Gson().fromJson(json, Array<String>::class.java).toSet()
                } catch (e: JsonSyntaxException) {
                    emptySet()
                }
            }
            CoroutineScope(Dispatchers.IO).launch {
                callback(flow.first())
            }
        }
    }
}