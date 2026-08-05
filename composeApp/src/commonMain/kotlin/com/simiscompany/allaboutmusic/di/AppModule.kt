package com.simiscompany.allaboutmusic.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.simiscompany.allaboutmusic.data.api.JamendoApiService
import com.simiscompany.allaboutmusic.data.api.JamendoMusicSource
import com.simiscompany.allaboutmusic.data.api.createHttpClient
import com.simiscompany.allaboutmusic.data.database.AppDatabase
import com.simiscompany.allaboutmusic.data.database.getDatabaseBuilder
import com.simiscompany.allaboutmusic.data.downloader.DownloadRepository
import com.simiscompany.allaboutmusic.data.repository.MixRepository
import com.simiscompany.allaboutmusic.data.repository.TrackRepository
import com.simiscompany.allaboutmusic.domain.model.MusicSource
import com.simiscompany.allaboutmusic.domain.usecase.GetFeaturedTracksUseCase
import com.simiscompany.allaboutmusic.domain.usecase.GetStreamUrlUseCase
import com.simiscompany.allaboutmusic.domain.usecase.GetTracksByGenreUseCase
import com.simiscompany.allaboutmusic.domain.usecase.SearchTracksUseCase
import com.simiscompany.allaboutmusic.ui.home.HomeViewModel
import com.simiscompany.allaboutmusic.ui.library.LibraryViewModel
import com.simiscompany.allaboutmusic.ui.mix.MixDetailViewModel
import com.simiscompany.allaboutmusic.ui.mix.MixListViewModel
import com.simiscompany.allaboutmusic.ui.player.PlayerViewModel
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
    single { get<AppDatabase>().mixDao() }

    // Repositories
    single { TrackRepository(get(), get()) }
    single { DownloadRepository(get(), get(), get()) }
    single { MixRepository(get()) }

    // Use cases
    factory { SearchTracksUseCase(get()) }
    factory { GetStreamUrlUseCase(get()) }
    factory { GetFeaturedTracksUseCase(get()) }
    factory { GetTracksByGenreUseCase(get()) }

    // ViewModels
    viewModel { PlayerViewModel(get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { MixListViewModel(get()) }
    viewModel { MixDetailViewModel(get(), get(), get()) }
}
