package com.mygdx.game.ui.dialogs

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mygdx.game.baza.Objekt

@Composable
fun AddOrEditObjectDialog(
    objectToEdit: Objekt?,
    initialCoordinates: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (coordinates: String, name: String, color: String) -> Unit
) {
    var coordinates by remember {
        mutableStateOf(
            objectToEdit?.let { "${it.x}, ${it.y}, ${it.z}" }
                ?: initialCoordinates
                ?: ""
        )
    }
    var name by remember { mutableStateOf(objectToEdit?.name ?: "") }
    var colorText by remember {
        mutableStateOf(
            objectToEdit?.let { String.format("#%06X", 0xFFFFFF and it.color) } ?: ""
        )
    }
    var previewColor by remember {
        mutableStateOf(
            objectToEdit?.let { Color(it.color) } ?: Color.White
        )
    }

    LaunchedEffect(colorText) {
        previewColor = try {
            Color(AndroidColor.parseColor(colorText))
        } catch (e: Exception) {
            Color.White
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (objectToEdit == null) "Add new object" else "Edit object",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = coordinates,
                    onValueChange = { coordinates = it },
                    label = { Text("Coordinates (lat, lon, alt)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = colorText,
                        onValueChange = { colorText = it },
                        label = { Text("Color (hex)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("#FFFFFF") }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parts = coordinates.split(",").map { it.trim() }
                    if (parts.size == 3) {
                        onConfirm(coordinates, name, colorText)
                    }
                }
            ) {
                Text(if (objectToEdit == null) "Add" else "Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
