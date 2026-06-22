package com.example.allaboutmusic.ui.mix

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.example.allaboutmusic.domain.model.MixTrack
import com.example.allaboutmusic.domain.model.Track
import com.example.allaboutmusic.ui.components.formatDuration
import kotlin.math.roundToInt

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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (state.tracks.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.exportMix() },
                            enabled = !state.isExporting
                        ) {
                            Icon(
                                imageVector = Icons.Filled.IosShare,
                                contentDescription = "Export mix",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onPlayMix(mixId) }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play mix",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddTrackSheet() }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add track"
                )
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
                val listState = rememberLazyListState()
                var draggedIndex by remember { mutableIntStateOf(-1) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, mixTrack ->
                        val isDragged = draggedIndex == index
                        val elevation by animateDpAsState(if (isDragged) 8.dp else 0.dp)

                        Box(
                            modifier = Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .then(
                                    if (isDragged) Modifier.offset {
                                        IntOffset(0, dragOffsetY.roundToInt())
                                    } else Modifier
                                )
                        ) {
                            MixTrackCard(
                                mixTrack = mixTrack,
                                index = index,
                                isExpanded = state.expandedTrackId == mixTrack.id,
                                isDragged = isDragged,
                                elevation = elevation,
                                onToggleExpand = { viewModel.toggleExpanded(mixTrack.id) },
                                onRemove = { viewModel.removeTrack(mixTrack.id) },
                                onCuePointsChanged = { cueIn, cueOut ->
                                    viewModel.updateCuePoints(mixTrack.id, cueIn, cueOut)
                                },
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffsetY = 0f
                                },
                                onDrag = { dragAmount ->
                                    dragOffsetY += dragAmount
                                    // Calculate target index based on drag offset
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                                    val currentItem = itemInfo.firstOrNull { it.index == draggedIndex }
                                    if (currentItem != null) {
                                        val draggedCenter = currentItem.offset + currentItem.size / 2 + dragOffsetY
                                        val targetItem = itemInfo.firstOrNull { info ->
                                            info.index != draggedIndex &&
                                                draggedCenter.toInt() in info.offset..(info.offset + info.size)
                                        }
                                        if (targetItem != null) {
                                            val targetIndex = targetItem.index
                                            viewModel.moveTrack(draggedIndex, targetIndex)
                                            draggedIndex = targetIndex
                                            dragOffsetY = 0f
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggedIndex = -1
                                    dragOffsetY = 0f
                                }
                            )
                        }
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

    // Export progress dialog
    if (state.isExporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Exporting Mix") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { state.exportProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.exportProgress * 100).toInt()}%")
                }
            }
        )
    }

    // Export success dialog
    if (state.exportResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) {
                    Text("OK")
                }
            },
            title = { Text("Export Complete") },
            text = { Text("Saved to: ${state.exportResult}") }
        )
    }

    // Error dialog
    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            },
            title = { Text("Error") },
            text = { Text(state.error ?: "") }
        )
    }
}

@Composable
private fun MixTrackCard(
    mixTrack: MixTrack,
    index: Int,
    isExpanded: Boolean,
    isDragged: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onToggleExpand: () -> Unit,
    onRemove: () -> Unit,
    onCuePointsChanged: (Long, Long?) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .shadow(elevation, RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Drag handle
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { change, offset ->
                                    change.consume()
                                    onDrag(offset.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        }
                )

                Spacer(Modifier.width(8.dp))

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

                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Cue point info summary
            val cueInText = formatDuration(mixTrack.cueInMs)
            val cueOutText = mixTrack.cueOutMs?.let { formatDuration(it) } ?: "end"
            Text(
                text = "Cue: $cueInText - $cueOutText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp)
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

    Column(modifier = Modifier.padding(start = 32.dp)) {
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
