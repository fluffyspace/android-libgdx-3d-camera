package com.mygdx.game.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mygdx.game.network.OverpassServer

@Composable
fun OverpassServerDialog(
    servers: List<OverpassServer>,
    selectedServerId: String,
    onSelect: (String) -> Unit,
    onAdd: (name: String, url: String) -> Unit,
    onUpdate: (id: String, name: String, url: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddEdit by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<OverpassServer?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Overpass Server", modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    editingServer = null
                    showAddEdit = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add server")
                }
            }
        },
        text = {
            LazyColumn {
                items(servers, key = { it.id }) { server ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(server.id) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = server.id == selectedServerId,
                            onClick = { onSelect(server.id) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                server.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!server.isDefault) {
                            IconButton(onClick = {
                                editingServer = server
                                showAddEdit = true
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDelete(server.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showAddEdit) {
        AddEditServerDialog(
            server = editingServer,
            onConfirm = { name, url ->
                val editing = editingServer
                if (editing != null) {
                    onUpdate(editing.id, name, url)
                } else {
                    onAdd(name, url)
                }
                showAddEdit = false
                editingServer = null
            },
            onDismiss = {
                showAddEdit = false
                editingServer = null
            }
        )
    }
}

@Composable
private fun AddEditServerDialog(
    server: OverpassServer?,
    onConfirm: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    var url by remember { mutableStateOf(server?.url ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server != null) "Edit Server" else "Add Server") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    placeholder = { Text("https://your-server/api/interpreter") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), url.trim()) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
