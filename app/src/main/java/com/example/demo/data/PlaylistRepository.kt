package com.example.demo.data

import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val playlistDao: PlaylistDao) {
    
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    
    val allPlaylistsWithSongs: Flow<List<PlaylistWithSongs>> = playlistDao.getAllPlaylistsWithSongs()
    
    suspend fun getPlaylistById(playlistId: Long): Playlist? {
        return playlistDao.getPlaylistById(playlistId)
    }
    
    suspend fun getPlaylistByName(name: String): Playlist? {
        return playlistDao.getPlaylistByName(name)
    }
    
    suspend fun getPlaylistWithSongs(playlistId: Long): PlaylistWithSongs? {
        return playlistDao.getPlaylistWithSongs(playlistId)
    }
    
    suspend fun createPlaylist(name: String, isSystem: Boolean = false): Long {
        val playlist = Playlist(name = name, isSystem = isSystem)
        return playlistDao.insertPlaylist(playlist)
    }
    
    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.updatePlaylist(playlist)
    }
    
    suspend fun deletePlaylist(playlist: Playlist) {
        // Don't delete system playlists
        if (!playlist.isSystem) {
            playlistDao.deletePlaylist(playlist)
        }
    }
    
    suspend fun deleteAllUserPlaylists() {
        playlistDao.deleteAllUserPlaylists()
    }
    
    suspend fun initializeDefaultPlaylists() {
        // Check and create "All Songs" playlist
        if (playlistDao.playlistExists(SystemPlaylists.ALL_SONGS) == 0) {
            createPlaylist(SystemPlaylists.ALL_SONGS, isSystem = true)
        }
        
        // Check and create "Recent Played" playlist
        if (playlistDao.playlistExists(SystemPlaylists.RECENT_PLAYED) == 0) {
            createPlaylist(SystemPlaylists.RECENT_PLAYED, isSystem = true)
        }
        
        // Check and create "Favorite" playlist
        if (playlistDao.playlistExists(SystemPlaylists.FAVORITE) == 0) {
            createPlaylist(SystemPlaylists.FAVORITE, isSystem = true)
        }
        
        // Check and create "Recent Download" playlist
        if (playlistDao.playlistExists(SystemPlaylists.RECENT_DOWNLOAD) == 0) {
            createPlaylist(SystemPlaylists.RECENT_DOWNLOAD, isSystem = true)
        }
    }
    
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, position: Int = 0) {
        val crossRef = PlaylistSongCrossRef(
            playlistId = playlistId,
            songId = songId,
            position = position
        )
        playlistDao.addSongToPlaylist(crossRef)
    }
    
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        val crossRef = PlaylistSongCrossRef(
            playlistId = playlistId,
            songId = songId
        )
        playlistDao.removeSongFromPlaylist(crossRef)
    }
    
    suspend fun removeAllSongsFromPlaylist(playlistId: Long) {
        playlistDao.removeAllSongsFromPlaylist(playlistId)
    }
    
    suspend fun getSongCountInPlaylist(playlistId: Long): Int {
        return playlistDao.getSongCountInPlaylist(playlistId)
    }
    
    fun getSongCountInPlaylistFlow(playlistId: Long): Flow<Int> {
        return playlistDao.getSongCountInPlaylistFlow(playlistId)
    }
}
