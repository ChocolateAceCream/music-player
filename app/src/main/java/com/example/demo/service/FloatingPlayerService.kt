package com.example.demo.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.example.demo.R

class FloatingPlayerService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        private const val TAG = "FloatingPlayerService"
        const val ACTION_SHOW = "com.example.demo.SHOW_FLOATING_PLAYER"
        const val ACTION_HIDE = "com.example.demo.HIDE_FLOATING_PLAYER"
        const val ACTION_PREVIOUS = "com.example.demo.PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.example.demo.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.demo.NEXT"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_SHOW -> showFloatingWindow()
            ACTION_HIDE -> hideFloatingWindow()
            ACTION_PREVIOUS -> {
                sendBroadcast(Intent("com.example.demo.PLAYER_PREVIOUS"))
            }
            ACTION_PLAY_PAUSE -> {
                sendBroadcast(Intent("com.example.demo.PLAYER_PLAY_PAUSE"))
            }
            ACTION_NEXT -> {
                sendBroadcast(Intent("com.example.demo.PLAYER_NEXT"))
            }
        }
        return START_STICKY
    }

    private fun showFloatingWindow() {
        Log.d(TAG, "showFloatingWindow called, floatingView=$floatingView")
        if (floatingView != null) {
            Log.d(TAG, "Floating view already exists")
            return
        }

        try {
            floatingView = LayoutInflater.from(this).inflate(
                R.layout.floating_player_layout,
                null
            )
            Log.d(TAG, "Layout inflated successfully")

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0
            params.y = 100

            windowManager.addView(floatingView, params)
            Log.d(TAG, "Floating view added to window manager")

            setupTouchListener(params)
            setupButtons()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating window", e)
        }
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        val hotPinkColor = android.graphics.Color.parseColor("#FF69B4")
        
        floatingView?.findViewById<ImageButton>(R.id.btn_previous)?.apply {
            setColorFilter(hotPinkColor)
            setOnClickListener {
                Log.d(TAG, "Previous button clicked")
                sendBroadcast(Intent("com.example.demo.PLAYER_PREVIOUS"))
            }
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_play_pause)?.apply {
            setColorFilter(hotPinkColor)
            setOnClickListener {
                Log.d(TAG, "Play/Pause button clicked")
                sendBroadcast(Intent("com.example.demo.PLAYER_PLAY_PAUSE"))
            }
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_next)?.apply {
            setColorFilter(hotPinkColor)
            setOnClickListener {
                Log.d(TAG, "Next button clicked")
                sendBroadcast(Intent("com.example.demo.PLAYER_NEXT"))
            }
        }

        floatingView?.findViewById<ImageButton>(R.id.btn_close)?.setOnClickListener {
            Log.d(TAG, "Close button clicked")
            hideFloatingWindow()
        }
    }

    private fun hideFloatingWindow() {
        Log.d(TAG, "hideFloatingWindow called")
        floatingView?.let {
            try {
                windowManager.removeView(it)
                floatingView = null
                Log.d(TAG, "Floating view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingWindow()
        Log.d(TAG, "Service destroyed")
    }

    fun updateSongInfo(title: String, artist: String) {
        floatingView?.findViewById<TextView>(R.id.tv_song_title)?.text = title
        floatingView?.findViewById<TextView>(R.id.tv_song_artist)?.text = artist
    }

    fun updatePlayPauseButton(isPlaying: Boolean) {
        floatingView?.findViewById<ImageButton>(R.id.btn_play_pause)?.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }
}
