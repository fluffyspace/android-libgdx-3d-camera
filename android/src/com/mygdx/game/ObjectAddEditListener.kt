package com.mygdx.game

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mygdx.game.baza.Objekt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObjectAddEditListener(var addObject: (objekt: Objekt) -> (Unit), var editObject: (objekt: Objekt) -> (Unit)) : AddOrEditObjectDialog.AddOrEditObjectDialogListener {

    override fun onClickAddObject(coordinates: String, name: String, color: String) {

        textToObject(coordinates)?.apply {
            this.name = name
            if(color != "") {
                try {
                    this.color = Color.parseColor(color)
                }catch(e: NumberFormatException){

                }
            }
        }?.let { objekt ->
            addObject(objekt)
        }
    }

    override fun onClickEditObject(
        objekt: Objekt,
        coordinates: String,
        name: String,
        color: String
    ) {
            textToObject(coordinates)?.apply {
                this.name = name
                if(color != "") {
                    try {
                        this.color = Color.parseColor(color)
                    }catch(e: NumberFormatException){

                    }
                }
                this.id = objekt.id
            }?.let { objekt ->
                editObject(objekt)
            }
    }

    companion object {
        fun textToObject(text: String): com.mygdx.game.baza.Objekt? {
            val coordinates = text.split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            Log.d("ingo", coordinates.contentToString())
            if (coordinates.size != 3) return null
            try {
                return com.mygdx.game.baza.Objekt(
                    0,
                    coordinates[0].toFloat(),
                    coordinates[1].toFloat(),
                    coordinates[2].toFloat()
                )
                //updateDataStore(cameraDataStoreKey, Gson().toJson(camera))
            } catch (e: NullPointerException) {
                e.printStackTrace()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            return null
        }
    }
}