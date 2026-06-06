package com.example.allaboutmusic.ui.mix

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.allaboutmusic.domain.model.MixTrack
import com.example.allaboutmusic.domain.model.Track
import com.example.allaboutmusic.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixDetailScreen(
    viewModel: MixDetailViewModel,
    mixId: String,
    onBack: () -> Unit,
    onPlayMix: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(mixId) {
        viewModel.loadMix(mixId)
    }

    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.mix?.name ?: "Mix") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("<") }
                },
                actions = {
                    if (state.tracks.isNotEmpty()) {
                        TextButton(onClick = { onPlayMix(mixId) }) {
                            Text("Play")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddTrackSheet() }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks in this mix.\nTap + to add downloaded tracks.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, mixTrack ->
                        MixTrackCard(
                            mixTrack = mixTrack,
                            index = index,
                            isExpanded = state.expandedTrackId == mixTrack.id,
                            isFirst = index == 0,
                            isLast = index == state.tracks.lastIndex,
                            onToggleExpand = { viewModel.toggleExpanded(mixTrack.id) },
                            onRemove = { viewModel.removeTrack(mixTrack.id) },
                            onCuePointsChanged = { cueIn, cueOut ->
                                viewModel.updateCuePoints(mixTrack.id, cueIn, cueOut)
                            },
                            onMoveUp = { viewModel.moveTrack(index, index - 1) },
                            onMoveDown = { viewModel.moveTrack(index, index + 1) }
                        )
                    }
                }
            }
        }
    }

    // Add track bottom sheet
    if (state.showAddTrackSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissAddTrackSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AddTrackSheet(
                tracks = state.availableTracks,
                onTrackSelected = { viewModel.addTrack(it) }
            )
        }
    }
}

@Composable
private fun MixTrackCard(
    mixTrack: MixTrack,
    index: Int,
    isExpanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onCuePointsChanged: (Long, Long?) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp)
                )

                AsyncImage(
                    model = mixTrack.coverUrl,
                    contentDescription = mixTrack.title,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mixTrack.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mixTrack.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Reorder buttons
                Column {
                    if (!isFirst) {
                        TextButton(onClick = onMoveUp, modifier = Modifier.height(28.dp)) {
                            Text("^", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (!isLast) {
                        TextButton(onClick = onMoveDown, modifier = Modifier.height(28.dp)) {
                            Text("v", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Cue point info summary
            val cueInText = formatDuration(mixTrack.cueInMs)
            val cueOutText = mixTrack.cueOutMs?.let { formatDuration(it) } ?: "end"
            Text(
                text = "Cue: $cueInText - $cueOutText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 4.dp)
            )

            // Expanded cue-point editor
            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                CuePointEditor(
                    mixTrack = mixTrack,
                    onCuePointsChanged = onCuePointsChanged,
                    onRemove = onRemove
                )
            }
        }
    }
}

@Composable
private fun CuePointEditor(
    mixTrack: MixTrack,
    onCuePointsChanged: (Long, Long?) -> Unit,
    onRemove: () -> Unit
) {
    val durationMs = mixTrack.durationMs.toFloat()
    if (durationMs <= 0f) return

    var cueIn by remember(mixTrack.id) { mutableStateOf(mixTrack.cueInMs.toFloat()) }
    var cueOut by remember(mixTrack.id) {
        mutableStateOf(mixTrack.cueOutMs?.toFloat() ?: durationMs)
    }
    var useFullEnd by remember(mixTrack.id) { mutableStateOf(mixTrack.cueOutMs == null) }

    Column(modifier = Modifier.padding(start = 28.dp)) {
        Text("Cue In: ${formatDuration(cueIn.toLong())}", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = cueIn,
            onValueChange = { cueIn = it },
            onValueChangeFinished = {
                val outMs = if (useFullEnd) null else cueOut.toLong()
                onCuePointsChanged(cueIn.toLong(), outMs)
            },
            valueRange = 0f..maxOf(cueOut - 1000f, 0f),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cue Out: ${if (useFullEnd) "end" else formatDuration(cueOut.toLong())}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                useFullEnd = !useFullEnd
                if (useFullEnd) {
                    onCuePointsChanged(cueIn.toLong(), null)
                } else {
                    cueOut = durationMs
                    onCuePointsChanged(cueIn.toLong(), durationMs.toLong())
                }
            }) {
                Text(if (useFullEnd) "Set cue-out" else "Play to end")
            }
        }

        if (!useFullEnd) {
            Slider(
                value = cueOut,
                onValueChange = { cueOut = it },
                onValueChangeFinished = {
                    onCuePointsChanged(cueIn.toLong(), cueOut.toLong())
                },
                valueRange = (cueIn + 1000f)..durationMs,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRemove) {
            Text("Remove from mix", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddTrackSheet(
    tracks: List<Track>,
    onTrackSelected: (Track) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add Track", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (tracks.isEmpty()) {
            Text(
                text = "No downloaded tracks available.\nDownload songs first to add them to a mix.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackSelected(track) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.coverUrl,
                            contentDescription = track.title,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatDuration(track.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
