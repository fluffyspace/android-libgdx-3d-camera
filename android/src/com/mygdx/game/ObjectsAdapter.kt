package com.mygdx.game

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mygdx.game.baza.Objekt

class ObjectsAdapter(var dataSet: MutableList<Objekt>, var listener: ObjectClickListener) :
    RecyclerView.Adapter<ObjectsAdapter.ViewHolder>() {

    interface ObjectClickListener {
        fun onClickOnObject(objekt: Objekt)
        fun onLongClickOnObject(objekt: Objekt)
    }

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val objectName: TextView
        val objectCoordinates: TextView
        val color: View

        init {
            // Define click listener for the ViewHolder's View
            objectName = view.findViewById(R.id.object_name)
            objectCoordinates = view.findViewById(R.id.object_coordinates)
            color = view.findViewById(R.id.color)
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.objekt_row_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.color.setBackgroundColor(dataSet[position].color)
        viewHolder.objectName.text = dataSet[position].name
        viewHolder.objectCoordinates.text = "${dataSet[position].x}, ${dataSet[position].y}, ${dataSet[position].z}"
        viewHolder.itemView.setOnClickListener {
            listener.onClickOnObject(dataSet[position])
        }
        viewHolder.itemView.setOnLongClickListener {
            listener.onLongClickOnObject(dataSet[position])
            return@setOnLongClickListener true
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size

}
