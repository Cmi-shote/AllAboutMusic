package com.simiscompany.allaboutmusic.ui.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.simiscompany.allaboutmusic.domain.model.DownloadItem
import com.simiscompany.allaboutmusic.domain.model.Track
import com.simiscompany.allaboutmusic.resources.Res
import com.simiscompany.allaboutmusic.resources.action_cancel
import com.simiscompany.allaboutmusic.resources.action_grant_permission
import com.simiscompany.allaboutmusic.resources.action_retry
import com.simiscompany.allaboutmusic.resources.cd_rescan
import com.simiscompany.allaboutmusic.resources.device_no_music
import com.simiscompany.allaboutmusic.resources.device_permission_needed
import com.simiscompany.allaboutmusic.resources.device_scanning
import com.simiscompany.allaboutmusic.resources.device_track_count
import com.simiscompany.allaboutmusic.resources.download_cancelled
import com.simiscompany.allaboutmusic.resources.download_failed
import com.simiscompany.allaboutmusic.resources.download_percent
import com.simiscompany.allaboutmusic.resources.download_waiting
import com.simiscompany.allaboutmusic.resources.library_active_downloads
import com.simiscompany.allaboutmusic.resources.library_clear_queue
import com.simiscompany.allaboutmusic.resources.library_downloaded_track_count
import com.simiscompany.allaboutmusic.resources.library_no_downloads
import com.simiscompany.allaboutmusic.resources.library_storage_short
import com.simiscompany.allaboutmusic.resources.library_storage_used
import com.simiscompany.allaboutmusic.resources.library_storage_warning
import com.simiscompany.allaboutmusic.resources.library_tab_device
import com.simiscompany.allaboutmusic.resources.library_tab_downloads
import com.simiscompany.allaboutmusic.resources.library_title
import com.simiscompany.allaboutmusic.ui.components.TrackCard
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onTrackClick: (Track) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.library_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))

        @OptIn(ExperimentalMaterial3Api::class)
        PrimaryTabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = state.selectedTab == LibraryTab.DOWNLOADS,
                onClick = { viewModel.selectTab(LibraryTab.DOWNLOADS) },
                text = { Text(stringResource(Res.string.library_tab_downloads)) }
            )
            Tab(
                selected = state.selectedTab == LibraryTab.DEVICE,
                onClick = { viewModel.selectTab(LibraryTab.DEVICE) },
                text = { Text(stringResource(Res.string.library_tab_device)) }
            )
        }

        Spacer(Modifier.height(8.dp))

        when (state.selectedTab) {
            LibraryTab.DOWNLOADS -> DownloadsTab(
                tracks = state.downloadedTracks,
                activeDownloads = state.activeDownloads,
                storageUsedBytes = state.storageUsedBytes,
                showStorageWarning = state.showStorageWarning,
                onTrackClick = onTrackClick,
                onCancelDownload = { viewModel.cancelDownload(it) },
                onRetryDownload = { viewModel.retryDownload(it) },
                onClearCompleted = { viewModel.clearCompleted() }
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
    activeDownloads: List<DownloadItem>,
    storageUsedBytes: Long,
    showStorageWarning: Boolean,
    onTrackClick: (Track) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    // Non-completed downloads (pending, downloading, failed, cancelled)
    val queueItems = activeDownloads.filter { it.status != DownloadItem.Status.COMPLETED }

    val hasCompletedQueue = activeDownloads.any { it.status == DownloadItem.Status.COMPLETED }

    if (queueItems.isEmpty() && tracks.isEmpty()) {
        // Storage info
        Text(
            text = stringResource(Res.string.library_storage_used, formatBytes(storageUsedBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = if (showStorageWarning) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.library_no_downloads),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Storage info
        item("storage") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.library_storage_short, formatBytes(storageUsedBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (showStorageWarning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasCompletedQueue) {
                    TextButton(onClick = onClearCompleted) {
                        Text(
                            stringResource(Res.string.library_clear_queue),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            if (showStorageWarning) {
                Text(
                    text = stringResource(Res.string.library_storage_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Active download queue
        if (queueItems.isNotEmpty()) {
            item("queue_header") {
                Text(
                    text = stringResource(Res.string.library_active_downloads),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            items(queueItems, key = { "dl_${it.id}" }) { item ->
                DownloadQueueCard(
                    item = item,
                    onCancel = { onCancelDownload(item.id) },
                    onRetry = { onRetryDownload(item.id) },
                    modifier = Modifier.animateItem()
                )
            }
            item("queue_divider") {
                Spacer(Modifier.height(8.dp))
            }
        }

        // Downloaded tracks
        if (tracks.isNotEmpty()) {
            item("tracks_header") {
                Text(
                    text = pluralStringResource(
                        Res.plurals.library_downloaded_track_count,
                        tracks.size,
                        tracks.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
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
private fun DownloadQueueCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.track.coverUrl,
                contentDescription = item.track.title,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Crossfade(
                    targetState = item.status,
                    animationSpec = tween(300)
                ) { status ->
                    when (status) {
                        DownloadItem.Status.PENDING ->
                            Text(
                                stringResource(Res.string.download_waiting),
                                style = MaterialTheme.typography.labelSmall
                            )
                        DownloadItem.Status.DOWNLOADING -> {
                            Column {
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(
                                        Res.string.download_percent,
                                        (item.progress * 100).toInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        DownloadItem.Status.FAILED ->
                            Text(
                                item.errorMessage ?: stringResource(Res.string.download_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        DownloadItem.Status.CANCELLED ->
                            Text(
                                stringResource(Res.string.download_cancelled),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        else -> {}
                    }
                }
            }

            when (item.status) {
                DownloadItem.Status.PENDING,
                DownloadItem.Status.DOWNLOADING ->
                    TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
                DownloadItem.Status.FAILED,
                DownloadItem.Status.CANCELLED ->
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                else -> {}
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> {
            val mb = bytes / (1024.0 * 1024.0)
            "${(mb * 10).toLong() / 10.0} MB"
        }
        else -> {
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            "${(gb * 100).toLong() / 100.0} GB"
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
                    text = stringResource(Res.string.device_permission_needed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRequestPermission) {
                    Text(stringResource(Res.string.action_grant_permission))
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
                Text(stringResource(Res.string.device_scanning))
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
                text = pluralStringResource(
                    Res.plurals.device_track_count,
                    tracks.size,
                    tracks.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(Res.string.cd_rescan)
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.device_no_music),
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
