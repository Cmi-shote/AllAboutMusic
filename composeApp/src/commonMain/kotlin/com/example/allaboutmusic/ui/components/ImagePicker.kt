package com.example.allaboutmusic.ui.components

import androidx.compose.runtime.Composable

data class ImagePickerLauncher(
    val launch: () -> Unit
)

@Composable
expect fun rememberImagePickerLauncher(
    onImagePicked: (String?) -> Unit
): ImagePickerLauncher
