package com.simiscompany.allaboutmusic.ui.downloads

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.simiscompany.allaboutmusic.resources.Res
import com.simiscompany.allaboutmusic.resources.action_cancel
import com.simiscompany.allaboutmusic.resources.action_retry
import com.simiscompany.allaboutmusic.resources.download_cancelled
import com.simiscompany.allaboutmusic.resources.download_completed
import com.simiscompany.allaboutmusic.resources.download_failed
import com.simiscompany.allaboutmusic.resources.download_percent
import com.simiscompany.allaboutmusic.resources.download_waiting
import com.simiscompany.allaboutmusic.resources.downloads_clear_completed
import com.simiscompany.allaboutmusic.resources.downloads_empty
import com.simiscompany.allaboutmusic.resources.downloads_title
import com.simiscompany.allaboutmusic.resources.library_storage_used
import com.simiscompany.allaboutmusic.resources.library_storage_warning
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onTrackClick: (com.simiscompany.allaboutmusic.domain.model.Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.downloads_title),
            style = MaterialTheme.typography.headlineMedium
        )

        // Storage info
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                Res.string.library_storage_used,
                formatBytes(state.storageUsedBytes)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.showStorageWarning) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.showStorageWarning) {
            Text(
                text = stringResource(Res.string.library_storage_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.downloads.any { it.status == DownloadItem.Status.COMPLETED }) {
            TextButton(onClick = { viewModel.clearCompleted() }) {
                Text(stringResource(Res.string.downloads_clear_completed))
            }
        }

        if (state.downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.downloads_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.downloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onClick = {
                            if (item.status == DownloadItem.Status.COMPLETED) {
                                onTrackClick(item.track)
                            }
                        },
                        onCancel = { viewModel.cancelDownload(item.id) },
                        onRetry = { viewModel.retryDownload(item.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == DownloadItem.Status.COMPLETED) { onClick() },
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.track.coverUrl,
                contentDescription = item.track.title,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
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
                Text(
                    text = item.track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Crossfade(
                    targetState = item.status,
                    animationSpec = tween(300)
                ) { status ->
                    Column {
                        when (status) {
                            DownloadItem.Status.PENDING -> {
                                Text(
                                    stringResource(Res.string.download_waiting),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            DownloadItem.Status.DOWNLOADING -> {
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
                            DownloadItem.Status.COMPLETED -> {
                                Text(
                                    stringResource(Res.string.download_completed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            DownloadItem.Status.FAILED -> {
                                Text(
                                    item.errorMessage ?: stringResource(Res.string.download_failed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            DownloadItem.Status.CANCELLED -> {
                                Text(
                                    stringResource(Res.string.download_cancelled),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Action button
            when (item.status) {
                DownloadItem.Status.PENDING,
                DownloadItem.Status.DOWNLOADING -> {
                    TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
                }
                DownloadItem.Status.FAILED,
                DownloadItem.Status.CANCELLED -> {
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                }
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

