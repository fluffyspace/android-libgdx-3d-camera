package com.mygdx.game

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class AddOrEditObjectDialog(var objectToEdit: com.mygdx.game.baza.Objekt?): DialogFragment() {

    internal lateinit var listener: AddOrEditObjectDialogListener

    lateinit var colorPreview: View
    lateinit var coordinatesEditText: EditText
    lateinit var nameEditText: EditText
    lateinit var colorEditText: EditText

    // The activity that creates an instance of this dialog fragment must
    // implement this interface to receive event callbacks. Each method passes
    // the DialogFragment in case the host needs to query it.
    interface AddOrEditObjectDialogListener {
        fun onClickAddObject(coordinates: String, name: String, color: String)
        fun onClickEditObject(objekt: com.mygdx.game.baza.Objekt, coordinates: String, name: String, color: String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface.
        try {
            // Instantiate the AddNewObjectDialogListener so you can send events to
            // the host.
            listener = context as AddOrEditObjectDialogListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface. Throw exception.
            throw ClassCastException((context.toString() +
                    " must implement AddNewObjectDialogListener"))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { activity ->
            val builder = AlertDialog.Builder(activity)
            // Get the layout inflater.
            val inflater = requireActivity().layoutInflater;

            // Inflate and set the layout for the dialog.
            // Pass null as the parent view because it's going in the dialog
            // layout.
            val view = inflater.inflate(R.layout.adding_object_layout, null)

            colorPreview = view.findViewById<View>(R.id.color_preview)
            coordinatesEditText = view.findViewById<EditText>(R.id.coordinates)
            nameEditText = view.findViewById<EditText>(R.id.name)
            colorEditText = view.findViewById<EditText>(R.id.color)
            colorEditText.addTextChangedListener(object : TextWatcher{
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    try {
                        colorPreview.setBackgroundColor(Color.parseColor(p0.toString()))
                    } catch (e: IllegalArgumentException){

                    }
                }

                override fun afterTextChanged(p0: Editable?) {

                }

            })

            if(objectToEdit != null){
                view.findViewById<TextView>(R.id.dialog_title).text = "Editing object"
                val hex = String.format("#%06X", 0xFFFFFF and objectToEdit!!.color)
                colorEditText.setText(hex)
                coordinatesEditText.setText("${objectToEdit!!.x}, ${objectToEdit!!.y}, ${objectToEdit!!.z}")
                nameEditText.setText(objectToEdit!!.name)
                colorPreview.setBackgroundColor(objectToEdit!!.color)
            }

            builder.setView(view)
                // Add action buttons.
                .setPositiveButton(if(objectToEdit == null) "Add" else "Edit", null)
                .setNegativeButton("Cancel",
                    DialogInterface.OnClickListener { dialog, id ->
                        dismiss()
                    })
                .setCancelable(false)
            builder.create().apply {
                setOnShowListener {dialoginteface ->
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {listenerview ->
                        val coordinates = coordinatesEditText.text.toString().split(",".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                        Log.d("ingo", coordinates.contentToString())
                        if (coordinates.size != 3) return@setOnClickListener
                        if(objectToEdit == null) {
                            listener.onClickAddObject(
                                coordinatesEditText.text.toString(),
                                nameEditText.text.toString(),
                                colorEditText.text.toString(),
                            )
                        } else {
                            listener.onClickEditObject(
                                objectToEdit!!,
                                coordinatesEditText.text.toString(),
                                nameEditText.text.toString(),
                                colorEditText.text.toString(),
                            )
                        }
                        dismiss()
                    }
                }
            }
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}