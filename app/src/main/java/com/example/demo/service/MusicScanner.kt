package com.example.demo.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.demo.data.PlaylistRepository
import com.example.demo.data.Song
import com.example.demo.data.SongRepository
import com.example.demo.data.SystemPlaylists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicScanner(
    private val context: Context,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) {

    suspend fun scanMusicFiles(): ScanResult = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        var scannedCount = 0
        var errorCount = 0

        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DURATION
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album"
                        val filePath = cursor.getString(dataColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        val dateModified = cursor.getLong(dateModifiedColumn) * 1000 // Convert to milliseconds
                        val durationMs = cursor.getLong(durationColumn)

                        // Filter out very short audio files (e.g. ringtones) shorter than 1 minute
                        // Keep songs with unknown or zero duration to avoid dropping valid tracks
                        if (durationMs in 1 until 60_000L) {
                            continue
                        }

                        // Get content URI for the audio file
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        // Get album art URI
                        val albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        )

                        val song = Song(
                            name = title,
                            author = artist,
                            album = album,
                            coverPageLink = albumArtUri.toString(),
                            link = contentUri.toString(),
                            lastPlayedAt = null,
                            downloadedAt = dateModified
                        )

                        songs.add(song)
                        scannedCount++

                    } catch (e: Exception) {
                        Log.e("MusicScanner", "Error processing file", e)
                        errorCount++
                    }
                }
            }

            // Save all songs to database and get their IDs
            if (songs.isNotEmpty()) {
                // Get existing songs from Favorite and Recent Played playlists
                val favoritePlaylist = playlistRepository.getPlaylistByName(SystemPlaylists.FAVORITE)
                val recentPlayedPlaylist = playlistRepository.getPlaylistByName(SystemPlaylists.RECENT_PLAYED)
                
                val favoritePlaylistWithSongs = favoritePlaylist?.let { 
                    playlistRepository.getPlaylistWithSongs(it.id) 
                }
                val recentPlayedPlaylistWithSongs = recentPlayedPlaylist?.let { 
                    playlistRepository.getPlaylistWithSongs(it.id) 
                }
                
                // Create a map of existing songs by their file path (link)
                val scannedSongsByLink = songs.associateBy { it.link }
                
                // Clear existing songs first
                songRepository.deleteAllSongs()

                // Insert new songs and collect their IDs
                val songIdsByLink = mutableMapOf<String, Long>()
                for (song in songs) {
                    val songId = songRepository.insertSong(song)
                    songIdsByLink[song.link] = songId
                }

                // Get "All Songs" playlist
                val allSongsPlaylist = playlistRepository.getPlaylistByName(SystemPlaylists.ALL_SONGS)

                if (allSongsPlaylist != null) {
                    // Clear existing songs from "All Songs" playlist
                    playlistRepository.removeAllSongsFromPlaylist(allSongsPlaylist.id)

                    // Add all scanned songs to "All Songs" playlist
                    songIdsByLink.values.forEachIndexed { index, songId ->
                        playlistRepository.addSongToPlaylist(
                            playlistId = allSongsPlaylist.id,
                            songId = songId,
                            position = index
                        )
                    }
                }

                // Get "Recent Download" playlist
                val recentDownloadPlaylist = playlistRepository.getPlaylistByName(SystemPlaylists.RECENT_DOWNLOAD)

                if (recentDownloadPlaylist != null) {
                    // Clear existing songs from "Recent Download" playlist
                    playlistRepository.removeAllSongsFromPlaylist(recentDownloadPlaylist.id)

                    // Sort songs by downloadedAt timestamp (newest first) and take top 50
                    val sortedSongs = songs.sortedByDescending { it.downloadedAt }.take(50)

                    // Add to Recent Download playlist
                    sortedSongs.forEachIndexed { index, song ->
                        songIdsByLink[song.link]?.let { songId ->
                            playlistRepository.addSongToPlaylist(
                                playlistId = recentDownloadPlaylist.id,
                                songId = songId,
                                position = index
                            )
                        }
                    }
                }
                
                // Restore Favorite playlist - only keep songs that still exist
                if (favoritePlaylist != null && favoritePlaylistWithSongs != null) {
                    playlistRepository.removeAllSongsFromPlaylist(favoritePlaylist.id)
                    
                    favoritePlaylistWithSongs.songs.forEachIndexed { index, oldSong ->
                        // Check if this song still exists in the scanned files
                        val newSongId = songIdsByLink[oldSong.link]
                        if (newSongId != null) {
                            // Song still exists, add it back to Favorite playlist
                            playlistRepository.addSongToPlaylist(
                                playlistId = favoritePlaylist.id,
                                songId = newSongId,
                                position = index
                            )
                            // Also update the isFavorite flag
                            songRepository.setFavorite(newSongId, true)
                        }
                    }
                }
                
                // Restore Recent Played playlist - only keep songs that still exist
                if (recentPlayedPlaylist != null && recentPlayedPlaylistWithSongs != null) {
                    playlistRepository.removeAllSongsFromPlaylist(recentPlayedPlaylist.id)
                    
                    recentPlayedPlaylistWithSongs.songs.forEachIndexed { index, oldSong ->
                        // Check if this song still exists in the scanned files
                        val newSongId = songIdsByLink[oldSong.link]
                        if (newSongId != null) {
                            // Song still exists, add it back to Recent Played playlist
                            playlistRepository.addSongToPlaylist(
                                playlistId = recentPlayedPlaylist.id,
                                songId = newSongId,
                                position = index
                            )
                            // Restore the lastPlayedAt timestamp
                            if (oldSong.lastPlayedAt != null) {
                                songRepository.updateLastPlayedAt(newSongId, oldSong.lastPlayedAt)
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("MusicScanner", "Error scanning music files", e)
            return@withContext ScanResult(
                success = false,
                songsFound = 0,
                errorCount = errorCount,
                errorMessage = e.message
            )
        }

        ScanResult(
            success = true,
            songsFound = scannedCount,
            errorCount = errorCount,
            errorMessage = null
        )
    }
}

data class ScanResult(
    val success: Boolean,
    val songsFound: Int,
    val errorCount: Int,
    val errorMessage: String?
)
