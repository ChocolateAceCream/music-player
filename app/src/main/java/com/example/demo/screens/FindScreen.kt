package com.example.demo.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.demo.components.AddToPlaylistDialog
import com.example.demo.components.FavoriteIconButton
import com.example.demo.data.Song
import com.example.demo.service.PlaybackState
import com.example.demo.viewmodel.FindViewModel
import com.example.demo.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindScreen(
    playerViewModel: PlayerViewModel,
    viewModel: FindViewModel = viewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val playbackState by playerViewModel.playbackState.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                // Multi-select mode top bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedSongIds.size} selected",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitMultiSelectMode() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.showAddToPlaylistDialog() },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Add to Playlist"
                            )
                        }
                    }
                )
            } else {
                // Normal mode top bar with search
                TopAppBar(
                    title = {
                        Text("Find")
                    },
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        focusManager.clearFocus()
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar (hide in multi-select mode)
            if (!isMultiSelectMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search by name/artist/album") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // Results list
            if (filteredSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            focusManager.clearFocus()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) {
                            "No songs available"
                        } else {
                            "No results found"
                        },
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            focusManager.clearFocus()
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSongs) { song ->
                        val isSelected = selectedSongIds.contains(song.id)
                        
                        FindSongItem(
                            song = song,
                            isPlaying = playbackState is PlaybackState.Playing && currentSong?.id == song.id,
                            isPaused = playbackState is PlaybackState.Paused && currentSong?.id == song.id,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = isSelected,
                            onSongClick = {
                                if (isMultiSelectMode) {
                                    viewModel.toggleSongSelection(song.id)
                                } else {
                                    // Play this song and set all filtered songs as playlist
                                    val index = filteredSongs.indexOf(song)
                                    playerViewModel.setPlaylist(filteredSongs, index)
                                }
                            },
                            onSongLongPress = {
                                if (!isMultiSelectMode) {
                                    viewModel.enterMultiSelectMode(song.id)
                                }
                            },
                            onFavoriteToggle = {
                                viewModel.toggleSongFavorite(song.id)
                            }
                        )
                    }
                }
            }
        }

        // Add to playlist dialog
        if (showAddToPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = allPlaylists,
                onDismiss = { viewModel.hideAddToPlaylistDialog() },
                onPlaylistSelected = { playlistId ->
                    viewModel.addSelectedSongsToPlaylist(playlistId)
                },
                onCreateNew = { playlistName ->
                    viewModel.createPlaylistAndAddSongs(playlistName)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FindSongItem(
    song: Song,
    isPlaying: Boolean,
    isPaused: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSongClick: () -> Unit,
    onSongLongPress: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSongClick,
                onLongClick = onSongLongPress
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                isPlaying -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox in multi-select mode
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSongClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Album art
            Card(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.small
            ) {
                AsyncImage(
                    model = song.coverPageLink,
                    contentDescription = "Album art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                        isPlaying -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.author,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        isPlaying -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = song.album,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        isPlaying -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }

            // Favorite icon (hide in multi-select mode)
            if (!isMultiSelectMode) {
                FavoriteIconButton(
                    isFavorite = song.isFavorite,
                    onToggle = onFavoriteToggle,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Play/Pause indicator
                if (isPlaying || isPaused) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Playing" else "Paused",
                        tint = if (isPlaying) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}
