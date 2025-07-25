package com.myndstream.myndcoresdk.exp

import android.content.Context
import android.content.Intent
import com.myndstream.myndcoresdk.audio.AudioPlayerEvent
import com.myndstream.myndcoresdk.audio.IAudioClient
import com.myndstream.myndcoresdk.audio.PlaybackProgress
import com.myndstream.myndcoresdk.audio.PlaybackState
import com.myndstream.myndcoresdk.audio.RepeatMode
import com.myndstream.myndcoresdk.audio.RoyaltyTrackingEvent
import kotlinx.coroutines.flow.Flow
import models.PlaylistWithSongs
import models.Song

class MyndAudioClient(val ctx: Context): IAudioClient {
    private val player = PlayerWrapper.create(ctx)
    private val backgroundHandler = BackgroundHandler(ctx, player.exoPlayer)

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
        val intent = Intent(ctx, BackgroundHandler::class.java)
        ctx.startService(intent)
        player.loadPlaylist(playlist)
        player.play()
    }

    override fun pause() = player.pause()
    override fun resume() = player.play()
    override suspend fun stop()  {
        player.stop()
        val intent = Intent(ctx, BackgroundHandler::class.java)
        ctx.stopService(intent)

    }
    override fun setRepeatMode(mode: RepeatMode) = player.setRepeatMode(mode)
    override fun setVolume(value: Float) {
        player.volume = value
    }

    fun release() {
        player.release()
        backgroundHandler.release()
    }


    companion object {
        fun create(ctx: Context): MyndAudioClient {
            return MyndAudioClient(ctx)

        }
    }
}