package com.example.allaboutmusic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.allaboutmusic.di.appModule
import com.example.allaboutmusic.di.platformModule
import com.example.allaboutmusic.ui.navigation.AppNavigation
import org.koin.compose.KoinApplication

@Composable
fun App(
    onRequestAudioPermission: ((Boolean) -> Unit) -> Unit = {}
) {
    KoinApplication(application = {
        properties(mapOf("JAMENDO_CLIENT_ID" to "72cec4ca"))
        modules(appModule, platformModule)
    }) {
        MaterialTheme {
            AppNavigation(onRequestAudioPermission = onRequestAudioPermission)
        }
    }
}
