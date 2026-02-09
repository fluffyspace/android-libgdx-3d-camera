package com.mygdx.game.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mygdx.game.R
import com.mygdx.game.baza.UserBuilding

enum class BuildingUploadStatus {
    PENDING, UPLOADING, SUCCESS, ERROR
}

data class BuildingUploadState(
    val building: UserBuilding,
    val status: BuildingUploadStatus = BuildingUploadStatus.PENDING,
    val error: String? = null
)

@Composable
fun OsmUploadDialog(
    buildings: List<UserBuilding>,
    uploadStates: Map<Int, BuildingUploadState>,
    isUploading: Boolean,
    uploadDone: Boolean,
    onUpload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var comment by remember { mutableStateOf("Building height survey") }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = { Text("Upload to OpenStreetMap") },
        text = {
            Column {
                if (buildings.isEmpty()) {
                    Text("No buildings with OSM IDs to upload.")
                } else {
                    Text(
                        "${buildings.size} building(s) with modified heights",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Changeset comment") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isUploading && !uploadDone
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(buildings, key = { it.id }) { building ->
                            val state = uploadStates[building.id]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = building.name.ifEmpty { "Way #${building.osmId}" },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "h=${building.heightMeters}m" +
                                                if (building.minHeightMeters > 0) ", min=${building.minHeightMeters}m" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                when (state?.status) {
                                    BuildingUploadStatus.UPLOADING -> {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                    BuildingUploadStatus.SUCCESS -> {
                                        Icon(
                                            painter = painterResource(id = android.R.drawable.ic_menu_save),
                                            contentDescription = "Success",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    BuildingUploadStatus.ERROR -> {
                                        Text(
                                            text = "Error",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "Pending",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uploadDone) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val successCount = uploadStates.values.count { it.status == BuildingUploadStatus.SUCCESS }
                        val errorCount = uploadStates.values.count { it.status == BuildingUploadStatus.ERROR }
                        Text(
                            text = "Done: $successCount succeeded, $errorCount failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (uploadDone || buildings.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            } else {
                Button(
                    onClick = { onUpload(comment) },
                    enabled = !isUploading && buildings.isNotEmpty()
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Upload")
                }
            }
        },
        dismissButton = {
            if (!uploadDone && !isUploading) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
