package com.example.demo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletedSongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeletedSong(deletedSong: DeletedSong)

    @Query("SELECT link FROM deleted_songs")
    suspend fun getAllDeletedLinks(): List<String>

    @Query("SELECT COUNT(*) FROM deleted_songs WHERE link = :link")
    suspend fun countByLink(link: String): Int

    @Query("DELETE FROM deleted_songs WHERE link = :link")
    suspend fun removeDeletedLink(link: String)
}
