package com.example.allaboutmusic.di

import com.example.allaboutmusic.data.database.appContext
import com.example.allaboutmusic.data.downloader.DownloadManager
import com.example.allaboutmusic.data.export.MixExporter
import com.example.allaboutmusic.data.scanner.LocalAudioScanner
import com.example.allaboutmusic.player.MusicPlayer
import org.koin.dsl.module

actual val platformModule = module {
    single { MusicPlayer(appContext) }
    single { DownloadManager(appContext) }
    single { LocalAudioScanner(appContext) }
    single { MixExporter(appContext) }
}
