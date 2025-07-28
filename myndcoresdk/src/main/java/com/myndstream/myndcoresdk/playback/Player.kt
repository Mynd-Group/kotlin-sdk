package com.myndstream.myndcoresdk.playback

import kotlinx.coroutines.flow.Flow
import models.PlaylistWithSongs
import models.Song

interface IAudioClient {
    val events: Flow<AudioPlayerEvent>
    val royaltyEvents: Flow<RoyaltyTrackingEvent>
    val state: PlaybackState
    val progress: PlaybackProgress

    /// Convenience flag
    val isPlaying: Boolean

    /// Current context (optional helpers)
    val currentSong: Song?
    val currentPlaylist: PlaylistWithSongs?

    suspend fun play(playlist: PlaylistWithSongs)
    fun pause()
    fun resume()
    suspend fun stop()
    fun setRepeatMode(mode: RepeatMode)

    // Volume control
    val volume: Float
    fun setVolume(value: Float)
}

// Supporting enums and data classes
enum class RepeatMode {
    NONE,
    PLAYLIST
}

sealed class AudioPlayerEvent {
    data class PlaylistQueued(val playlist: PlaylistWithSongs) : AudioPlayerEvent()
    data class StateChanged(val state: PlaybackState) : AudioPlayerEvent()
    data class ProgressUpdated(val progress: PlaybackProgress) : AudioPlayerEvent()
    object PlaylistCompleted : AudioPlayerEvent()
    object SongNetworkStalled : AudioPlayerEvent()
    data class SongNetworkFailure(val error: Throwable) : AudioPlayerEvent()
    data class ErrorOccurred(val error: Throwable) : AudioPlayerEvent()
    data class VolumeChanged(val volume: Float) : AudioPlayerEvent()
}

sealed class RoyaltyTrackingEvent {
    data class TrackStarted(val song: Song) : RoyaltyTrackingEvent()
    data class TrackProgress(val song: Song, val progress: Double) : RoyaltyTrackingEvent()
    data class TrackFinished(val song: Song) : RoyaltyTrackingEvent()
}

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Playing(val song: Song, val index: Int) : PlaybackState()
    data class Paused(val song: Song, val index: Int) : PlaybackState()
    object Stopped : PlaybackState()
}

data class PlaybackProgress(
    // Track-level progress
    val trackCurrentTime: Double,      // Current time in track (seconds)
    val trackDuration: Double,         // Track duration (seconds)
    val trackIndex: Int,               // Current track index

    // Playlist-level progress
    val playlistCurrentTime: Double,   // Current time in playlist (seconds)
    val playlistDuration: Double       // Total playlist duration (seconds)
) {
    // Computed properties for track
    val trackProgress: Double
        get() = if (trackDuration > 0) trackCurrentTime / trackDuration else 0.0

    // Computed properties for playlist
    val playlistProgress: Double
        get() = if (playlistDuration > 0) playlistCurrentTime / playlistDuration else 0.0
}

sealed class AudioError : Exception() {
    object EmptyPlaylist : AudioError() {
        override val message: String = "The playlist is empty"
    }

    data class InvalidURL(val url: String) : AudioError() {
        override val message: String = "Invalid URL: $url"
    }
}
