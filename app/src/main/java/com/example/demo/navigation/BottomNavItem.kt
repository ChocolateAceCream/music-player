package com.example.demo.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )

    object Library : BottomNavItem(
        route = "library",
        title = "Library",
        icon = Icons.AutoMirrored.Filled.LibraryBooks
    )

    object Find : BottomNavItem(
        route = "find",
        title = "Find",
        icon = Icons.Default.Search
    )
}