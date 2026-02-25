package com.example.demo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.demo.components.FavoriteIconButton
import com.example.demo.service.PlaybackState
import com.example.demo.viewmodel.PlayerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onShowPlaylist: () -> Unit
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playbackState by playerViewModel.playbackState.collectAsState()
    val progress by playerViewModel.progress.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val isShuffleEnabled by playerViewModel.isShuffleEnabled.collectAsState()
    val isFavorite by playerViewModel.isFavorite.collectAsState()

    val scrollState = rememberScrollState()
    var totalDragAmountVertical by remember { mutableStateOf(0f) }
    var totalDragAmountHorizontal by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scrollable content taking available vertical space above controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(scrollState)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                totalDragAmountVertical = 0f
                                totalDragAmountHorizontal = 0f
                            },
                            onDragEnd = {
                                // Check vertical swipe down to dismiss
                                if (scrollState.value == 0 && totalDragAmountVertical > 150) {
                                    onNavigateBack()
                                }
                                // Check horizontal swipe left for next song
                                else if (totalDragAmountHorizontal < -150) {
                                    playerViewModel.playNext()
                                }
                                // Check horizontal swipe right for previous song
                                else if (totalDragAmountHorizontal > 150) {
                                    playerViewModel.playPrevious()
                                }
                                totalDragAmountVertical = 0f
                                totalDragAmountHorizontal = 0f
                            },
                            onDragCancel = {
                                totalDragAmountVertical = 0f
                                totalDragAmountHorizontal = 0f
                            },
                            onDrag = { change, dragAmount ->
                                // Accumulate vertical drag (positive = down, negative = up)
                                if (scrollState.value == 0 && dragAmount.y > 0) {
                                    totalDragAmountVertical += dragAmount.y
                                    change.consume()
                                }
                                // Accumulate horizontal drag (negative = left, positive = right)
                                totalDragAmountHorizontal += dragAmount.x
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Album art and song info (only if we have a song)
                currentSong?.let { song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        AsyncImage(
                            model = song.coverPageLink,
                            contentDescription = "Album art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = song.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = song.author,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = song.album,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        FavoriteIconButton(
                            isFavorite = isFavorite,
                            onToggle = { playerViewModel.toggleFavorite() }
                        )
                    }
                }
            }

            // Fixed bottom controls + progress bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Progress bar
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            val newPosition = (newProgress * duration).roundToInt()
                            playerViewModel.seekTo(newPosition)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = androidx.compose.ui.graphics.Color.Transparent,
                            activeTrackColor = androidx.compose.ui.graphics.Color(0xFFFF69B4), // Hot pink
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledThumbColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledActiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledInactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledActiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledInactiveTickColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        thumb = {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.size(12.dp)
                            ) {
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color(0xFFFF69B4) // Hot pink dot only
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(duration),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Control buttons (fixed at bottom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle button
                    IconButton(
                        onClick = { playerViewModel.toggleShuffle() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Previous button
                    IconButton(
                        onClick = { playerViewModel.playPrevious() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Play/Pause button
                    FilledIconButton(
                        onClick = { playerViewModel.togglePlayPause() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState is PlaybackState.Playing) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (playbackState is PlaybackState.Playing) "Pause" else "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next button
                    IconButton(
                        onClick = { playerViewModel.playNext() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Playlist button
                    IconButton(
                        onClick = onShowPlaylist
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Playlist",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Int): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
