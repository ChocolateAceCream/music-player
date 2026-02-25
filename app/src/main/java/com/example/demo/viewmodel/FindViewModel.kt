package com.example.demo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.AppDatabase
import com.example.demo.data.PlaylistRepository
import com.example.demo.data.PlaylistWithSongs
import com.example.demo.data.Song
import com.example.demo.data.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FindViewModel(application: Application) : AndroidViewModel(application) {

    private val songRepository: SongRepository
    private val playlistRepository: PlaylistRepository

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _filteredSongs = MutableStateFlow<List<Song>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs.asStateFlow()
    
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()
    
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds.asStateFlow()
    
    private val _showAddToPlaylistDialog = MutableStateFlow(false)
    val showAddToPlaylistDialog: StateFlow<Boolean> = _showAddToPlaylistDialog.asStateFlow()
    
    private val _allPlaylists = MutableStateFlow<List<PlaylistWithSongs>>(emptyList())
    val allPlaylists: StateFlow<List<PlaylistWithSongs>> = _allPlaylists.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        songRepository = SongRepository(database.songDao(), database.playlistDao())
        playlistRepository = PlaylistRepository(database.playlistDao())
        
        loadAllSongs()
    }

    private fun loadAllSongs() {
        viewModelScope.launch {
            songRepository.allSongs.collect { songs ->
                _allSongs.value = songs
                filterSongs(_searchQuery.value)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterSongs(query)
    }

    private fun filterSongs(query: String) {
        if (query.isBlank()) {
            _filteredSongs.value = _allSongs.value
        } else {
            val lowerQuery = query.lowercase()
            _filteredSongs.value = _allSongs.value.filter { song ->
                song.name.lowercase().contains(lowerQuery) ||
                song.author.lowercase().contains(lowerQuery) ||
                song.album.lowercase().contains(lowerQuery)
            }
        }
    }

    fun toggleSongFavorite(songId: Long) {
        viewModelScope.launch {
            songRepository.toggleFavorite(songId)
        }
    }

    // Multi-select methods
    fun enterMultiSelectMode(songId: Long) {
        _isMultiSelectMode.value = true
        _selectedSongIds.value = setOf(songId)
    }
    
    fun exitMultiSelectMode() {
        _isMultiSelectMode.value = false
        _selectedSongIds.value = emptySet()
        _showAddToPlaylistDialog.value = false
    }
    
    fun toggleSongSelection(songId: Long) {
        val currentSelection = _selectedSongIds.value
        _selectedSongIds.value = if (currentSelection.contains(songId)) {
            currentSelection - songId
        } else {
            currentSelection + songId
        }
    }
    
    fun showAddToPlaylistDialog() {
        viewModelScope.launch {
            playlistRepository.allPlaylistsWithSongs.collect { playlists ->
                _allPlaylists.value = playlists.filter { !it.playlist.isSystem }
                _showAddToPlaylistDialog.value = true
                return@collect
            }
        }
    }
    
    fun hideAddToPlaylistDialog() {
        _showAddToPlaylistDialog.value = false
    }
    
    fun addSelectedSongsToPlaylist(targetPlaylistId: Long) {
        viewModelScope.launch {
            val songIds = _selectedSongIds.value.toList()
            playlistRepository.addSongsToPlaylist(targetPlaylistId, songIds)
            hideAddToPlaylistDialog()
            exitMultiSelectMode()
        }
    }
    
    fun createPlaylistAndAddSongs(playlistName: String) {
        viewModelScope.launch {
            val newPlaylistId = playlistRepository.createPlaylist(playlistName)
            val songIds = _selectedSongIds.value.toList()
            playlistRepository.addSongsToPlaylist(newPlaylistId, songIds)
            hideAddToPlaylistDialog()
            exitMultiSelectMode()
        }
    }
}
