package com.example.allaboutmusic

import androidx.compose.ui.window.ComposeUIViewController
import platform.MediaPlayer.MPMediaLibrary
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusAuthorized

fun MainViewController() = ComposeUIViewController {
    App(
        onRequestAudioPermission = { callback ->
            MPMediaLibrary.requestAuthorization { status ->
                callback(status == MPMediaLibraryAuthorizationStatusAuthorized)
            }
        }
    )
}
