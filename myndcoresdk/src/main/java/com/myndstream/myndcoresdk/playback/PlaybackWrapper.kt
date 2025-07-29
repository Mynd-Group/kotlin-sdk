package com.myndstream.myndcoresdk.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.WAKE_MODE_NETWORK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import models.*

class PlaybackWrapper private constructor(
    public val exoPlayer: ExoPlayer
) {
    var currentPlaylist: PlaylistWithSongs? = null
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _events = MutableSharedFlow<AudioPlayerEvent>()
    private val _royaltyEvents = MutableSharedFlow<RoyaltyTrackingEvent>()

    val royaltyEvents: SharedFlow<RoyaltyTrackingEvent> = _royaltyEvents.asSharedFlow()
    val events: Flow<AudioPlayerEvent> = _events.asSharedFlow()

    private var _state: PlaybackState = PlaybackState.Idle
    val state: PlaybackState
        get() = _state

    private var _progress: PlaybackProgress = PlaybackProgress(0.0, 0.0, 0, 0.0, 0.0)
    val progress: PlaybackProgress
        get() = _progress

    val isPlaying: Boolean
        get() = exoPlayer.isPlaying

    val currentSong: Song?
        get() = currentPlaylist?.songs?.getOrNull(exoPlayer.currentMediaItemIndex)

    var volume: Float
        get() = exoPlayer.volume
        set(value) {
            val newVolume = value.coerceIn(0f, 1f)
            exoPlayer.volume = newVolume
            scope.launch {
                _events.emit(AudioPlayerEvent.VolumeChanged(newVolume))
            }
        }

    private var progressJob: Job? = null

    init {
        setupPlayerListener()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val previousIdx = exoPlayer.previousMediaItemIndex
                val currentIdx = exoPlayer.currentMediaItemIndex

                println("index: $currentIdx $previousIdx")

                if (previousIdx != -1) {
                    scope.launch {
                        currentPlaylist?.songs?.getOrNull(previousIdx)?.let { song ->
                            _royaltyEvents.emit(RoyaltyTrackingEvent.TrackFinished(song))
                        }
                    }
                }

                if (currentIdx != -1) {
                    scope.launch {
                        currentPlaylist?.songs?.getOrNull(currentIdx)?.let { song ->
                            _royaltyEvents.emit(RoyaltyTrackingEvent.TrackStarted(song))
                        }
                    }
                    updateProgress()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) {
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                scope.launch {
                    _events.emit(AudioPlayerEvent.ErrorOccurred(error))
                }
            }
        })
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive && isPlaying) {
                updateProgress()
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    suspend fun loadPlaylist(playlist: PlaylistWithSongs) {
        if (playlist.songs.isEmpty()) {
            _events.emit(AudioPlayerEvent.ErrorOccurred(AudioError.EmptyPlaylist))
            return
        }

        currentPlaylist = playlist
        
        // Keep individual tracks but show playlist-level metadata
        // The progress will still be track-based, but the display shows playlist context
        val mediaItems = playlist.songs.mapIndexed { index, song ->
            MediaItem.Builder()
                .setUri(song.audio.mp3.url)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(playlist.playlist.name)
                        .setArtist("Track ${index + 1} of ${playlist.songs.size}")
                        .setAlbumTitle(playlist.playlist.description ?: "Playlist")
                        .setGenre(playlist.playlist.genre)
                        .apply {
                            playlist.playlist.image?.url?.let { imageUrl ->
                                setArtworkUri(android.net.Uri.parse(imageUrl))
                            }
                        }
                        .build()
                )
                .build()
        }

        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.prepare()

        _events.emit(AudioPlayerEvent.PlaylistQueued(playlist))
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    suspend fun stop() {
        exoPlayer.stop()
        _state = PlaybackState.Stopped
        _events.emit(AudioPlayerEvent.StateChanged(_state))
    }

    fun setRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            RepeatMode.PLAYLIST -> Player.REPEAT_MODE_ALL
        }
    }

    private fun updateState() {
        val currentIndex = exoPlayer.currentMediaItemIndex
        val song = currentPlaylist?.songs?.getOrNull(currentIndex)

        _state = when {
            song == null -> PlaybackState.Idle
            isPlaying -> PlaybackState.Playing(song, currentIndex)
            else -> PlaybackState.Paused(song, currentIndex)
        }

        scope.launch {
            _events.emit(AudioPlayerEvent.StateChanged(_state))
        }
    }

    private fun updateProgress() {
        val currentPlaylist = this.currentPlaylist ?: return

        val currentIndex = exoPlayer.currentMediaItemIndex
        val trackTime = exoPlayer.currentPosition / 1000.0
        val trackDuration = if (exoPlayer.duration > 0) {
            exoPlayer.duration / 1000.0
        } else {
            0.0
        }

        val (playlistTime, playlistDuration) = calculatePlaylistProgress(
            currentPlaylist,
            currentIndex,
            trackTime
        )

        _progress = PlaybackProgress(
            trackCurrentTime = trackTime,
            trackDuration = trackDuration,
            trackIndex = currentIndex,
            playlistCurrentTime = playlistTime,
            playlistDuration = playlistDuration
        )

        currentSong?.let { song ->
            scope.launch {
                _royaltyEvents.emit(
                    RoyaltyTrackingEvent.TrackProgress(song, _progress.trackProgress)
                )
            }
        }

        scope.launch {
            _events.emit(AudioPlayerEvent.ProgressUpdated(_progress))
        }
    }

    private fun calculatePlaylistProgress(
        playlist: PlaylistWithSongs,
        currentIndex: Int,
        currentTrackTime: Double
    ): Pair<Double, Double> {
        var totalTime = 0.0
        var currentTime = 0.0

        playlist.songs.forEachIndexed { index, song ->
            val duration = song.audio.mp3.durationInSeconds.toDouble()
            totalTime += duration

            when {
                index < currentIndex -> currentTime += duration
                index == currentIndex -> currentTime += currentTrackTime
            }
        }

        return currentTime to totalTime
    }

    fun release() {
        stopProgressUpdates()
        scope.cancel()
        exoPlayer.release()
    }

    companion object {
        fun create(context: Context): PlaybackWrapper {
            val exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                .build()
            exoPlayer.setWakeMode(WAKE_MODE_NETWORK)

            return PlaybackWrapper(exoPlayer)
        }
    }
}