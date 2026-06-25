package com.example.allaboutmusic.ui.mix

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.allaboutmusic.domain.model.Mix
import com.example.allaboutmusic.ui.components.rememberImagePickerLauncher

@Composable
fun MixListScreen(
    viewModel: MixListViewModel,
    onMixClick: (String) -> Unit,
    onPlayMix: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Mixes", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            if (state.mixes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No mixes yet.\nTap + to create one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.mixes, key = { it.id }) { mix ->
                        MixCard(
                            mix = mix,
                            onClick = { onMixClick(mix.id) },
                            onPlay = { onPlayMix(mix.id) },
                            onDelete = { viewModel.deleteMix(mix.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // FAB positioned in bottom-end
        FloatingActionButton(
            onClick = { viewModel.showCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Create mix"
            )
        }
    }

    if (state.showCreateDialog) {
        CreateMixDialog(
            onDismiss = { viewModel.dismissCreateDialog() },
            onCreate = { name, coverPath -> viewModel.createMix(name, coverPath) }
        )
    }
}

@Composable
private fun MixCoverImage(
    coverImagePath: String?,
    size: Int,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    if (coverImagePath != null) {
        AsyncImage(
            model = coverImagePath,
            contentDescription = "Mix cover",
            modifier = modifier
                .size(size.dp)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Default cover",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size((size / 2).dp)
            )
        }
    }
}

@Composable
private fun MixCard(
    mix: Mix,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MixCoverImage(
                coverImagePath = mix.coverImagePath,
                size = 48
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mix.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${mix.trackCount} track${if (mix.trackCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onPlay,
                enabled = mix.trackCount > 0
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play mix",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete mix",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CreateMixDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var coverImagePath by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberImagePickerLauncher { path ->
        if (path != null) coverImagePath = path
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Mix") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { imagePicker.launch() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(88.dp)) {
                        if (coverImagePath != null) {
                            AsyncImage(
                                model = coverImagePath,
                                contentDescription = "Mix cover",
                                modifier = Modifier
                                    .size(80.dp)
                                    .align(Alignment.TopStart)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .align(Alignment.TopStart)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = "Add cover image",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Add cover (optional)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mix name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), coverImagePath) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
