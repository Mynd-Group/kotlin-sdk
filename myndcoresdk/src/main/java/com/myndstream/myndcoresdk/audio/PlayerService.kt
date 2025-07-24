package com.myndstream.myndcoresdk.audio

import MyndAudioPlayer
import android.content.Context
import kotlinx.coroutines.flow.Flow
import models.PlaylistWithSongs
import models.Song

class MyndAudioClient(context: Context) : IAudioClient {
    private val player = MyndAudioPlayer(context.applicationContext)

    // Implement all IAudioClient interface methods
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

    override suspend fun play(playlist: PlaylistWithSongs) {
        player.loadPlaylist(playlist)
        player.play()
    }

    override fun pause() = player.pause()
    override fun resume() = player.play()
    override suspend fun stop() = player.stop()
    override fun setRepeatMode(mode: RepeatMode) = player.setRepeatMode(mode)
    override fun setVolume(value: Float) {
        player.volume = value
    }
}
