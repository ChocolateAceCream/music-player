package com.example.demo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.AppDatabase
import com.example.demo.data.Song
import com.example.demo.data.SongRepository
import com.example.demo.service.MusicPlayer
import com.example.demo.service.PlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val songRepository: SongRepository
    val musicPlayer: MusicPlayer = MusicPlayer(application)
    
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    
    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    val playbackState: StateFlow<PlaybackState> = musicPlayer.playbackState
    
    private var progressJob: Job? = null
    
    init {
        val database = AppDatabase.getDatabase(application)
        songRepository = SongRepository(database.songDao())
        
        // Monitor playback state changes
        viewModelScope.launch {
            playbackState.collect { state ->
                when (state) {
                    is PlaybackState.Playing -> startProgressTracking()
                    is PlaybackState.Paused -> stopProgressTracking()
                    is PlaybackState.Completed -> playNext()
                    else -> stopProgressTracking()
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
            musicPlayer.playSong(song.id, song.link)
            
            // Reset duration, it will be updated by progress tracking
            _duration.value = 0
            _currentPosition.value = 0
            _progress.value = 0f
            
            // Update last played timestamp
            viewModelScope.launch {
                songRepository.updateLastPlayedAt(song.id)
            }
            
            // Force an immediate duration update after a short delay
            viewModelScope.launch {
                delay(200) // Wait for MediaPlayer to be ready
                val dur = musicPlayer.getDuration()
                if (dur > 0) {
                    _duration.value = dur
                }
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
        
        val newIndex = if (_isShuffleEnabled.value) {
            // Random next song
            (songs.indices).random()
        } else {
            // Sequential next song
            if (currentIdx < songs.size - 1) {
                currentIdx + 1
            } else {
                0 // Loop to first song
            }
        }
        
        playSongAt(newIndex)
    }
    
    fun togglePlayPause() {
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
    
    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }
    
    fun seekTo(position: Int) {
        musicPlayer.seekTo(position)
        _currentPosition.value = position
        updateProgress()
    }
    
    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val position = musicPlayer.getCurrentPosition()
                val duration = musicPlayer.getDuration()
                
                // Update duration if we got a valid value
                if (duration > 0 && _duration.value != duration) {
                    _duration.value = duration
                }
                
                _currentPosition.value = position
                updateProgress()
                
                delay(100) // Update every 100ms
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
        musicPlayer.release()
    }
}
