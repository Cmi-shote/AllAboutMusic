package com.example.allaboutmusic.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (String?) -> Unit
): ImagePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            onImagePicked(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val coversDir = File(context.filesDir, "mix_covers")
                    coversDir.mkdirs()
                    val destFile = File(coversDir, "${Uuid.random()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    destFile.absolutePath
                } catch (_: Exception) {
                    null
                }
            }
            onImagePicked(path)
        }
    }

    return ImagePickerLauncher(
        launch = { launcher.launch("image/*") }
    )
}
