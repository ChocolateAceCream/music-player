package com.example.demo.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongId: Long? = null
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    
    fun playSong(songId: Long, uri: String) {
        try {
            // If same song is playing, just resume
            if (currentSongId == songId && mediaPlayer != null) {
                if (!mediaPlayer!!.isPlaying) {
                    mediaPlayer?.start()
                    _playbackState.value = PlaybackState.Playing(songId)
                }
                return
            }
            
            // Release previous player
            release()
            
            currentSongId = songId
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.parse(uri))
                prepare()
                start()
                
                setOnCompletionListener {
                    _playbackState.value = PlaybackState.Completed(songId)
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e("MusicPlayer", "Error: what=$what, extra=$extra")
                    _playbackState.value = PlaybackState.Error("Playback error occurred")
                    true
                }
            }
            
            _duration.value = mediaPlayer?.duration ?: 0
            _playbackState.value = PlaybackState.Playing(songId)
            
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error playing song", e)
            _playbackState.value = PlaybackState.Error(e.message ?: "Failed to play song")
        }
    }
    
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                currentSongId?.let { id ->
                    _playbackState.value = PlaybackState.Paused(id)
                }
            }
        }
    }
    
    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                currentSongId?.let { id ->
                    _playbackState.value = PlaybackState.Playing(id)
                }
            }
        }
    }
    
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }
    
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentSongId = null
        _playbackState.value = PlaybackState.Idle
        _currentPosition.value = 0
        _duration.value = 0
    }
}

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Playing(val songId: Long) : PlaybackState()
    data class Paused(val songId: Long) : PlaybackState()
    data class Completed(val songId: Long) : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}
