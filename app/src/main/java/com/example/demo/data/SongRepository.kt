package com.example.demo.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SongRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()

    val recentlyPlayedSongs: Flow<List<Song>> = songDao.getRecentlyPlayedSongs()

    val recentlyDownloadedSongs: Flow<List<Song>> = songDao.getRecentlyDownloadedSongs()

    suspend fun getSongById(songId: Long): Song? {
        return songDao.getSongById(songId)
    }

    fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query)
    }

    suspend fun insertSong(song: Song): Long {
        return songDao.insertSong(song)
    }

    suspend fun insertSongs(songs: List<Song>) {
        songDao.insertSongs(songs)
    }

    suspend fun updateSong(song: Song) {
        songDao.updateSong(song)
    }

    suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song)
    }

    suspend fun deleteAllSongs() {
        songDao.deleteAllSongs()
    }

    suspend fun updateLastPlayedAt(songId: Long, timestamp: Long = System.currentTimeMillis()) {
        songDao.updateLastPlayedAt(songId, timestamp)
    }

    suspend fun toggleFavorite(songId: Long): Boolean {
        val song = songDao.getSongById(songId)
        if (song != null) {
            val newFavoriteStatus = !song.isFavorite
            songDao.updateFavoriteStatus(songId, newFavoriteStatus)

            // Sync with Favorite playlist
            val favoritePlaylistId = playlistDao.getFavoritePlaylistId()
            if (favoritePlaylistId != null) {
                if (newFavoriteStatus) {
                    // Add to Favorite playlist
                    val crossRef = PlaylistSongCrossRef(
                        playlistId = favoritePlaylistId,
                        songId = songId
                    )
                    playlistDao.insertPlaylistSongCrossRef(crossRef)
                } else {
                    // Remove from Favorite playlist
                    playlistDao.deletePlaylistSongCrossRef(favoritePlaylistId, songId)
                }
            }

            return newFavoriteStatus
        }
        return false
    }

    suspend fun setFavorite(songId: Long, isFavorite: Boolean) {
        songDao.updateFavoriteStatus(songId, isFavorite)
    }

    fun observeFavoriteStatus(songId: Long): Flow<Boolean> {
        return songDao.observeFavoriteStatus(songId).map { it ?: false }
    }
}
