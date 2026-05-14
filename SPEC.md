# AllAboutMusic — Technical Specification

## Overview
Cross-platform music streaming and download app built with Kotlin Multiplatform + Compose Multiplatform. Android primary, iOS secondary. Users search, stream, and download Creative Commons music from Jamendo, then build cue-pointed mixes for seamless playback.

## Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Music source | Jamendo only (SoundCloud abstracted for later) | SoundCloud API registration is closed; Jamendo has open API with direct 320kbps MP3 downloads |
| Project structure | Single `composeApp` module, package separation | Simpler Gradle config, faster builds, sufficient boundary via packages |
| iOS UI | Compose Multiplatform | Share ~90% UI code, already in template |
| Auth | None — fully local | All data on-device, no cloud sync |
| Mix limits | 50 tracks max per mix, playback only | Prevents UI perf issues; no export feature |
| Attribution | Text line on player UI | "Artist — via Jamendo (CC BY)" below track title |
| Downloads | Queue multiple + storage warning | Sequential execution, warn at configurable threshold (default 2GB) |
| Home screen | Featured tracks + genre filter chips | Uses Jamendo featured/curated endpoints |
| Player UI | Standard layout + mini-player | Full-screen: album art, controls, seek bar. Collapsed: mini-player bar |

## Architecture

### Package Structure (single module: `composeApp`)
```
com.example.allaboutmusic/
  data/
    api/              Ktor HTTP client, Jamendo API service, response DTOs
    database/         Room entities, DAOs, database class, migrations
    downloader/       Download queue logic, progress tracking
    repository/       Repository implementations bridging API + DB
  domain/
    model/            Domain models (Track, Mix, MixTrack, DownloadStatus)
    usecase/          Use cases (SearchTracks, GetStreamUrl, ManageMix, etc.)
  player/
    MusicPlayer.kt    expect/actual interface
    androidMain/      Media3 ExoPlayer actual
    iosMain/          AVPlayer/AVQueuePlayer actual
  ui/
    home/             Home screen (featured + genre chips + search)
    search/           Search results screen
    player/           Full player screen + mini-player bar
    library/          Downloaded tracks / offline library
    mix/              Mix arranger (drag-reorder, cue-point editor)
    downloads/        Download queue / progress screen
  di/                 Koin modules
```

### Tech Stack
| Layer | Technology |
|---|---|
| Networking | Ktor (KMP) with kotlinx.serialization |
| Database | Room (KMP) |
| Player (Android) | Media3 / ExoPlayer |
| Player (iOS) | AVPlayer / AVQueuePlayer |
| DI | Koin (KMP) |
| UI | Compose Multiplatform |
| Downloads (Android) | WorkManager |
| Downloads (iOS) | URLSession background download |
| Images | Coil 3 (KMP) |
| State | Kotlin Flows + Coroutines |

## Music Source: Jamendo API

- Base URL: `https://api.jamendo.com/v3.0`
- Auth: `client_id` query parameter (register at developer.jamendo.com)
- Key endpoints:
  - `GET /tracks/?search={query}&client_id={id}&format=jsonpretty&audioformat=mp32`
  - `GET /tracks/?id={trackId}&client_id={id}&audioformat=mp32` (returns `audiodownload` URL)
  - `GET /tracks/?featured=1&client_id={id}&audioformat=mp32` (featured/curated)
  - `GET /tracks/?tags={genre}&client_id={id}&audioformat=mp32` (genre filter)
- Always request `audioformat=mp32` for 320kbps MP3
- Remix filter: exclude results where `name` or `tags` contain "remix", "dj", "mashup", "remaster" (case-insensitive)
- Stream URLs expire in 30-60 min — NEVER store them. Store `track_id` only; fetch fresh URL just before playback or download.

### SoundCloud Abstraction
Build a `MusicSource` interface so a second source can be plugged in later:
```kotlin
interface MusicSource {
    suspend fun search(query: String, limit: Int = 20): List<Track>
    suspend fun getTrack(id: String): Track
    suspend fun getStreamUrl(trackId: String): String
    suspend fun getDownloadUrl(trackId: String): String
    suspend fun getFeatured(limit: Int = 20): List<Track>
    suspend fun getByGenre(genre: String, limit: Int = 20): List<Track>
}
```

## Database Schema (Room KMP)

```kotlin
@Entity(tableName = "track")
data class TrackEntity(
    @PrimaryKey val id: String,
    val source: String = "jamendo",
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val coverUrl: String? = null,
    val licenseUrl: String? = null,
    val localPath: String? = null,
    val downloadedAt: Long? = null
)

@Entity(tableName = "mix")
data class MixEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "mix_track",
    foreignKeys = [
        ForeignKey(entity = MixEntity::class, parentColumns = ["id"], childColumns = ["mixId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"])
    ],
    indices = [Index("mixId"), Index("trackId")]
)
data class MixTrackEntity(
    @PrimaryKey val id: String,
    val mixId: String,
    val trackId: String,
    val position: Int,
    val cueInMs: Long = 0,
    val cueOutMs: Long? = null
)

@Entity(
    tableName = "download_queue",
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"])
    ],
    indices = [Index("trackId")]
)
data class DownloadQueueEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val status: String = "pending",
    val progress: Float = 0f,
    val localPath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long
)
```

Download statuses: `pending`, `downloading`, `completed`, `failed`, `cancelled`

## Player System

### expect/actual MusicPlayer
```kotlin
expect class MusicPlayer {
    fun playTrack(track: Track, streamUrl: String)
    fun playMix(mixTracks: List<MixTrack>, streamUrls: Map<String, String>)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
    val playerState: StateFlow<PlayerState>
    val currentPosition: StateFlow<Long>
    val currentTrackIndex: StateFlow<Int>
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val durationMs: Long = 0,
    val isBuffering: Boolean = false
)
```

### Android (Media3 ExoPlayer)
- Single track: `ProgressiveMediaSource` from stream URL
- Mix playback: `ConcatenatingMediaSource2` with `ClippingMediaSource` per track
- Cue points: `ClippingMediaSource(source, cueInMs * 1000L, cueOutMs?.let { it * 1000L } ?: C.TIME_END_OF_SOURCE)`
- Cache: `SimpleCache` with 500MB `LeastRecentlyUsedCacheEvictor`
- Offline: play from `FileDataSource` using `track.local_path`

### iOS (AVPlayer)
- Single track: `AVPlayer` with `AVPlayerItem(url:)`
- Mix playback: `AVQueuePlayer` with sequential `AVPlayerItem`s
- Cue-in: `seek(to: CMTime)` after item status becomes `.readyToPlay`
- Cue-out: `addBoundaryTimeObserver` at cue-out time, advance to next item in callback
- CRITICAL: Remove boundary observers before replacing/advancing items

## Cue-In / Cue-Out System

- Each `MixTrack` has `cueInMs: Long = 0` and `cueOutMs: Long? = null`
- `null` cueOut means play to end of track
- Downloaded tracks: waveform scrubber for precise cue point setting
- Streamed tracks: plain seek bar, debounced to finger-lift only (no per-drag seeks)
- Preview button: plays just the clipped segment of one track
- Mix playback: auto-advances through all tracks respecting cue points

## Download System

### Android (WorkManager)
1. User taps download -> insert into `download_queue` with status `pending`
2. Enqueue `DownloadWorker` as unique work per `track_id`
3. Worker fetches fresh `audiodownload` URL from Jamendo API (never queue the URL)
4. Chunked download with `HttpURLConnection` + `Range` header for resume
5. Save to `context.filesDir/music/{track_id}.mp3`
6. Update `download_queue.progress` periodically
7. On success: update `track.local_path` and `track.downloaded_at`, set status `completed`
8. On failure: clean up partial file, set status `failed` with `error_message`
9. Show persistent notification with progress %
10. Retry policy: exponential backoff, max 3 attempts

### iOS (URLSession)
- `URLSessionDownloadTask` with background configuration
- Same flow: fresh URL -> download -> update DB

### Storage Warning
- Check `filesDir` usage on each download completion
- Warn user when total music storage exceeds configurable threshold (default 2GB)
- Show current usage in settings/library screen

## UI Screens

### 1. Home Screen
- Top: Search bar
- Below: Horizontal genre chip row (Rock, Electronic, Jazz, Hip-Hop, Classical, Pop, Ambient, etc.)
- Main: Vertical list of featured tracks (album art, title, artist, duration)
- Tapping a genre chip filters the list
- Tapping a track -> full player screen + starts streaming
- Long press or menu -> download option

### 2. Search Results
- Real-time search with debounce (300ms)
- Results: same track card layout as home
- Empty state when no results

### 3. Full Player Screen
- Large album art (top half)
- Track title + artist
- Attribution line: "via Jamendo (CC BY)"
- Seek bar with current time / total time
- Controls: previous, play/pause, next (if in mix/queue)
- Download button (if not already downloaded)
- "Add to Mix" button

### 4. Mini Player Bar
- Fixed at bottom of screen (above nav bar if present)
- Album art thumbnail, track title, play/pause button
- Tap to expand to full player

### 5. Library Screen
- Tabs: Downloaded / Mixes
- Downloaded: list of offline tracks, swipe to delete
- Mixes: list of mixes, tap to open mix arranger

### 6. Mix Arranger
- Track list with drag handles for reordering
- Each track row: art, title, artist, cue-in/cue-out times
- Expand a track -> cue-point editor (waveform or seek bar)
- Preview button per track (plays clipped segment)
- Play All button (plays full mix with transitions)
- Max 50 tracks per mix

### 7. Download Queue
- List of pending/active/completed downloads
- Progress bar per item
- Cancel button for pending/active
- Retry for failed

### Navigation
- Bottom nav: Home | Library | Downloads
- Player accessible from any screen via mini-player bar

## Build Phases

### Phase 1 — Foundation (API + Data Layer)
- Set up package structure within `composeApp`
- Add Ktor + kotlinx.serialization dependencies
- Implement `JamendoApi` service (search, getTrack, getStreamUrl, getFeatured, getByGenre)
- Remix filter logic
- Room database with `TrackEntity` + `TrackDao`
- `MusicSource` interface + `JamendoMusicSource` implementation
- Unit tests for API parsing and remix filtering
- **Commit:** "feat: Jamendo API integration with search and track retrieval"

### Phase 2 — Streaming + Player UI
- Add Media3/ExoPlayer dependencies
- `expect/actual MusicPlayer` (Android actual only, iOS stub)
- ExoPlayer setup with `SimpleCache` (500MB)
- `PlayerViewModel` with play/pause/seek
- Home screen with featured tracks + genre chips
- Search screen with debounced input
- Full player screen + mini-player bar
- **Commit:** "feat: streaming playback with ExoPlayer and player UI"

### Phase 3 — Downloads + Offline
- Add WorkManager dependency
- `DownloadWorker` with chunked download + resume
- `download_queue` Room table + DAO
- Download queue UI screen
- Notification with progress
- Offline playback via local file URI
- Storage usage tracking + warning
- **Commit:** "feat: download system with WorkManager and offline playback"

### Phase 4 — Mix Arranger + Cue Points
- `mix` and `mix_track` Room tables + DAOs
- `MixTrack` domain model
- `ClippingMediaSource` + `ConcatenatingMediaSource2` for mix playback
- Mix arranger UI: drag-reorder, cue-point editor, preview
- Waveform scrubber (downloaded) / plain seek bar (streamed)
- 50-track limit enforcement
- **Commit:** "feat: mix arranger with cue-in/cue-out and drag reorder"

### Phase 5 — iOS Player
- AVPlayer/AVQueuePlayer actual implementation
- Cue-in via seek, cue-out via boundary observer
- iOS download manager (URLSession)
- Verify Compose Multiplatform UI works on iOS
- **Commit:** "feat: iOS player implementation with AVPlayer"

### Phase 6 — Polish + Store Prep
- `MediaSessionService` for background playback
- Audio focus handling (pause on call, duck, etc.)
- Lock screen / notification media controls
- iOS background audio entitlement
- Jamendo attribution compliance check
- App icon, splash screen
- Play Store / App Store policy review
- **Commit:** "feat: background playback, notifications, store prep"

## Hard Constraints
1. Never store stream/download URLs in the database — they expire
2. All downloads to `filesDir/music/{track_id}.mp3` — never external storage
3. Never use yt-dlp or unofficial stream extraction
4. Debounce streamed seeks to finger-lift only
5. `ClippingMediaSource` takes microseconds: `ms * 1000L`
6. Remove AVPlayer boundary observers before replacing items
7. WorkManager must clean up partial files on failure
8. Jamendo attribution required on every track display
