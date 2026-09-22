package com.simiscompany.allaboutmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.simiscompany.allaboutmusic.resources.Res
import com.simiscompany.allaboutmusic.resources.action_ok
import com.simiscompany.allaboutmusic.resources.attribution_jamendo
import com.simiscompany.allaboutmusic.resources.cd_back
import com.simiscompany.allaboutmusic.resources.cd_download
import com.simiscompany.allaboutmusic.resources.cd_downloaded
import com.simiscompany.allaboutmusic.resources.cd_next
import com.simiscompany.allaboutmusic.resources.cd_pause
import com.simiscompany.allaboutmusic.resources.cd_play
import com.simiscompany.allaboutmusic.resources.cd_previous
import com.simiscompany.allaboutmusic.resources.dialog_storage_full_title
import com.simiscompany.allaboutmusic.resources.player_mix_position
import com.simiscompany.allaboutmusic.resources.player_queued
import com.simiscompany.allaboutmusic.ui.components.formatDuration
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playerState.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val track = state.currentTrack

    if (track == null) {
        onBack()
        return
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.cd_back)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Album art
            AsyncImage(
                model = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(24.dp))

            // Track info
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (track.source == "jamendo") {
                Text(
                    text = stringResource(Res.string.attribution_jamendo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mix mode indicator
            if (state.isMixMode) {
                Text(
                    text = stringResource(
                        Res.string.player_mix_position,
                        state.mixTrackIndex + 1,
                        state.mixTrackCount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(24.dp))

            // Seek bar -- debounced to finger-lift only
            if (state.durationMs > 0) {
                val displayPosition = if (isSeeking) seekPosition else position.toFloat()

                Slider(
                    value = displayPosition,
                    onValueChange = { value ->
                        isSeeking = true
                        seekPosition = value
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(seekPosition.toLong())
                        isSeeking = false
                    },
                    valueRange = 0f..state.durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(if (isSeeking) seekPosition.toLong() else position),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatDuration(state.durationMs),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Controls
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                } else {
                    // Previous (mix mode only)
                    if (state.isMixMode) {
                        IconButton(
                            onClick = { viewModel.skipToPrevious() },
                            enabled = state.mixTrackIndex > 0,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(Res.string.cd_previous),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                    }

                    // Play/Pause - prominent filled circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (state.isPlaying) Res.string.cd_pause else Res.string.cd_play
                            ),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next (mix mode only)
                    if (state.isMixMode) {
                        Spacer(Modifier.width(16.dp))
                        IconButton(
                            onClick = { viewModel.skipToNext() },
                            enabled = state.mixTrackIndex < state.mixTrackCount - 1,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(Res.string.cd_next),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Download button (not shown in mix mode -- tracks are already downloaded)
            if (!state.isMixMode) {
                if (track.isDownloaded) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text(stringResource(Res.string.cd_downloaded))
                    }
                } else {
                    Button(
                        onClick = { viewModel.downloadCurrentTrack() },
                        enabled = !isDownloading
                    ) {
                        Text(
                            stringResource(
                                if (isDownloading) Res.string.player_queued
                                else Res.string.cd_download
                            )
                        )
                    }
                }
            }
        }
    }

    if (downloadError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDownloadError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDownloadError() }) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            title = { Text(stringResource(Res.string.dialog_storage_full_title)) },
            text = { Text(downloadError ?: "") }
        )
    }
}
