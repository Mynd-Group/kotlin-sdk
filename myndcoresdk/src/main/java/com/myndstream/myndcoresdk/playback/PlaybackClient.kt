package com.myndstream.myndcoresdk.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.myndstream.myndcoresdk.clients.ITrackingClient
import com.myndstream.myndcoresdk.core.utils.ListeningSessionManager
import com.myndstream.myndcoresdk.models.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

@UnstableApi
class PlaybackClient(
        private val ctx: Context,
        private val trackingClient: ITrackingClient,
        private val sessionManager: ListeningSessionManager,
        private val enableBackgroundService: Boolean = true
) : IAudioClient {
    private val player = PlaybackWrapper.create(ctx)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playlistSessionId: String = UUID.randomUUID().toString()

    init {
        if (enableBackgroundService) {
            // Set the player instance for the service
            AndroidPlaybackService.setPlayer(player.exoPlayer)
        }
        enableEventTracking()
    }

    override val events: Flow<AudioPlayerEvent> = player.events
    override val state: PlaybackState
        get() = player.state
    override val progress: PlaybackProgress
        get() = player.progress
    override val isPlaying: Boolean
        get() = player.isPlaying
    override val currentSong: Song?
        get() = player.currentSong
    override val currentPlaylist: PlaylistWithSongs?
        get() = player.currentPlaylist
    override val volume: Float
        get() = player.volume
    override val royaltyEvents: Flow<RoyaltyTrackingEvent> = player.royaltyEvents

    override suspend fun play(playlist: PlaylistWithSongs) {
        if (enableBackgroundService) {
            connectToSession()
        }

        playlistSessionId = UUID.randomUUID().toString()

        // Load and play
        player.loadPlaylist(playlist)
        player.play()
    }

    private fun connectToSession() {
        if (controllerFuture != null) {
            println("Controller already created, skipping create new session")
            return
        }

        println("Connecting to session")
        val sessionToken = SessionToken(ctx, ComponentName(ctx, AndroidPlaybackService::class.java))
        controllerFuture = MediaController.Builder(ctx, sessionToken).buildAsync()
        controllerFuture?.addListener(
                { mediaController = controllerFuture?.get() },
                MoreExecutors.directExecutor()
        )
    }

    override fun pause() = player.pause()
    override fun resume() = player.play()

    override suspend fun stop() {
        player.stop()
    }

    override fun setRepeatMode(mode: RepeatMode) = player.setRepeatMode(mode)
    override fun setVolume(value: Float) {
        player.volume = value
    }

    private fun enableEventTracking() {
        scope.launch {
            try {
                println("EventTracking: subscribing to player events")
                player.events.collect { event ->
                    when (event) {
                        is AudioPlayerEvent.PlaylistQueued -> handlePlaylistStarted(event.playlist)
                        is AudioPlayerEvent.PlaylistCompleted -> handlePlaylistCompleted()
                        else -> {}
                    }
                }
            } catch (t: Throwable) {
                println("EventTracking: player events collector failed: ${t.message}")
            }
        }

        scope.launch {
            try {
                println("EventTracking: subscribing to royalty events")
                player.royaltyEvents.collect { event ->
                    when (event) {
                        is RoyaltyTrackingEvent.TrackStarted -> handleTrackStarted(event.song)
                        is RoyaltyTrackingEvent.TrackProgress ->
                                handleTrackProgress(event.song, event.progress)
                        is RoyaltyTrackingEvent.TrackFinished -> handleTrackCompleted(event.song)
                    }
                }
            } catch (t: Throwable) {
                println("EventTracking: royalty events collector failed: ${t.message}")
            }
        }
    }

    private suspend fun handlePlaylistStarted(playlist: PlaylistWithSongs) {
        trackEvent {
            PlaylistStarted(
                    playlistId = playlist.playlist.id,
                    playlistName = playlist.playlist.name,
                    playlistGenre = playlist.playlist.genre ?: "",
                    playlistBPM = playlist.playlist.bpm ?: 0,
                    playlistInstrumentation = playlist.playlist.instrumentation ?: "",
                    playlistDuration = playlist.songs.sumOf { it.audio.mp3.durationInSeconds },
                    sessionId = sessionManager.getSessionId(),
                    playlistSessionId = playlistSessionId
            )
        }
    }

    private suspend fun handlePlaylistCompleted() {
        val playlist = currentPlaylist ?: return
        trackEvent {
            PlaylistCompleted(
                    playlistId = playlist.playlist.id,
                    playlistName = playlist.playlist.name,
                    playlistGenre = playlist.playlist.genre ?: "",
                    playlistBPM = playlist.playlist.bpm ?: 0,
                    playlistInstrumentation = playlist.playlist.instrumentation ?: "",
                    playlistDuration = playlist.songs.sumOf { it.audio.mp3.durationInSeconds },
                    sessionId = sessionManager.getSessionId(),
                    playlistSessionId = playlistSessionId
            )
        }
    }

    private var songSessionId: String = UUID.randomUUID().toString()

    private suspend fun handleTrackStarted(song: Song) {
        val playlist = currentPlaylist ?: return
        songSessionId = UUID.randomUUID().toString()
        trackEvent {
            TrackStarted(
                    songId = song.id,
                    songName = song.name,
                    songDuration = song.audio.mp3.durationInSeconds,
                    playlistId = playlist.playlist.id,
                    playlistName = playlist.playlist.name,
                    songSessionId = songSessionId,
                    sessionId = sessionManager.getSessionId(),
                    playlistSessionId = playlistSessionId
            )
        }
    }

    private suspend fun handleTrackProgress(song: Song, progress: Double) {
        val playlist = currentPlaylist ?: return
        trackEvent {
            TrackProgress(
                    songId = song.id,
                    songName = song.name,
                    songDuration = song.audio.mp3.durationInSeconds,
                    playlistId = playlist.playlist.id,
                    playlistName = playlist.playlist.name,
                    songSessionId = songSessionId,
                    progress = progress,
                    sessionId = sessionManager.getSessionId(),
                    playlistSessionId = playlistSessionId
            )
        }
    }

    private suspend fun handleTrackCompleted(song: Song) {
        val playlist = currentPlaylist ?: return
        trackEvent {
            TrackCompleted(
                    songId = song.id,
                    songName = song.name,
                    songDuration = song.audio.mp3.durationInSeconds,
                    playlistId = playlist.playlist.id,
                    playlistName = playlist.playlist.name,
                    songSessionId = songSessionId,
                    sessionId = sessionManager.getSessionId(),
                    playlistSessionId = playlistSessionId
            )
        }
    }

    private suspend inline fun trackEvent(eventBuilder: () -> TrackingEvent) {
        val result = trackingClient.trackEvent(eventBuilder())
        if (result.isFailure) {
            println("EventTracking error: ${result.exceptionOrNull()?.message}")
        }
    }

    fun release() {
        scope.cancel()
        player.release()
        if (enableBackgroundService) {
            MediaController.releaseFuture(controllerFuture ?: return)
            AndroidPlaybackService.clearPlayer()
        }
    }

    companion object {
        fun create(
                ctx: Context,
                trackingClient: ITrackingClient,
                sessionManager: ListeningSessionManager,
                enableBackgroundService: Boolean = true
        ): PlaybackClient {
            return PlaybackClient(ctx, trackingClient, sessionManager, enableBackgroundService)
        }
    }
}
