package com.example.demo.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSongId: Long? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playbackDelayed = false
    private var resumeOnFocusGain = false
    private var hasAudioFocus = false
    private var onCompletionCallback: (() -> Unit)? = null

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    fun setOnCompletionCallback(callback: () -> Unit) {
        onCompletionCallback = callback
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                // Resume playback if we were paused due to focus loss
                synchronized(this) {
                    if (playbackDelayed || resumeOnFocusGain) {
                        playbackDelayed = false
                        resumeOnFocusGain = false
                        resume()
                    }
                }
                try {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                } catch (e: IllegalStateException) {
                    Log.e("MusicPlayer", "Error setting volume: ${e.message}")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                // Lost focus permanently (e.g., another app starts playing music)
                // Stop playback completely and don't resume
                synchronized(this) {
                    resumeOnFocusGain = false
                    playbackDelayed = false
                }
                pause()
                // Optionally, you could call release() here to fully stop and clean up
                // but pause() allows user to manually resume if they want
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                // Lost focus for a short time: pause playback and resume when focus is regained
                synchronized(this) {
                    resumeOnFocusGain = try {
                        mediaPlayer?.isPlaying == true
                    } catch (e: IllegalStateException) {
                        false
                    }
                    playbackDelayed = false
                }
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lost focus for a short time, but it's ok to keep playing at a lower volume
                try {
                    mediaPlayer?.setVolume(0.2f, 0.2f)
                } catch (e: IllegalStateException) {
                    Log.e("MusicPlayer", "Error setting volume: ${e.message}")
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false) // Be more aggressive - don't wait
                .setWillPauseWhenDucked(true) // Force other apps to pause, not just duck
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasAudioFocus = true
                true
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                // Accept delayed focus for background playback
                Log.i("MusicPlayer", "Audio focus delayed, will play when granted")
                playbackDelayed = true
                false
            }
            else -> {
                hasAudioFocus = false
                false
            }
        }
    }

    private fun abandonAudioFocus() {
        hasAudioFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun playSong(songId: Long, uri: String) {
        try {
            // Request audio focus before playing, but allow playback if we already have it
            val audioFocusGranted = requestAudioFocus()
            if (!audioFocusGranted && !hasAudioFocus && !playbackDelayed) {
                Log.w("MusicPlayer", "Failed to gain audio focus and don't currently have it")
                return
            }

            // If same song is playing, just resume
            if (currentSongId == songId && mediaPlayer != null) {
                if (!mediaPlayer!!.isPlaying) {
                    mediaPlayer?.start()
                    _playbackState.value = PlaybackState.Playing(songId)
                }
                return
            }

            // Release previous player
            releaseMediaPlayer()

            currentSongId = songId
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setDataSource(context, Uri.parse(uri))
                prepare()

                // Set duration after prepare
                _duration.value = duration

                start()

                setOnCompletionListener {
                    // Check if this is still the current song to avoid race conditions
                    if (currentSongId == songId) {
                        _playbackState.value = PlaybackState.Completed(songId)
                        // Trigger callback immediately
                        onCompletionCallback?.invoke()
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e("MusicPlayer", "Error: what=$what, extra=$extra")
                    _playbackState.value = PlaybackState.Error("Playback error occurred")
                    true
                }
            }

            _playbackState.value = PlaybackState.Playing(songId)

        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error playing song", e)
            _playbackState.value = PlaybackState.Error(e.message ?: "Failed to play song")
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    currentSongId?.let { id ->
                        _playbackState.value = PlaybackState.Paused(id)
                    }
                }
            }
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayer", "Error pausing: ${e.message}")
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    currentSongId?.let { id ->
                        _playbackState.value = PlaybackState.Playing(id)
                    }
                }
            }
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayer", "Error resuming: ${e.message}")
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            _currentPosition.value = position
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayer", "Error seeking: ${e.message}")
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayer", "Error getting position: ${e.message}")
            0
        }
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayer", "Error getting duration: ${e.message}")
            0
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: IllegalStateException) {
            Log.e("MusicPlayer", "Error checking isPlaying: ${e.message}")
            false
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        releaseMediaPlayer()
        abandonAudioFocus()
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
