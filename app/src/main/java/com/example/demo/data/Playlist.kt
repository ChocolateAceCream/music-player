package com.example.demo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false
)

object SystemPlaylists {
    const val ALL_SONGS = "All Songs"
    const val RECENT_PLAYED = "Recent Played"
    const val FAVORITE = "Favorite"
}
