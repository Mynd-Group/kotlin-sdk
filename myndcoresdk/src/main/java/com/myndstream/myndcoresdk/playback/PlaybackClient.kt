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
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import models.PlaylistCompleted
import models.PlaylistStarted
import models.PlaylistWithSongs
import models.Song
import models.TrackCompleted
import models.TrackProgress
import models.TrackStarted

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
            events.collect { event ->
                when (event) {
                    is AudioPlayerEvent.PlaylistQueued -> {
                        val playlistId = event.playlist.playlist.id
                        val sessionId = sessionManager.getSessionId()
                        val result =
                                trackingClient.trackEvent(
                                        PlaylistStarted(
                                                playlistId = playlistId,
                                                sessionId = sessionId,
                                                playlistSessionId = playlistSessionId
                                        )
                                )
                        if (result.isFailure) {
                            println("EventTracking error: ${result.exceptionOrNull()?.message}")
                        }
                    }
                    is AudioPlayerEvent.PlaylistCompleted -> {
                        val sessionId = sessionManager.getSessionId()
                        val playlistId = currentPlaylist?.playlist?.id ?: ""
                        val result =
                                trackingClient.trackEvent(
                                        PlaylistCompleted(
                                                playlistId = playlistId,
                                                sessionId = sessionId,
                                                playlistSessionId = playlistSessionId
                                        )
                                )
                        if (result.isFailure) {
                            println("EventTracking error: ${result.exceptionOrNull()?.message}")
                        }
                    }
                    else -> {}
                }
            }
        }

        scope.launch {
            royaltyEvents.collect { revent ->
                when (revent) {
                    is RoyaltyTrackingEvent.TrackStarted -> {
                        val sessionId = sessionManager.getSessionId()
                        val result =
                                trackingClient.trackEvent(
                                        TrackStarted(
                                                songId = revent.song.id,
                                                sessionId = sessionId,
                                                playlistSessionId = playlistSessionId
                                        )
                                )
                        if (result.isFailure) {
                            println("EventTracking error: ${result.exceptionOrNull()?.message}")
                        }
                    }
                    is RoyaltyTrackingEvent.TrackProgress -> {
                        val sessionId = sessionManager.getSessionId()
                        val result =
                                trackingClient.trackEvent(
                                        TrackProgress(
                                                songId = revent.song.id,
                                                progress = revent.progress,
                                                sessionId = sessionId,
                                                playlistSessionId = playlistSessionId
                                        )
                                )
                        if (result.isFailure) {
                            println("EventTracking error: ${result.exceptionOrNull()?.message}")
                        }
                    }
                    is RoyaltyTrackingEvent.TrackFinished -> {
                        val sessionId = sessionManager.getSessionId()
                        val result =
                                trackingClient.trackEvent(
                                        TrackCompleted(
                                                songId = revent.song.id,
                                                sessionId = sessionId,
                                                playlistSessionId = playlistSessionId
                                        )
                                )
                        if (result.isFailure) {
                            println("EventTracking error: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }
            }
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
