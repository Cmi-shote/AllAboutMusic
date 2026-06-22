package com.example.allaboutmusic.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.allaboutmusic.domain.model.Track
import com.example.allaboutmusic.ui.components.TrackCard

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onTrackClick: (Track) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        @OptIn(ExperimentalMaterial3Api::class)
        PrimaryTabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = state.selectedTab == LibraryTab.DOWNLOADS,
                onClick = { viewModel.selectTab(LibraryTab.DOWNLOADS) },
                text = { Text("Downloads") }
            )
            Tab(
                selected = state.selectedTab == LibraryTab.DEVICE,
                onClick = { viewModel.selectTab(LibraryTab.DEVICE) },
                text = { Text("Device Music") }
            )
        }

        Spacer(Modifier.height(8.dp))

        when (state.selectedTab) {
            LibraryTab.DOWNLOADS -> DownloadsTab(
                tracks = state.downloadedTracks,
                onTrackClick = onTrackClick
            )
            LibraryTab.DEVICE -> DeviceMusicTab(
                tracks = state.localTracks,
                isScanning = state.isScanning,
                hasPermission = state.hasPermission,
                onTrackClick = onTrackClick,
                onRequestPermission = onRequestPermission,
                onRefresh = { viewModel.scanLocalLibrary() }
            )
        }
    }
}

@Composable
private fun DownloadsTab(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit
) {
    Text(
        text = "${tracks.size} downloaded track${if (tracks.size != 1) "s" else ""}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No downloaded tracks yet.\nDownload songs from the Home tab to see them here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn {
            items(tracks, key = { it.id }) { track ->
                TrackCard(
                    track = track,
                    onClick = { onTrackClick(track) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun DeviceMusicTab(
    tracks: List<Track>,
    isScanning: Boolean,
    hasPermission: Boolean,
    onTrackClick: (Track) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Permission needed to access device music.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRequestPermission) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    if (isScanning) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Scanning device music...")
            }
        }
        return
    }

    Column {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "${tracks.size} track${if (tracks.size != 1) "s" else ""} on device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Rescan"
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No music found on device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        onClick = { onTrackClick(track) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
