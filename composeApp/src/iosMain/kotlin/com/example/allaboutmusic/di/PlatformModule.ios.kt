package com.example.allaboutmusic.di

import com.example.allaboutmusic.data.downloader.DownloadManager
import com.example.allaboutmusic.player.MusicPlayer
import org.koin.dsl.module

actual val platformModule = module {
    single { MusicPlayer() }
    single { DownloadManager() }
}
