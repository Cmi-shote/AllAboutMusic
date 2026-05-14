# Music App — Claude Code Context

## What this project is
Cross-platform music streaming and download app. KMP + Compose Multiplatform (Android + iOS).
Android primary. Users search, stream, download songs and build cue-pointed mixes.

## Stack
- Networking:  Ktor (KMP) + kotlinx.serialization
- Database:    Room (KMP)
- Player:      Media3/ExoPlayer (Android), AVPlayer (iOS) via expect/actual
- DI:          Koin (KMP)
- UI:          Compose Multiplatform (shared across Android + iOS)
- Downloads:   WorkManager (Android), URLSession (iOS)
- Images:      Coil 3 (KMP)

## Project structure
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

## Build commands
./gradlew :composeApp:assembleDebug          # Android debug APK
./gradlew :composeApp:kspCommonMainKotlinMetadata   # Room KSP processing
./gradlew test                                # shared tests
./gradlew :composeApp:connectedAndroidTest   # instrumented tests

## Critical rules — never violate these
1. Never store stream URLs in the database — they expire in 30-60 min.
   Always store track_id only. Fetch a fresh URL just before play/download.
2. All downloads go to context.filesDir/music/{track_id}.mp3 — never external storage.
3. Never use yt-dlp or unofficial stream extraction.
4. Streamed track seeks: debounce to finger-lift only, never per-drag-event.
5. ClippingMediaSource takes microseconds (ms * 1000L), not milliseconds.
6. AVPlayer boundary observers must be removed before replacing the current item.
7. WorkManager download workers must handle partial file cleanup on failure.
8. Jamendo attribution required: "Artist — via Jamendo (CC BY)" on every track.

## Music source API
- Jamendo base URL: https://api.jamendo.com/v3.0
- Always request audioformat=mp32 for 320kbps
- Filter remixes: exclude tags/names containing "remix", "dj", "mashup", "remaster"
- Use featured=1 for curated tracks on home screen
- Stream/download URLs expire — fetch fresh just before use

## Cue-in / Cue-out (mix arranger)
- MixTrack: cueInMs: Long = 0, cueOutMs: Long? = null (null = play to end)
- Android: ClippingMediaSource(source, cueInMs * 1000L, cueOutMs?.let{it*1000L} ?: C.TIME_END_OF_SOURCE)
- iOS: seekTo(cueInMs) on item load + addBoundaryTimeObserver for cueOut
- Waveform scrubber: downloaded tracks only. Streamed: plain seek bar.
- Max 50 tracks per mix

## What to ask me before doing
- Changing the Room schema
- Adding a new dependency
- Anything that affects iOS parity
- Any UI/UX decisions not covered in SPEC.md
