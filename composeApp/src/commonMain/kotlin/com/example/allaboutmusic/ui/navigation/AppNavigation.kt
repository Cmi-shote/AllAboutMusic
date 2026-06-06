package com.example.allaboutmusic.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.allaboutmusic.ui.components.MiniPlayer
import com.example.allaboutmusic.ui.downloads.DownloadsScreen
import com.example.allaboutmusic.ui.downloads.DownloadsViewModel
import com.example.allaboutmusic.ui.home.HomeScreen
import com.example.allaboutmusic.ui.home.HomeViewModel
import com.example.allaboutmusic.ui.library.LibraryScreen
import com.example.allaboutmusic.ui.library.LibraryViewModel
import com.example.allaboutmusic.ui.mix.MixDetailScreen
import com.example.allaboutmusic.ui.mix.MixDetailViewModel
import com.example.allaboutmusic.ui.mix.MixListScreen
import com.example.allaboutmusic.ui.mix.MixListViewModel
import com.example.allaboutmusic.ui.player.PlayerScreen
import com.example.allaboutmusic.ui.player.PlayerViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable object HomeRoute
@Serializable object LibraryRoute
@Serializable object DownloadsRoute
@Serializable object MixListRoute
@Serializable data class MixDetailRoute(val mixId: String)
@Serializable object PlayerRoute

data class BottomNavItem(
    val label: String,
    val route: Any,
    val icon: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home", HomeRoute, "H"),
    BottomNavItem("Library", LibraryRoute, "L"),
    BottomNavItem("Mixes", MixListRoute, "M"),
    BottomNavItem("Downloads", DownloadsRoute, "D")
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val downloadsViewModel: DownloadsViewModel = koinViewModel()
    val libraryViewModel: LibraryViewModel = koinViewModel()
    val mixListViewModel: MixListViewModel = koinViewModel()
    val mixDetailViewModel: MixDetailViewModel = koinViewModel()
    val playerState by playerViewModel.playerState.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val hideBottomBar = navBackStackEntry?.destination?.let { dest ->
        dest.hasRoute<PlayerRoute>() || dest.hasRoute<MixDetailRoute>()
    } ?: false

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                Column {
                    // Mini player
                    if (playerState.currentTrack != null) {
                        MiniPlayer(
                            state = playerState,
                            currentPosition = currentPosition,
                            onTogglePlayPause = { playerViewModel.togglePlayPause() },
                            onClick = { navController.navigate(PlayerRoute) }
                        )
                    }

                    // Bottom navigation
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            val selected = navBackStackEntry?.destination?.let { dest ->
                                when (item.route) {
                                    is HomeRoute -> dest.hasRoute<HomeRoute>()
                                    is LibraryRoute -> dest.hasRoute<LibraryRoute>()
                                    is DownloadsRoute -> dest.hasRoute<DownloadsRoute>()
                                    is MixListRoute -> dest.hasRoute<MixListRoute>()
                                    else -> false
                                }
                            } ?: false

                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(HomeRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                label = { Text(item.label) },
                                icon = { Text(item.icon) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onTrackClick = { track ->
                            playerViewModel.playTrack(track)
                            navController.navigate(PlayerRoute)
                        }
                    )
                }
                composable<LibraryRoute> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onTrackClick = { track ->
                            playerViewModel.playTrack(track)
                            navController.navigate(PlayerRoute)
                        }
                    )
                }
                composable<MixListRoute> {
                    MixListScreen(
                        viewModel = mixListViewModel,
                        onMixClick = { mixId ->
                            navController.navigate(MixDetailRoute(mixId))
                        }
                    )
                }
                composable<MixDetailRoute> { backStackEntry ->
                    val route = backStackEntry.arguments?.getString("mixId") ?: return@composable
                    MixDetailScreen(
                        viewModel = mixDetailViewModel,
                        mixId = route,
                        onBack = { navController.popBackStack() },
                        onPlayMix = { mixId ->
                            // TODO: Phase 4d — play mix with ClippingMediaSource
                        }
                    )
                }
                composable<DownloadsRoute> {
                    DownloadsScreen(
                        viewModel = downloadsViewModel,
                        onTrackClick = { track ->
                            playerViewModel.playTrack(track)
                            navController.navigate(PlayerRoute)
                        }
                    )
                }
                composable<PlayerRoute> {
                    PlayerScreen(
                        viewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
