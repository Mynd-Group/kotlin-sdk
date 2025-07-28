package com.myndstream.myndcoresdk.playback

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MyndPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    companion object {
        private var exoPlayerInstance: ExoPlayer? = null

        fun setPlayer(player: ExoPlayer) {
            exoPlayerInstance = player
        }

        fun clearPlayer() {
            exoPlayerInstance = null
        }
    }

    override fun onCreate() {
        super.onCreate()

        val player = exoPlayerInstance ?: return

        // Create MediaSession with proper configuration
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(CustomMediaSessionCallback())
            .build()
    }

    // This is called when a MediaController wants to connect
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            // If the player is paused, stop the service
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        clearPlayer()
        super.onDestroy()
    }

    // Custom callback to restrict available commands
    inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Build session commands from scratch with only what we need
            val sessionCommands = SessionCommands.Builder()
                .add(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
                .build()

            // For player commands, create a custom set with only play/pause
            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_SET_PLAYLIST_METADATA)
                .add(Player.COMMAND_GET_TRACKS)
                .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
                .add(Player.COMMAND_SET_VOLUME)
                .add(Player.COMMAND_GET_VOLUME)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            // Handle any custom commands if needed
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}