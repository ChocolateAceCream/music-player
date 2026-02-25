package com.example.demo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.AppDatabase
import com.example.demo.data.PlaylistRepository
import com.example.demo.data.PlaylistWithSongs
import com.example.demo.data.Song
import com.example.demo.data.SystemPlaylists
import com.example.demo.data.SongRepository
import com.example.demo.service.MusicPlayer
import com.example.demo.service.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository: PlaylistRepository
    private val songRepository: SongRepository
    val musicPlayer: MusicPlayer = MusicPlayer(application)

    private val _playlistWithSongs = MutableStateFlow<PlaylistWithSongs?>(null)
    val playlistWithSongs: StateFlow<PlaylistWithSongs?> = _playlistWithSongs.asStateFlow()
    
    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog.asStateFlow()
    
    private val _renameError = MutableStateFlow<String?>(null)
    val renameError: StateFlow<String?> = _renameError.asStateFlow()
    
    private val _showAddSongsDialog = MutableStateFlow(false)
    val showAddSongsDialog: StateFlow<Boolean> = _showAddSongsDialog.asStateFlow()
    
    private val _availableSongs = MutableStateFlow<List<Song>>(emptyList())
    val availableSongs: StateFlow<List<Song>> = _availableSongs.asStateFlow()
    
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()
    
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds.asStateFlow()
    
    private val _showAddToPlaylistDialog = MutableStateFlow(false)
    val showAddToPlaylistDialog: StateFlow<Boolean> = _showAddToPlaylistDialog.asStateFlow()
    
    private val _allPlaylists = MutableStateFlow<List<PlaylistWithSongs>>(emptyList())
    val allPlaylists: StateFlow<List<PlaylistWithSongs>> = _allPlaylists.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = musicPlayer.playbackState

    init {
        val database = AppDatabase.getDatabase(application)
        playlistRepository = PlaylistRepository(database.playlistDao())
        songRepository = SongRepository(database.songDao(), database.playlistDao())
    }

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val playlist = playlistRepository.getPlaylistWithSongs(playlistId)
            val adjustedPlaylist = playlist?.let {
                when (it.playlist.name) {
                    SystemPlaylists.RECENT_DOWNLOAD -> {
                        // Sort by download date (newest first)
                        it.copy(songs = it.songs.sortedByDescending { song -> song.downloadedAt })
                    }
                    SystemPlaylists.RECENT_PLAYED -> {
                        // Sort by last played date (most recent first)
                        it.copy(songs = it.songs.sortedByDescending { song -> song.lastPlayedAt ?: 0 })
                    }
                    else -> it
                }
            }
            _playlistWithSongs.value = adjustedPlaylist
        }
    }

    fun playSong(song: Song) {
        musicPlayer.playSong(song.id, song.link)

        // Update last played timestamp
        viewModelScope.launch {
            songRepository.updateLastPlayedAt(song.id)
        }
    }

    fun pauseSong() {
        musicPlayer.pause()
    }

    fun resumeSong() {
        musicPlayer.resume()
    }
    
    fun toggleSongFavorite(songId: Long) {
        viewModelScope.launch {
            songRepository.toggleFavorite(songId)
            // Reload the playlist to reflect the updated favorite status
            _playlistWithSongs.value?.playlist?.id?.let { playlistId ->
                loadPlaylist(playlistId)
            }
        }
    }
    
    fun showRenameDialog() {
        val playlist = _playlistWithSongs.value?.playlist
        if (playlist?.isSystem == false) {
            _showRenameDialog.value = true
        }
    }
    
    fun hideRenameDialog() {
        _showRenameDialog.value = false
        _renameError.value = null
    }
    
    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            val playlist = _playlistWithSongs.value?.playlist
            if (playlist != null) {
                val result = playlistRepository.renamePlaylist(playlist.id, newName)
                result.fold(
                    onSuccess = {
                        hideRenameDialog()
                        // Reload the playlist to get updated name
                        loadPlaylist(playlist.id)
                    },
                    onFailure = { error ->
                        _renameError.value = error.message
                    }
                )
            }
        }
    }
    
    fun showAddSongsDialog() {
        val playlist = _playlistWithSongs.value?.playlist
        if (playlist?.isSystem == false) {
            viewModelScope.launch {
                // Load all available songs
                songRepository.allSongs.collect { songs ->
                    _availableSongs.value = songs
                    _showAddSongsDialog.value = true
                    return@collect // Only collect once
                }
            }
        }
    }
    
    fun hideAddSongsDialog() {
        _showAddSongsDialog.value = false
    }
    
    fun addSongsToPlaylist(songIds: List<Long>) {
        viewModelScope.launch {
            val playlist = _playlistWithSongs.value?.playlist
            if (playlist != null) {
                playlistRepository.addSongsToPlaylist(playlist.id, songIds)
                hideAddSongsDialog()
                // Reload the playlist to show newly added songs
                loadPlaylist(playlist.id)
            }
        }
    }
    
    fun removeSongFromPlaylist(songId: Long) {
        viewModelScope.launch {
            val playlist = _playlistWithSongs.value?.playlist
            if (playlist != null && !playlist.isSystem) {
                playlistRepository.removeSongFromPlaylist(playlist.id, songId)
                // Reload the playlist to update the list
                loadPlaylist(playlist.id)
            }
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
        // Also hide any open dialogs
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
            // Load all playlists
            playlistRepository.allPlaylistsWithSongs.collect { playlists ->
                // Filter out system playlists
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
    
    fun deleteSelectedSongs() {
        viewModelScope.launch {
            val playlist = _playlistWithSongs.value?.playlist
            if (playlist != null && !playlist.isSystem) {
                val songIds = _selectedSongIds.value.toList()
                songIds.forEach { songId ->
                    playlistRepository.removeSongFromPlaylist(playlist.id, songId)
                }
                // Make sure dialog is hidden
                hideAddToPlaylistDialog()
                exitMultiSelectMode()
                // Reload the playlist to update the list
                loadPlaylist(playlist.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }
}
