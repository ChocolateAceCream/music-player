package com.example.demo.data

import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {
    
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    
    val recentlyPlayedSongs: Flow<List<Song>> = songDao.getRecentlyPlayedSongs()
    
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
}
