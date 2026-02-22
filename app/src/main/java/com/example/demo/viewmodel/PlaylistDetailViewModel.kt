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

    val playbackState: StateFlow<PlaybackState> = musicPlayer.playbackState

    init {
        val database = AppDatabase.getDatabase(application)
        playlistRepository = PlaylistRepository(database.playlistDao())
        songRepository = SongRepository(database.songDao())
    }

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val playlist = playlistRepository.getPlaylistWithSongs(playlistId)
            val adjustedPlaylist = playlist?.let {
                if (it.playlist.name == SystemPlaylists.RECENT_DOWNLOAD) {
                    it.copy(songs = it.songs.sortedByDescending { song -> song.downloadedAt })
                } else {
                    it
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

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }
}
