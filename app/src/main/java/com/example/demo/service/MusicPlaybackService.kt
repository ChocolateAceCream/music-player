package com.example.demo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.example.demo.MainActivity
import com.example.demo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URL

class MusicPlaybackService : Service() {

    private val binder = MusicBinder()
    private lateinit var musicPlayer: MusicPlayer
    private var onPlayNextCallback: (() -> Unit)? = null
    private var onPlayPreviousCallback: (() -> Unit)? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "MusicPlaybackService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_playback_channel"
        
        const val ACTION_PLAY = "com.example.demo.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.demo.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.demo.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.demo.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.demo.ACTION_STOP"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        musicPlayer = MusicPlayer(applicationContext)
        
        // Set completion callback to trigger next song
        musicPlayer.setOnCompletionCallback {
            Log.d(TAG, "Song completed, triggering next song")
            onPlayNextCallback?.invoke()
        }
        
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d(TAG, "MediaSession: onPlay")
                    musicPlayer.resume()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }

                override fun onPause() {
                    Log.d(TAG, "MediaSession: onPause")
                    musicPlayer.pause()
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onSkipToNext() {
                    Log.d(TAG, "MediaSession: onSkipToNext")
                    onPlayNextCallback?.invoke()
                }

                override fun onSkipToPrevious() {
                    Log.d(TAG, "MediaSession: onSkipToPrevious")
                    onPlayPreviousCallback?.invoke()
                }

                override fun onStop() {
                    Log.d(TAG, "MediaSession: onStop")
                    stopForeground(true)
                    stopSelf()
                }

                override fun onSeekTo(pos: Long) {
                    musicPlayer.seekTo(pos.toInt())
                    updatePlaybackState(
                        if (musicPlayer.isPlaying()) PlaybackStateCompat.STATE_PLAYING 
                        else PlaybackStateCompat.STATE_PAUSED
                    )
                }
            })
            
            isActive = true
        }
    }

    private fun updatePlaybackState(state: Int) {
        val position = musicPlayer.getCurrentPosition().toLong()
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, 1.0f)
            .build()
        
        mediaSession.setPlaybackState(playbackState)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        
        when (intent?.action) {
            ACTION_PLAY -> {
                musicPlayer.resume()
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
            ACTION_PAUSE -> {
                musicPlayer.pause()
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }
            ACTION_NEXT -> {
                onPlayNextCallback?.invoke()
            }
            ACTION_PREVIOUS -> {
                onPlayPreviousCallback?.invoke()
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
            }
        }
        
        return START_STICKY
    }

    fun getMusicPlayer(): MusicPlayer {
        return musicPlayer
    }

    fun setOnPlayNextCallback(callback: () -> Unit) {
        onPlayNextCallback = callback
    }

    fun setOnPlayPreviousCallback(callback: () -> Unit) {
        onPlayPreviousCallback = callback
    }

    fun startForegroundService(songTitle: String, artist: String, albumArtUrl: String?) {
        Log.d(TAG, "startForegroundService: title=$songTitle, artist=$artist, albumArtUrl=$albumArtUrl")
        
        // Start with notification without album art first
        val notification = createNotification(songTitle, artist, null, true)
        startForeground(NOTIFICATION_ID, notification)
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        
        // Load album art asynchronously and update notification
        serviceScope.launch {
            val albumArt = loadAlbumArt(albumArtUrl)
            Log.d(TAG, "Album art loaded: ${albumArt != null}, size: ${albumArt?.width}x${albumArt?.height}")
            
            if (albumArt != null) {
                val updatedNotification = createNotification(songTitle, artist, albumArt, true)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            }
            
            // Update media session metadata
            updateMediaMetadata(songTitle, artist, albumArt)
            
            Log.d(TAG, "Notification updated with album art")
        }
    }

    fun updateNotification(songTitle: String, artist: String, albumArtUrl: String?, isPlaying: Boolean) {
        Log.d(TAG, "updateNotification: isPlaying=$isPlaying")
        serviceScope.launch {
            val albumArt = loadAlbumArt(albumArtUrl)
            val notification = createNotification(songTitle, artist, albumArt, isPlaying)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            
            // Update media session
            updateMediaMetadata(songTitle, artist, albumArt)
            updatePlaybackState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING 
                else PlaybackStateCompat.STATE_PAUSED
            )
        }
    }

    private fun updateMediaMetadata(title: String, artist: String, albumArt: Bitmap?) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, musicPlayer.getDuration().toLong())
            .apply {
                if (albumArt != null) {
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                }
            }
            .build()
        
        mediaSession.setMetadata(metadata)
    }

    private suspend fun loadAlbumArt(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) {
            Log.d(TAG, "Album art URL is null or empty")
            return null
        }
        
        Log.d(TAG, "Loading album art from: $url")
        
        return try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val bitmap = when {
                    // Content URI (MediaStore album art)
                    url.startsWith("content://") -> {
                        Log.d(TAG, "Loading from content URI")
                        val uri = android.net.Uri.parse(url)
                        contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                    // File URI
                    url.startsWith("file://") -> {
                        Log.d(TAG, "Loading from file URI")
                        val uri = android.net.Uri.parse(url)
                        contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                    // Absolute file path
                    url.startsWith("/") -> {
                        Log.d(TAG, "Loading from file path")
                        BitmapFactory.decodeFile(url)
                    }
                    // HTTP/HTTPS URL
                    url.startsWith("http://") || url.startsWith("https://") -> {
                        Log.d(TAG, "Loading from HTTP URL")
                        val connection = URL(url).openConnection()
                        connection.doInput = true
                        connection.connect()
                        val input = connection.getInputStream()
                        BitmapFactory.decodeStream(input)
                    }
                    else -> {
                        Log.d(TAG, "Unknown format, trying as file path")
                        BitmapFactory.decodeFile(url)
                    }
                }
                
                if (bitmap != null) {
                    Log.d(TAG, "Successfully loaded album art: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.w(TAG, "Failed to load album art, bitmap is null")
                }
                
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading album art from: $url", e)
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing music"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        songTitle: String, 
        artist: String, 
        albumArt: Bitmap?,
        isPlaying: Boolean
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Previous action
        val previousIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this,
            0,
            previousIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause action
        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            0,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Next action
        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            0,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(songTitle)
            .setContentText(artist)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(albumArt)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                previousPendingIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        mediaSession.isActive = false
        mediaSession.release()
        musicPlayer.release()
        serviceScope.cancel()
    }
}
