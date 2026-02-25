package com.example.demo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.AppDatabase
import com.example.demo.data.Playlist
import com.example.demo.data.PlaylistRepository
import com.example.demo.data.PlaylistWithSongs
import com.example.demo.data.SongRepository
import com.example.demo.service.MusicScanner
import com.example.demo.service.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val songRepository: SongRepository
    private val playlistRepository: PlaylistRepository
    private val musicScanner: MusicScanner
    
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    val playlists: StateFlow<List<PlaylistWithSongs>>
    
    init {
        val database = AppDatabase.getDatabase(application)
        songRepository = SongRepository(database.songDao(), database.playlistDao())
        playlistRepository = PlaylistRepository(database.playlistDao())
        musicScanner = MusicScanner(application, songRepository, playlistRepository)
        
        // Initialize default playlists
        viewModelScope.launch {
            playlistRepository.initializeDefaultPlaylists()
        }
        
        // Collect playlists with songs
        val playlistsFlow = MutableStateFlow<List<PlaylistWithSongs>>(emptyList())
        viewModelScope.launch {
            playlistRepository.allPlaylistsWithSongs.collect { playlistList ->
                playlistsFlow.value = playlistList
            }
        }
        playlists = playlistsFlow.asStateFlow()
    }
    
    fun startScan() {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            
            try {
                val result = musicScanner.scanMusicFiles()
                
                _scanState.value = if (result.success) {
                    ScanState.Success(result.songsFound, result.errorCount)
                } else {
                    ScanState.Error(result.errorMessage ?: "Unknown error occurred")
                }
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.message ?: "Failed to scan music files")
            }
        }
    }
    
    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }
    
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }
    
    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }
    
    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, songId)
        }
    }
    
    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }
}

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(val songsFound: Int, val errorCount: Int) : ScanState()
    data class Error(val message: String) : ScanState()
}
