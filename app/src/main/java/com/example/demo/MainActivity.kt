package com.example.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.demo.components.MiniPlayer
import com.example.demo.navigation.BottomNavItem
import com.example.demo.navigation.BottomNavigationBar
import com.example.demo.screens.FindScreen
import com.example.demo.screens.HomeScreen
import com.example.demo.screens.LibraryScreen
import com.example.demo.screens.PlaylistDetailScreen
import com.example.demo.service.FloatingPlayerManager
import com.example.demo.ui.theme.DemoTheme
import com.example.demo.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private lateinit var floatingPlayerManager: FloatingPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        floatingPlayerManager = FloatingPlayerManager(this)

        // Request overlay permission on first launch
        floatingPlayerManager.checkAndRequestPermission(this) {
            // Permission granted
        }

        setContent {
            DemoTheme {
                MainScreen(floatingPlayerManager)
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        floatingPlayerManager.unregisterPlayerControls()
        floatingPlayerManager.hideFloatingPlayer()
    }
}

@Composable
fun MainScreen(floatingPlayerManager: FloatingPlayerManager) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Register player controls for floating window
    DisposableEffect(Unit) {
        floatingPlayerManager.registerPlayerControls(
            onPrevious = { playerViewModel.playPrevious() },
            onPlayPause = { playerViewModel.togglePlayPause() },
            onNext = { playerViewModel.playNext() }
        )
        onDispose {
            floatingPlayerManager.unregisterPlayerControls()
        }
    }

    // Show/hide floating player based on app lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // App goes to background, show floating player if song is playing
                    if (currentSong != null && floatingPlayerManager.hasOverlayPermission()) {
                        floatingPlayerManager.showFloatingPlayer()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // App comes to foreground, hide floating player
                    floatingPlayerManager.hideFloatingPlayer()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val isOnPlayerScreen = currentRoute == "player"
            val isOnPlaylistDetail = currentRoute?.startsWith("playlist_detail") == true

            androidx.compose.foundation.layout.Column {
                // Show mini player if there's a song playing and not on player screen
                if (currentSong != null && !isOnPlayerScreen) {
                    MiniPlayer(
                        playerViewModel = playerViewModel,
                        onExpand = {
                            navController.navigate("player")
                        }
                    )
                }

                // Show bottom navigation bar except on playlist detail and player screens
                if (!isOnPlaylistDetail && !isOnPlayerScreen) {
                    BottomNavigationBar(navController = navController)
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Home.route
            ) {
                composable(BottomNavItem.Home.route) {
                    HomeScreen()
                }
                composable(BottomNavItem.Library.route) {
                    LibraryScreen(
                        onPlaylistClick = { playlistId ->
                            navController.navigate("playlist_detail/$playlistId")
                        }
                    )
                }
                composable(BottomNavItem.Find.route) {
                    FindScreen(playerViewModel = playerViewModel)
                }
                composable(
                    route = "playlist_detail/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        onNavigateBack = { navController.navigateUp() },
                        playerViewModel = playerViewModel
                    )
                }
                composable("player") {
                    com.example.demo.screens.PlayerScreen(
                        playerViewModel = playerViewModel,
                        onNavigateBack = { navController.navigateUp() },
                        onShowPlaylist = {
                            // TODO: Show current playlist dialog
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    DemoTheme {
        // MainScreen() - Preview disabled due to FloatingPlayerManager dependency
    }
}