package com.example.demo.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.demo.service.PlaybackState
import com.example.demo.viewmodel.PlayerViewModel

@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playbackState by playerViewModel.playbackState.collectAsState()
    val progress by playerViewModel.progress.collectAsState()

    currentSong?.let { song ->
        Surface(
            modifier = modifier
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp
        ) {
            Column {
                // Progress bar with hot pink color
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color(0xFFFF69B4), // Hot pink
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album art - clickable
                    Card(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                playerViewModel.refreshPlayerState()
                                onExpand()
                            },
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

                    // Song info - clickable
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                playerViewModel.refreshPlayerState()
                                onExpand()
                            }
                    ) {
                        Text(
                            text = song.name,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = song.author,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    // Controls
                    IconButton(onClick = {
                        playerViewModel.playPrevious()
                        playerViewModel.refreshPlayerState()
                    }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = androidx.compose.ui.graphics.Color(0xFFFF69B4) // Hot pink
                        )
                    }

                    // Play/Pause button with hot pink background
                    FilledIconButton(
                        onClick = {
                            playerViewModel.togglePlayPause()
                            playerViewModel.refreshPlayerState()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFFF69B4), // Hot pink
                            contentColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (playbackState is PlaybackState.Playing) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (playbackState is PlaybackState.Playing) "Pause" else "Play"
                        )
                    }

                    IconButton(onClick = {
                        playerViewModel.playNext()
                        playerViewModel.refreshPlayerState()
                    }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = androidx.compose.ui.graphics.Color(0xFFFF69B4) // Hot pink
                        )
                    }
                }
            }
        }
    }
}
