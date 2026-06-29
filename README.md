# AllAboutMusic

Cross-platform music streaming and download app built with **Kotlin Multiplatform + Compose Multiplatform**. Browse, stream, and download Creative Commons music from [Jamendo](https://www.jamendo.com), then build cue-pointed mixes for seamless playback.

**Platforms:** Android (primary), iOS (secondary)

## Features

- Search and stream 320kbps MP3 tracks from Jamendo
- Browse featured tracks and filter by genre
- Download tracks for offline playback
- Build mixes with drag-reorder and cue-in/cue-out points
- Waveform scrubber for downloaded tracks
- Mini-player with full-screen expansion
- Background playback with media controls

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Compose Multiplatform |
| Networking | Ktor + kotlinx.serialization |
| Database | Room (KMP) |
| Player (Android) | Media3 / ExoPlayer |
| Player (iOS) | AVPlayer / AVQueuePlayer |
| DI | Koin |
| Downloads (Android) | WorkManager |
| Downloads (iOS) | URLSession |
| Images | Coil 3 |

## Project Structure

Single `composeApp` module with package-level separation:

```
com.example.allaboutmusic/
  data/api/          — Ktor client, Jamendo API, DTOs
  data/database/     — Room entities, DAOs, database class
  data/downloader/   — Download queue, progress tracking
  data/repository/   — Repositories bridging API + DB
  domain/model/      — Track, Mix, MixTrack, DownloadStatus
  domain/usecase/    — SearchTracks, GetStreamUrl, ManageMix, etc.
  player/            — expect/actual MusicPlayer
  ui/home/           — Home screen (featured + genre chips)
  ui/search/         — Search results
  ui/player/         — Full player + mini-player
  ui/library/        — Downloaded tracks + mixes list
  ui/mix/            — Mix arranger + cue-point editor
  ui/downloads/      — Download queue screen
  di/                — Koin modules
```

## Setup

1. Register at [developer.jamendo.com](https://developer.jamendo.com) to get a client ID
2. Add your client ID to the project configuration
3. Build and run:

```shell
# Android
./gradlew :composeApp:assembleDebug

# Run shared tests
./gradlew test

# Instrumented tests
./gradlew :composeApp:connectedAndroidTest
```

For iOS, open the `iosApp` directory in Xcode and run from there.

## Music Source

All music is sourced from the [Jamendo API](https://developer.jamendo.com) — Creative Commons licensed tracks. Attribution is displayed on every track: **"Artist — via Jamendo (CC BY)"**.

## License

All streamed and downloaded music is subject to Jamendo's Creative Commons licensing terms.
