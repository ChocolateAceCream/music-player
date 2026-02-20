package com.example.demo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY is_system DESC, created_at DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?
    
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): Playlist?
    
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithSongs(playlistId: Long): PlaylistWithSongs?
    
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY is_system DESC, created_at DESC")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long
    
    @Update
    suspend fun updatePlaylist(playlist: Playlist)
    
    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
    
    @Query("DELETE FROM playlists WHERE is_system = 0")
    suspend fun deleteAllUserPlaylists()
    
    @Query("SELECT COUNT(*) FROM playlists WHERE name = :name")
    suspend fun playlistExists(name: String): Int
    
    // Playlist-Song relationship operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)
    
    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)
    
    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun removeAllSongsFromPlaylist(playlistId: Long)
    
    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun getSongCountInPlaylist(playlistId: Long): Int
    
    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    fun getSongCountInPlaylistFlow(playlistId: Long): Flow<Int>
}
