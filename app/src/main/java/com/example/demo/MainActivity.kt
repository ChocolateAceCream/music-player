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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import com.example.demo.ui.theme.DemoTheme
import com.example.demo.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val currentSong by playerViewModel.currentSong.collectAsState()

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
        MainScreen()
    }
}