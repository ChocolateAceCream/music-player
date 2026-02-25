package com.example.demo.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.demo.data.Song

@Composable
fun AddSongsDialog(
    availableSongs: List<Song>,
    currentPlaylistSongIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val selectedSongIds = remember { mutableStateListOf<Long>() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Songs") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                items(availableSongs) { song ->
                    val isInPlaylist = currentPlaylistSongIds.contains(song.id)
                    val isSelected = selectedSongIds.contains(song.id)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isInPlaylist) {
                                if (isSelected) {
                                    selectedSongIds.remove(song.id)
                                } else {
                                    selectedSongIds.add(song.id)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected || isInPlaylist,
                            onCheckedChange = null,
                            enabled = !isInPlaylist
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = song.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedSongIds.toList()) },
                enabled = selectedSongIds.isNotEmpty()
            ) {
                Text("Add (${selectedSongIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
