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
}
