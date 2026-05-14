package com.example.allaboutmusic.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.allaboutmusic.data.api.JamendoApiService
import com.example.allaboutmusic.data.api.JamendoMusicSource
import com.example.allaboutmusic.data.api.createHttpClient
import com.example.allaboutmusic.data.database.AppDatabase
import com.example.allaboutmusic.data.database.getDatabaseBuilder
import com.example.allaboutmusic.data.repository.TrackRepository
import com.example.allaboutmusic.domain.model.MusicSource
import com.example.allaboutmusic.domain.usecase.GetFeaturedTracksUseCase
import com.example.allaboutmusic.domain.usecase.GetStreamUrlUseCase
import com.example.allaboutmusic.domain.usecase.GetTracksByGenreUseCase
import com.example.allaboutmusic.domain.usecase.SearchTracksUseCase
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
            .build()
    }
    single { get<AppDatabase>().trackDao() }

    // Repository
    single { TrackRepository(get(), get()) }

    // Use cases
    factory { SearchTracksUseCase(get()) }
    factory { GetStreamUrlUseCase(get()) }
    factory { GetFeaturedTracksUseCase(get()) }
    factory { GetTracksByGenreUseCase(get()) }
}
