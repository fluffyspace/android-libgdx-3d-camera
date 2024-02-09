package com.mygdx.game

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.fragment.app.DialogFragment

class AddNewObjectDialog: DialogFragment() {

    internal lateinit var listener: AddNewObjectDialogListener

    // The activity that creates an instance of this dialog fragment must
    // implement this interface to receive event callbacks. Each method passes
    // the DialogFragment in case the host needs to query it.
    interface AddNewObjectDialogListener {
        fun onClickAddObject(coordinates: String, name: String, color: String)
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface.
        try {
            // Instantiate the AddNewObjectDialogListener so you can send events to
            // the host.
            listener = context as AddNewObjectDialogListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface. Throw exception.
            throw ClassCastException((context.toString() +
                    " must implement AddNewObjectDialogListener"))
        }

    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            // Get the layout inflater.
            val inflater = requireActivity().layoutInflater;

            // Inflate and set the layout for the dialog.
            // Pass null as the parent view because it's going in the dialog
            // layout.
            val view = inflater.inflate(R.layout.adding_object_layout, null)
            val colorPreview = view.findViewById<View>(R.id.color_preview)
            view.findViewById<EditText>(R.id.color).addTextChangedListener(object : TextWatcher{
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
            builder.setView(view)
                // Add action buttons.
                .setPositiveButton("Add",
                    DialogInterface.OnClickListener { dialog, id ->
                        listener.onClickAddObject(
                            view.findViewById<EditText>(R.id.coordinates).text.toString(),
                            view.findViewById<EditText>(R.id.name).text.toString(),
                            view.findViewById<EditText>(R.id.color).text.toString(),
                        )
                    })
                .setNegativeButton("Cancel",
                    DialogInterface.OnClickListener { dialog, id ->
                        dismiss()
                    })
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}