# MyndSDK

> ⚠️ **EARLY BETA SOFTWARE** ⚠️
> 
> **This SDK is currently in early beta development. Expect bugs, breaking changes, and incomplete features.**
> 
> - 🔄 **Breaking Changes**: API may change significantly between versions
> - 📝 **Incomplete Documentation**: Some features may be undocumented
> - 🧪 **Testing Required**: Thoroughly test all functionality in your use case
> - 💬 **Feedback Welcome**: Please report issues and provide feedback
> 

A comprehensive Android SDK for music streaming and playback, providing seamless integration with the Myndstream platform.

## Overview

MyndSDK enables Android applications to access curated music content through a robust catalogue system and high-quality audio playback engine. The SDK handles authentication, content discovery, and media playback with built-in background service support.

## Core Features

### 🎵 Music Catalogue
- Browse organized music categories
- Access curated playlists with metadata (genre, BPM, instrumentation)
- Retrieve individual songs with artist information
- High-resolution artwork support

### 🎧 Audio Playback
- ExoPlayer-based streaming engine
- Background playback with MediaSession integration
- Support for HLS and MP3 formats
- Volume control and repeat modes
- Real-time progress tracking
- Royalty tracking events

### 🔐 Authentication
- Token-based authentication with automatic refresh
- Thread-safe token management
- Configurable HTTP client with retry logic

## Quick Start

### Installation

The SDK is distributed via JitPack. Add the JitPack repository and dependency to your `build.gradle.kts`:

```kotlin
// Project-level build.gradle.kts
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// Module-level build.gradle.kts
dependencies {
    implementation("com.github.myndstream:MyndCore:1.0.0")
}
```

### Basic Usage

```kotlin
// Initialize SDK
val sdk = MyndSDK.getOrCreate(
    refreshToken = "your_refresh_token",
    ctx = applicationContext
)

// Browse catalogue
val categories = sdk.catalogueClient.getCategories().getOrNull()
val playlists = sdk.catalogueClient.getPlaylists(categoryId = null).getOrNull()

// Play music
val playlistWithSongs = sdk.catalogueClient.getPlaylist("playlist_id").getOrNull()
playlistWithSongs?.let { playlist ->
    sdk.player.play(playlist)
}

// Control playback
sdk.player.pause()
sdk.player.resume()
sdk.player.setVolume(0.8f)

// Monitor playback events
sdk.player.events.collect { event ->
    when (event) {
        is AudioPlayerEvent.StateChanged -> handleStateChange(event.state)
        is AudioPlayerEvent.ProgressUpdated -> updateUI(event.progress)
        is AudioPlayerEvent.ErrorOccurred -> handleError(event.error)
    }
}
```

## API Reference

### MyndSDK

Main entry point providing access to catalogue and playback functionality.

```kotlin
interface IMyndSDK {
    val catalogueClient: ICatalogueClient
    val player: IAudioClient
}
```

**Methods:**
- `getOrCreate(refreshToken: String, ctx: Context): MyndSDK` - Singleton initialization
- `release()` - Clean up resources

### Catalogue Client

```kotlin
interface ICatalogueClient {
    suspend fun getCategories(): Result<List<Category>>
    suspend fun getCategory(categoryId: String): Result<Category>
    suspend fun getPlaylists(categoryId: String?): Result<List<Playlist>>
    suspend fun getPlaylist(playlistId: String): Result<PlaylistWithSongs>
}
```

### Audio Player

```kotlin
interface IAudioClient {
    val events: Flow<AudioPlayerEvent>
    val royaltyEvents: Flow<RoyaltyTrackingEvent>
    val state: PlaybackState
    val progress: PlaybackProgress
    val isPlaying: Boolean
    val currentSong: Song?
    val currentPlaylist: PlaylistWithSongs?
    val volume: Float

    suspend fun play(playlist: PlaylistWithSongs)
    fun pause()
    fun resume()
    suspend fun stop()
    fun setRepeatMode(mode: RepeatMode)
    fun setVolume(value: Float)
}
```

## Data Models

### Song
```kotlin
data class Song(
    val id: String,
    val name: String,
    val image: SongImage?,
    val audio: Audio,           // HLS and MP3 URLs
    val artists: List<Artist>
)
```

### Playlist
```kotlin
data class Playlist(
    val id: String,
    val name: String,
    val image: PlaylistImage?,
    val description: String?,
    val instrumentation: String?,
    val genre: String?,
    val bpm: Int?
)
```

### Category
```kotlin
data class Category(
    val id: String,
    val name: String,
    val image: CategoryImage?
)
```

## Playback Events

### Audio Player Events
- `PlaylistQueued` - New playlist loaded
- `StateChanged` - Playback state transitions
- `ProgressUpdated` - Position updates
- `PlaylistCompleted` - End of playlist
- `SongNetworkStalled` - Network buffering
- `SongNetworkFailure` - Stream error
- `ErrorOccurred` - General errors
- `VolumeChanged` - Volume adjustments

### Royalty Tracking Events
- `TrackStarted` - Song playback begins
- `TrackProgress` - Playback progress milestones
- `TrackFinished` - Song completion

## Requirements

- **Minimum SDK:** API 24 (Android 7.0)
- **Target SDK:** API 35
- **Kotlin:** 1.9.10+

## Dependencies

- AndroidX Media3 (ExoPlayer)
- Kotlinx Serialization
- Kotlinx Coroutines

## Thread Safety

The SDK is designed for concurrent access:
- Authentication tokens are managed with mutex synchronization
- Playback operations are thread-safe
- All suspend functions are safe for concurrent execution

## Resource Management

Always call `MyndSDK.release()` when done to properly clean up:
- Releases ExoPlayer resources
- Stops background services
- Clears authentication state

## Error Handling

All catalogue operations return `Result<T>` for explicit error handling. Playback errors are delivered through the events flow.

## License

Proprietary - Myndstream Platform