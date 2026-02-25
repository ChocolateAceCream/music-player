package com.example.demo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY name ASC")
    fun getAllSongs(): Flow<List<Song>>
    
    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: Long): Song?
    
    @Query("SELECT * FROM songs ORDER BY last_played_at DESC")
    fun getRecentlyPlayedSongs(): Flow<List<Song>>
    
    @Query("SELECT * FROM songs ORDER BY downloaded_at DESC LIMIT 50")
    fun getRecentlyDownloadedSongs(): Flow<List<Song>>
    
    @Query("SELECT * FROM songs WHERE name LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<Song>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)
    
    @Update
    suspend fun updateSong(song: Song)
    
    @Delete
    suspend fun deleteSong(song: Song)
    
    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
    
    @Query("UPDATE songs SET last_played_at = :timestamp WHERE id = :songId")
    suspend fun updateLastPlayedAt(songId: Long, timestamp: Long)
    
    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean)
    
    @Query("SELECT is_favorite FROM songs WHERE id = :songId")
    fun observeFavoriteStatus(songId: Long): Flow<Boolean?>
    
    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY name ASC")
    fun getFavoriteSongs(): Flow<List<Song>>
}
