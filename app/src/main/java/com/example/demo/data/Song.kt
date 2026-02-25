package com.example.demo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "author")
    val author: String,
    
    @ColumnInfo(name = "album")
    val album: String,
    
    @ColumnInfo(name = "cover_page_link")
    val coverPageLink: String,
    
    @ColumnInfo(name = "link")
    val link: String,
    
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long? = null,
    
    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)
