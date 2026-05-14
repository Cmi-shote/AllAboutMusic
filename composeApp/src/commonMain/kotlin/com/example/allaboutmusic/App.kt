package com.example.allaboutmusic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.allaboutmusic.di.appModule
import com.example.allaboutmusic.di.platformModule
import com.example.allaboutmusic.ui.navigation.AppNavigation
import org.koin.compose.KoinApplication
import org.koin.core.parameter.parametersOf

@Composable
fun App() {
    KoinApplication(application = {
        properties(mapOf("JAMENDO_CLIENT_ID" to "YOUR_CLIENT_ID_HERE"))
        modules(appModule, platformModule)
    }) {
        MaterialTheme {
            AppNavigation()
        }
    }
}
