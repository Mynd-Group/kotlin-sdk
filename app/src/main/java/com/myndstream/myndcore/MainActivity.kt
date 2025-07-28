package com.myndstream.myndcore

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myndstream.myndcoresdk.playback.IAudioClient
import com.myndstream.myndcoresdk.playback.PlaybackClient
import com.myndstream.myndcoresdk.playback.AudioPlayerEvent
import com.myndstream.myndcoresdk.playback.PlaybackState
import kotlinx.coroutines.launch
import models.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println("🚀 MyndCore app started!")

        testAudioPlayer()
    }

    private fun testAudioPlayer() {
        try {
            val audioClient: IAudioClient = PlaybackClient(this)
            println("✅ Audio client created successfully!")

            lifecycleScope.launch {
                // Start observing player events
                launch {
                    audioClient.events.collect { event ->
                        when (event) {
                            is AudioPlayerEvent.PlaylistQueued -> {
                                println("🎵 Playlist queued: ${event.playlist.playlist.name} with ${event.playlist.songs.size} songs")
                            }
                            is AudioPlayerEvent.StateChanged -> {
                                when (event.state) {
                                    is PlaybackState.Idle -> println("⏸️ Player state: Idle")
                                    is PlaybackState.Playing -> println("▶️ Player state: Playing - ${(event.state as PlaybackState.Playing).song.name} (track ${(event.state as PlaybackState.Playing).index + 1})")
                                    is PlaybackState.Paused -> println("⏸️ Player state: Paused - ${(event.state as PlaybackState.Paused).song.name} (track ${(event.state as PlaybackState.Paused).index + 1})")
                                    is PlaybackState.Stopped -> println("⏹️ Player state: Stopped")
                                }
                            }
                            is AudioPlayerEvent.ProgressUpdated -> {
                                val progress = event.progress
                                println("⏱️ Progress: Track ${progress.trackIndex + 1} - ${progress.trackCurrentTime.toInt()}s/${progress.trackDuration.toInt()}s (${(progress.trackProgress * 100).toInt()}%)")
                            }
                            is AudioPlayerEvent.PlaylistCompleted -> {
                                println("🏁 Playlist completed!")
                            }
                            is AudioPlayerEvent.SongNetworkStalled -> {
                                println("⚠️ Network stalled")
                            }
                            is AudioPlayerEvent.SongNetworkFailure -> {
                                println("❌ Network failure: ${event.error.message}")
                            }
                            is AudioPlayerEvent.ErrorOccurred -> {
                                println("❌ Player error: ${event.error.message}")
                            }
                            is AudioPlayerEvent.VolumeChanged -> {
                                println("🔊 Volume changed: ${event.volume}")
                            }
                        }
                    }
                }

                // Display initial state
                println("🎮 Initial player state: ${audioClient.state}")
                println("🎵 Is playing: ${audioClient.isPlaying}")
                println("🔊 Volume: ${audioClient.volume}")

                val songs = listOf(
                    Song(
                        id = "1", name = "Test Song 1", image = SongImage(
                            id = "img1", url = "https://example.com/song1.jpg"
                        ), audio = Audio(
                            hls = SongHLS(
                                id = "hls1",
                                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                                durationInSeconds = 497,
                                urlExpiresAtISO = "2024-12-31T23:59:59Z"
                            ), mp3 = SongMP3(
                                id = "mp3_1",
                                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                                durationInSeconds = 497,
                                urlExpiresAtISO = "2024-12-31T23:59:59Z"
                            )
                        ), artists = listOf(
                            Artist(id = "artist1", name = "Test Artist 1")
                        )
                    ), Song(
                        id = "2", name = "Test Song 2", image = SongImage(
                            id = "img2", url = "https://example.com/song2.jpg"
                        ), audio = Audio(
                            hls = SongHLS(
                                id = "hls2",
                                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                                durationInSeconds = 458,
                                urlExpiresAtISO = "2024-12-31T23:59:59Z"
                            ), mp3 = SongMP3(
                                id = "mp3_2",
                                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                                durationInSeconds = 458,
                                urlExpiresAtISO = "2024-12-31T23:59:59Z"
                            )
                        ), artists = listOf(
                            Artist(id = "artist2", name = "Test Artist 2")
                        )
                    )
                )

                val playlist = Playlist(
                    id = "test-playlist",
                    name = "My Test Playlist",
                    image = PlaylistImage(
                        id = "playlist-img", url = "https://example.com/playlist.jpg"
                    ),
                    description = "A test playlist for audio player",
                    instrumentation = "Electronic",
                    genre = "Test",
                    bpm = 120
                )

                val playlistWithSongs = PlaylistWithSongs(
                    playlist = playlist, songs = songs
                )

                println("🚀 Starting playbook...")
                audioClient.play(playlist = playlistWithSongs)

                // Display state after play command
                println("🎮 Player state after play: ${audioClient.state}")
                println("🎵 Is playing after play: ${audioClient.isPlaying}")
            }

        } catch (e: Exception) {
            println("❌ Error creating audio client: ${e.message}")
            e.printStackTrace()
        }
    }
}