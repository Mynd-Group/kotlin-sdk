package com.myndstream.myndcoresdk.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.Flow
import models.PlaylistWithSongs
import models.Song

@UnstableApi
class MyndAudioClient(private val ctx: Context, private val enableBackgroundService: Boolean = true): IAudioClient {
    private val player = PlayerWrapper.create(ctx)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        if (enableBackgroundService) {
            // Set the player instance for the service
            MyndPlaybackService.setPlayer(player.exoPlayer)
        }
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
            // Start the service
            val intent = Intent(ctx, MyndPlaybackService::class.java)
            ctx.startService(intent)

            // Connect to the MediaSession
            connectToSession()
        }

        // Load and play
        player.loadPlaylist(playlist)
        player.play()
    }

    private fun connectToSession() {
        println("Connecting to session")
        val sessionToken = SessionToken(ctx, ComponentName(ctx, MyndPlaybackService::class.java))
        controllerFuture = MediaController.Builder(ctx, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())
    }

    override fun pause() = player.pause()
    override fun resume() = player.play()

    override suspend fun stop() {
        player.stop()
        if (enableBackgroundService) {
            val intent = Intent(ctx, MyndPlaybackService::class.java)
            ctx.stopService(intent)
        }
    }

    override fun setRepeatMode(mode: RepeatMode) = player.setRepeatMode(mode)
    override fun setVolume(value: Float) {
        player.volume = value
    }

    fun release() {
        player.release()
        if (enableBackgroundService) {
            MediaController.releaseFuture(controllerFuture ?: return)
            MyndPlaybackService.clearPlayer()
        }
    }

    companion object {
        fun create(ctx: Context, enableBackgroundService: Boolean = true): MyndAudioClient {
            return MyndAudioClient(ctx, enableBackgroundService)
        }
    }
}