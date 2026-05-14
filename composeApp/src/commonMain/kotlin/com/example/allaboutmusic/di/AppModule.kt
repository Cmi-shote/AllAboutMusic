package com.example.allaboutmusic.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.allaboutmusic.data.api.JamendoApiService
import com.example.allaboutmusic.data.api.JamendoMusicSource
import com.example.allaboutmusic.data.api.createHttpClient
import com.example.allaboutmusic.data.database.AppDatabase
import com.example.allaboutmusic.data.database.getDatabaseBuilder
import com.example.allaboutmusic.data.downloader.DownloadRepository
import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.domain.model.MusicSource
import com.example.allaboutmusic.domain.usecase.GetFeaturedTracksUseCase
import com.example.allaboutmusic.domain.usecase.GetStreamUrlUseCase
import com.example.allaboutmusic.domain.usecase.GetTracksByGenreUseCase
import com.example.allaboutmusic.domain.usecase.SearchTracksUseCase
import com.example.allaboutmusic.ui.downloads.DownloadsViewModel
import com.example.allaboutmusic.ui.home.HomeViewModel
import com.example.allaboutmusic.ui.player.PlayerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Network
    single { createHttpClient() }
    single { JamendoApiService(get(), getProperty("JAMENDO_CLIENT_ID")) }
    single<MusicSource> { JamendoMusicSource(get()) }

    // Database
    single<AppDatabase> {
        getDatabaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(true)
            .build()
    }
    single { get<AppDatabase>().trackDao() }
    single { get<AppDatabase>().downloadQueueDao() }

    // Repositories
    single { TrackRepository(get(), get()) }
    single { DownloadRepository(get(), get(), get()) }

    // Use cases
    factory { SearchTracksUseCase(get()) }
    factory { GetStreamUrlUseCase(get()) }
    factory { GetFeaturedTracksUseCase(get()) }
    factory { GetTracksByGenreUseCase(get()) }

    // ViewModels
    viewModel { PlayerViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { DownloadsViewModel(get()) }
}
