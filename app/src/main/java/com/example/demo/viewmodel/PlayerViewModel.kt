package com.example.demo.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.AppDatabase
import com.example.demo.data.PlaylistRepository
import com.example.demo.data.Song
import com.example.demo.data.SongRepository
import com.example.demo.service.MusicPlaybackService
import com.example.demo.service.MusicPlayer
import com.example.demo.service.PlaybackState
import com.example.demo.service.PlayMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val songRepository: SongRepository
    private val playlistRepository: PlaylistRepository
    private var musicPlaybackService: MusicPlaybackService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("PlayerViewModel", "Service connected")
            val binder = service as MusicPlaybackService.MusicBinder
            musicPlaybackService = binder.getService()
            serviceBound = true
            
            // Set the play next callback
            musicPlaybackService?.setOnPlayNextCallback {
                Log.d("PlayerViewModel", "Play next callback triggered")
                playNext()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("PlayerViewModel", "Service disconnected")
            serviceBound = false
            musicPlaybackService = null
        }
    }
    
    val musicPlayer: MusicPlayer
        get() = musicPlaybackService?.getMusicPlayer() ?: throw IllegalStateException("Service not bound")
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private val _playMode = MutableStateFlow(PlayMode.LOOP)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()
    
    val playbackState: StateFlow<PlaybackState>
        get() = musicPlaybackService?.getMusicPlayer()?.playbackState 
            ?: MutableStateFlow(PlaybackState.Idle).asStateFlow()
    
    private var progressJob: Job? = null
    private var favoriteObserverJob: Job? = null
    
    init {
        val database = AppDatabase.getDatabase(application)
        songRepository = SongRepository(database.songDao(), database.playlistDao())
        playlistRepository = PlaylistRepository(database.playlistDao())
        
        // Start and bind to the music service
        val intent = Intent(application, MusicPlaybackService::class.java)
        application.startService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        // Monitor playback state changes
        viewModelScope.launch {
            while (isActive) {
                delay(100)
                if (serviceBound) {
                    musicPlaybackService?.getMusicPlayer()?.playbackState?.collect { state ->
                        when (state) {
                            is PlaybackState.Playing -> startProgressTracking()
                            is PlaybackState.Paused -> stopProgressTracking()
                            is PlaybackState.Completed -> {
                                stopProgressTracking()
                            }
                            else -> stopProgressTracking()
                        }
                    }
                    break
                }
            }
        }
    }
    
    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        _playlist.value = songs
        _currentIndex.value = startIndex
        if (songs.isNotEmpty() && startIndex < songs.size) {
            playSongAt(startIndex)
        }
    }
    
    fun playSongAt(index: Int) {
        val songs = _playlist.value
        if (index in songs.indices) {
            _currentIndex.value = index
            val song = songs[index]
            _currentSong.value = song
            
            if (serviceBound) {
                musicPlayer.playSong(song.id, song.link)
                
                // Start foreground service with notification
                musicPlaybackService?.startForegroundService(song.name, song.author)
            }
            
            // Reset duration, it will be updated by progress tracking
            _duration.value = 0
            _currentPosition.value = 0
            _progress.value = 0f
            
            // Observe favorite status for this song
            observeCurrentSongFavorite(song.id)
            
            // Update last played timestamp and add to Recent Played playlist
            viewModelScope.launch {
                songRepository.updateLastPlayedAt(song.id)
                playlistRepository.addToRecentPlayed(song.id)
            }
            
            // Force an immediate duration update after a short delay
            viewModelScope.launch {
                delay(200) // Wait for MediaPlayer to be ready
                if (serviceBound) {
                    val dur = musicPlayer.getDuration()
                    if (dur > 0) {
                        _duration.value = dur
                    }
                }
            }
        }
    }
    
    private fun observeCurrentSongFavorite(songId: Long) {
        // Cancel previous observer to prevent memory leaks
        favoriteObserverJob?.cancel()
        favoriteObserverJob = viewModelScope.launch {
            songRepository.observeFavoriteStatus(songId)
                .collect { isFav ->
                    _isFavorite.value = isFav
                }
        }
    }
    
    fun toggleFavorite() {
        viewModelScope.launch {
            currentSong.value?.let { song ->
                val newStatus = songRepository.toggleFavorite(song.id)
                _isFavorite.value = newStatus
            }
        }
    }
    
    fun refreshPlayerState() {
        // Force update duration and position with retry logic
        viewModelScope.launch {
            var retries = 0
            while (retries < 10) { // Try up to 10 times
                val duration = musicPlayer.getDuration()
                val position = musicPlayer.getCurrentPosition()
                
                if (duration > 0) {
                    _duration.value = duration
                    _currentPosition.value = position
                    updateProgress()
                    break // Success, exit loop
                }
                
                delay(100) // Wait 100ms before retry
                retries++
            }
        }
    }
    
    fun playPrevious() {
        val currentIdx = _currentIndex.value
        val songs = _playlist.value
        
        if (songs.isEmpty()) return
        
        // In REPEAT_ONE mode, restart current song
        if (_playMode.value == PlayMode.REPEAT_ONE) {
            musicPlayer.seekTo(0)
            return
        }
        
        val newIndex = if (currentIdx > 0) {
            currentIdx - 1
        } else {
            songs.size - 1 // Loop to last song
        }
        
        playSongAt(newIndex)
    }
    
    fun playNext() {
        val currentIdx = _currentIndex.value
        val songs = _playlist.value
        
        if (songs.isEmpty()) return
        
        when (_playMode.value) {
            PlayMode.REPEAT_ONE -> {
                // Restart current song from beginning
                musicPlayer.seekTo(0)
            }
            PlayMode.SHUFFLE -> {
                // Random next song (avoid repeating current if possible)
                val availableIndices = songs.indices.filter { it != currentIdx }
                val newIndex = if (availableIndices.isNotEmpty()) {
                    availableIndices.random()
                } else {
                    currentIdx // Only one song, replay it
                }
                playSongAt(newIndex)
            }
            PlayMode.LOOP -> {
                // Sequential with loop
                val newIndex = if (currentIdx < songs.size - 1) {
                    currentIdx + 1
                } else {
                    0 // Loop to first song
                }
                playSongAt(newIndex)
            }
        }
    }
    
    fun togglePlayPause() {
        if (!serviceBound) return
        
        when (playbackState.value) {
            is PlaybackState.Playing -> musicPlayer.pause()
            is PlaybackState.Paused -> musicPlayer.resume()
            else -> {
                // If idle, play current song
                _currentSong.value?.let { song ->
                    musicPlayer.playSong(song.id, song.link)
                }
            }
        }
    }
    
    fun togglePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.LOOP
        }
    }
    
    fun seekTo(position: Int) {
        if (!serviceBound) return
        musicPlayer.seekTo(position)
        _currentPosition.value = position
        updateProgress()
    }
    
    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = viewModelScope.launch {
            while (isActive && serviceBound) {
                try {
                    val position = musicPlayer.getCurrentPosition()
                    val duration = musicPlayer.getDuration()
                    
                    // Update duration if we got a valid value
                    if (duration > 0 && _duration.value != duration) {
                        _duration.value = duration
                    }
                    
                    _currentPosition.value = position
                    updateProgress()
                    
                    delay(100) // Update every 100ms
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Error tracking progress", e)
                    break
                }
            }
        }
    }
    
    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
    
    private fun updateProgress() {
        val duration = _duration.value
        val position = _currentPosition.value
        _progress.value = if (duration > 0) {
            position.toFloat() / duration.toFloat()
        } else {
            0f
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopProgressTracking()
        favoriteObserverJob?.cancel()
        
        // Unbind from service
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
