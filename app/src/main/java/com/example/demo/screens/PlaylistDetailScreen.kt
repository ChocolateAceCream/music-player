package com.example.demo.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.demo.components.AddSongsDialog
import com.example.demo.components.FavoriteIconButton
import com.example.demo.components.RenamePlaylistDialog
import com.example.demo.data.Song
import com.example.demo.service.PlaybackState
import com.example.demo.viewmodel.PlaylistDetailViewModel
import com.example.demo.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onNavigateBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: PlaylistDetailViewModel = viewModel()
) {
    val playlistWithSongs by viewModel.playlistWithSongs.collectAsState()
    val playbackState by playerViewModel.playbackState.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val showRenameDialog by viewModel.showRenameDialog.collectAsState()
    val renameError by viewModel.renameError.collectAsState()
    val showAddSongsDialog by viewModel.showAddSongsDialog.collectAsState()
    val availableSongs by viewModel.availableSongs.collectAsState()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlistWithSongs?.playlist?.name ?: "Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Show rename option only for user playlists
                    if (playlistWithSongs?.playlist?.isSystem == false) {
                        IconButton(onClick = { viewModel.showRenameDialog() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename Playlist"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Show Add Songs button only for user playlists
            if (playlistWithSongs?.playlist?.isSystem == false) {
                FloatingActionButton(
                    onClick = { viewModel.showAddSongsDialog() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Songs"
                    )
                }
            }
        }
    ) { paddingValues ->
        playlistWithSongs?.let { playlist ->
            if (playlist.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No songs in this playlist",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlist.songs.size) { index ->
                        val song = playlist.songs[index]
                        val isUserPlaylist = playlist.playlist.isSystem == false

                        SwipeToDismissSongItem(
                            song = song,
                            isPlaying = playbackState is PlaybackState.Playing && currentSong?.id == song.id,
                            isPaused = playbackState is PlaybackState.Paused && currentSong?.id == song.id,
                            onSongClick = {
                                // Set the entire playlist and start playing from this song
                                playerViewModel.setPlaylist(playlist.songs, index)
                            },
                            onFavoriteToggle = {
                                viewModel.toggleSongFavorite(song.id)
                            },
                            onDelete = if (isUserPlaylist) {
                                { viewModel.removeSongFromPlaylist(song.id) }
                            } else null
                        )
                    }
                }
            }
        }

        // Rename dialog
        if (showRenameDialog) {
            playlistWithSongs?.playlist?.let { playlist ->
                RenamePlaylistDialog(
                    currentName = playlist.name,
                    errorMessage = renameError,
                    onDismiss = { viewModel.hideRenameDialog() },
                    onConfirm = { newName -> viewModel.renamePlaylist(newName) }
                )
            }
        }

        // Add songs dialog
        if (showAddSongsDialog) {
            playlistWithSongs?.let { playlist ->
                val currentSongIds = playlist.songs.map { it.id }.toSet()
                AddSongsDialog(
                    availableSongs = availableSongs,
                    currentPlaylistSongIds = currentSongIds,
                    onDismiss = { viewModel.hideAddSongsDialog() },
                    onConfirm = { songIds -> viewModel.addSongsToPlaylist(songIds) }
                )
            }
        }
    }
}

@Composable
fun SwipeToDismissSongItem(
    song: Song,
    isPlaying: Boolean,
    isPaused: Boolean,
    onSongClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (onDelete != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongItem(
                song = song,
                isPlaying = isPlaying,
                isPaused = isPaused,
                onSongClick = onSongClick,
                onFavoriteToggle = onFavoriteToggle,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        SongItem(
            song = song,
            isPlaying = isPlaying,
            isPaused = isPaused,
            onSongClick = onSongClick,
            onFavoriteToggle = onFavoriteToggle,
            modifier = modifier
        )
    }
}

@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    isPaused: Boolean,
    onSongClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.author,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = song.album,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }

            // Favorite icon
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
