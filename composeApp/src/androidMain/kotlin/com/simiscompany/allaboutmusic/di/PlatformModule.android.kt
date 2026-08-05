package com.simiscompany.allaboutmusic.di

import com.simiscompany.allaboutmusic.data.database.appContext
import com.simiscompany.allaboutmusic.data.downloader.DownloadManager
import com.simiscompany.allaboutmusic.data.export.MixExporter
import com.simiscompany.allaboutmusic.data.scanner.LocalAudioScanner
import com.simiscompany.allaboutmusic.player.MusicPlayer
import org.koin.dsl.module

actual val platformModule = module {
    single { MusicPlayer(appContext) }
    single { DownloadManager(appContext) }
    single { LocalAudioScanner(appContext) }
    single { MixExporter(appContext) }
}
