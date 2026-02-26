package com.example.demo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity

class FloatingPlayerManager(private val context: Context) {

    private var playerControlReceiver: BroadcastReceiver? = null
    private var onPreviousClick: (() -> Unit)? = null
    private var onPlayPauseClick: (() -> Unit)? = null
    private var onNextClick: (() -> Unit)? = null

    companion object {
        private const val TAG = "FloatingPlayerManager"
    }

    fun checkAndRequestPermission(activity: ComponentActivity, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Log.d(TAG, "Requesting overlay permission")
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                activity.startActivity(intent)
            } else {
                Log.d(TAG, "Overlay permission already granted")
                onGranted()
            }
        } else {
            Log.d(TAG, "Overlay permission not required for this Android version")
            onGranted()
        }
    }

    fun hasOverlayPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        Log.d(TAG, "hasOverlayPermission: $hasPermission")
        return hasPermission
    }

    fun showFloatingPlayer() {
        Log.d(TAG, "showFloatingPlayer called")
        if (hasOverlayPermission()) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = FloatingPlayerService.ACTION_SHOW
            }
            context.startService(intent)
            Log.d(TAG, "Service start command sent")
        } else {
            Log.w(TAG, "Cannot show floating player - no overlay permission")
        }
    }

    fun hideFloatingPlayer() {
        Log.d(TAG, "hideFloatingPlayer called")
        val intent = Intent(context, FloatingPlayerService::class.java).apply {
            action = FloatingPlayerService.ACTION_HIDE
        }
        context.startService(intent)
    }

    fun registerPlayerControls(
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit
    ) {
        Log.d(TAG, "registerPlayerControls called")
        this.onPreviousClick = onPrevious
        this.onPlayPauseClick = onPlayPause
        this.onNextClick = onNext

        playerControlReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast received: ${intent?.action}")
                when (intent?.action) {
                    "com.example.demo.PLAYER_PREVIOUS" -> onPrevious()
                    "com.example.demo.PLAYER_PLAY_PAUSE" -> onPlayPause()
                    "com.example.demo.PLAYER_NEXT" -> onNext()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.demo.PLAYER_PREVIOUS")
            addAction("com.example.demo.PLAYER_PLAY_PAUSE")
            addAction("com.example.demo.PLAYER_NEXT")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(playerControlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(playerControlReceiver, filter)
        }
        Log.d(TAG, "Player controls registered")
    }

    fun unregisterPlayerControls() {
        Log.d(TAG, "unregisterPlayerControls called")
        playerControlReceiver?.let {
            try {
                context.unregisterReceiver(it)
                playerControlReceiver = null
                Log.d(TAG, "Player controls unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
    }
}
