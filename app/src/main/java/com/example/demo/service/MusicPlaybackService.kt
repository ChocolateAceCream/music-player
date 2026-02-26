package com.example.demo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.demo.MainActivity
import com.example.demo.R

class MusicPlaybackService : Service() {

    private val binder = MusicBinder()
    private lateinit var musicPlayer: MusicPlayer
    private var onPlayNextCallback: (() -> Unit)? = null

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
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_PLAY -> {
                // Handle play action
            }
            ACTION_PAUSE -> {
                musicPlayer.pause()
            }
            ACTION_NEXT -> {
                onPlayNextCallback?.invoke()
            }
            ACTION_PREVIOUS -> {
                // Handle previous
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

    fun startForegroundService(songTitle: String, artist: String) {
        val notification = createNotification(songTitle, artist)
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Started foreground service")
    }

    fun updateNotification(songTitle: String, artist: String) {
        val notification = createNotification(songTitle, artist)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
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
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(songTitle: String, artist: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(songTitle)
            .setContentText(artist)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        musicPlayer.release()
    }
}
