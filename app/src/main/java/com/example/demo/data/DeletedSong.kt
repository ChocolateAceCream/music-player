package com.example.demo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_songs")
data class DeletedSong(
    @PrimaryKey
    @ColumnInfo(name = "link")
    val link: String
)
