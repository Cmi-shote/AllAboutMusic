package com.example.allaboutmusic.di

import com.example.allaboutmusic.data.downloader.DownloadManager
import com.example.allaboutmusic.data.export.MixExporter
import com.example.allaboutmusic.data.scanner.LocalAudioScanner
import com.example.allaboutmusic.player.MusicPlayer
import org.koin.dsl.module

actual val platformModule = module {
    single { MusicPlayer() }
    single { DownloadManager(database = get(), jamendoApi = get()) }
    single { LocalAudioScanner() }
    single { MixExporter() }
}
