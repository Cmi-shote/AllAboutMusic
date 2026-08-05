package com.simiscompany.allaboutmusic.di

import com.simiscompany.allaboutmusic.data.downloader.DownloadManager
import com.simiscompany.allaboutmusic.data.export.MixExporter
import com.simiscompany.allaboutmusic.data.scanner.LocalAudioScanner
import com.simiscompany.allaboutmusic.player.MusicPlayer
import org.koin.dsl.module

actual val platformModule = module {
    single { MusicPlayer() }
    single { DownloadManager(database = get(), jamendoApi = get()) }
    single { LocalAudioScanner() }
    single { MixExporter() }
}
