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

    suspend fun renamePlaylist(playlistId: Long, newName: String): Result<Unit> {
        return try {
            val trimmedName = newName.trim()

            // Validate name is not empty
            if (trimmedName.isEmpty()) {
                return Result.failure(Exception("Playlist name cannot be empty"))
            }

            // Check if playlist is a system playlist
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist?.isSystem == true) {
                return Result.failure(Exception("Cannot rename system playlists"))
            }

            // Check for duplicate names
            val duplicateCount = playlistDao.countPlaylistsWithName(trimmedName, playlistId)
            if (duplicateCount > 0) {
                return Result.failure(Exception("A playlist with this name already exists"))
            }

            // Update the name
            playlistDao.updatePlaylistName(playlistId, trimmedName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        songIds.forEach { songId ->
            val crossRef = PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId
            )
            playlistDao.insertPlaylistSongCrossRef(crossRef)
        }
    }

    suspend fun isPlaylistNameUnique(name: String, excludeId: Long? = null): Boolean {
        val count = playlistDao.countPlaylistsWithName(name, excludeId ?: -1)
        return count == 0
    }

    suspend fun getFavoritePlaylistId(): Long? {
        return playlistDao.getFavoritePlaylistId()
    }
    
    suspend fun addToRecentPlayed(songId: Long) {
        try {
            // Get Recent Played playlist
            val recentPlayedPlaylist = getPlaylistByName(SystemPlaylists.RECENT_PLAYED)
            if (recentPlayedPlaylist == null) {
                android.util.Log.e("PlaylistRepository", "Recent Played playlist not found!")
                return
            }
            
            android.util.Log.d("PlaylistRepository", "Adding song $songId to Recent Played playlist ${recentPlayedPlaylist.id}")
            
            // Remove the song if it already exists in the playlist
            removeSongFromPlaylist(recentPlayedPlaylist.id, songId)
            
            // Get current songs in the playlist
            val playlistWithSongs = getPlaylistWithSongs(recentPlayedPlaylist.id)
            val currentSongs = playlistWithSongs?.songs ?: emptyList()
            
            android.util.Log.d("PlaylistRepository", "Current songs in Recent Played: ${currentSongs.size}")
            
            // Add the song at position 0 (top of the list)
            addSongToPlaylist(recentPlayedPlaylist.id, songId, position = 0)
            
            android.util.Log.d("PlaylistRepository", "Song added to Recent Played")
            
            // If we now have more than 50 songs, remove the oldest ones
            if (currentSongs.size >= 50) {
                // Remove songs beyond the 50th position
                // Since we added one at the top, the 50th song (index 49) is now at position 50
                val songsToRemove = currentSongs.drop(49) // Keep first 49, remove the rest
                android.util.Log.d("PlaylistRepository", "Removing ${songsToRemove.size} old songs from Recent Played")
                songsToRemove.forEach { song ->
                    removeSongFromPlaylist(recentPlayedPlaylist.id, song.id)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepository", "Error adding to Recent Played: ${e.message}", e)
        }
    }
}
