package com.myndstream.myndcore

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myndstream.myndcoresdk.clients.HttpClient
import com.myndstream.myndcoresdk.playback.AudioPlayerEvent
import com.myndstream.myndcoresdk.playback.PlaybackState
import com.myndstream.myndcoresdk.public.MyndSDK
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.AuthPayload

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AuthRequest(val providerUserId: String)

suspend fun getInitialRefreshToken(): String {
    val client = HttpClient()
    val json = Json { ignoreUnknownKeys = true }
    val request = AuthRequest("test-user-id")
    val serialized = json.encodeToString(request)
    val result =
            client.post(
                    Config.baseApiUrl + "/integration-user/authenticate",
                    serialized,
                    mapOf(
                            "x-api-key" to
                                    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlZ3JhdGlvbkFwaUtleUlkIjoiMzMyNzE5NzctOWRhYS00YjJhLWFkODQtYWNlZjU0MzQ3ZmQ3IiwiYWNjb3VudElkIjoiMTBlOTlmMzAtNDlkNy00ZDljLWFiMWEtMmU2MjYxMTk2YTRiIiwiaWF0IjoxNzU1MDA5ODI0fQ.H0eYYVzPJpSvs_ys6OgexgsOaaMVo3tQuEbP0DfSnbw"
                    )
            )
    val authPayload = json.decodeFromString<AuthPayload>(result)
    return authPayload.refreshToken
}

class OtherActivity : AppCompatActivity() {
    private lateinit var sdk: MyndSDK

    companion object {
        private const val TAG = "OtherActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "🚀 MyndCore app started!")

        // everything runs under lifecycleScope so it's auto‐cancelled in onDestroy()
        lifecycleScope.launch {
            // 1) Get initial refresh token and create SDK singleton
            val refreshToken = getInitialRefreshToken()
            sdk = MyndSDK.getOrCreate(refreshToken = refreshToken, ctx = this@OtherActivity)

            // 2) Start collecting player events
            launch {
                sdk.player.royaltyEvents.collect { event -> println("Royalty Event: " + event) }
            }

            launch {
                sdk.player.events.collect { event ->
                    when (event) {
                        is AudioPlayerEvent.PlaylistQueued -> {
                            Log.i(
                                    TAG,
                                    "🎵 Playlist queued: ${event.playlist.playlist.name} (${event.playlist.songs.size} songs)"
                            )
                        }
                        is AudioPlayerEvent.StateChanged -> {
                            when (val s = event.state) {
                                is PlaybackState.Idle -> Log.i(TAG, "⏸️ Idle")
                                is PlaybackState.Playing ->
                                        Log.i(
                                                TAG,
                                                "▶️ Playing: ${s.song.name} (track ${s.index + 1})"
                                        )
                                is PlaybackState.Paused ->
                                        Log.i(
                                                TAG,
                                                "⏸️ Paused: ${s.song.name} (track ${s.index + 1})"
                                        )
                                is PlaybackState.Stopped -> Log.i(TAG, "⏹️ Stopped")
                            }
                        }
                        is AudioPlayerEvent.ProgressUpdated -> {
                            val p = event.progress
                            println(p)
                            Log.d(
                                    TAG,
                                    "Playlist ⏱️ ${p.trackIndex + 1}: ${p.playlistCurrentTime.toInt()}/${p.playlistDuration.toInt()}s"
                            )
                        }
                        is AudioPlayerEvent.PlaylistCompleted -> Log.i(TAG, "🏁 Playlist completed")
                        is AudioPlayerEvent.SongNetworkStalled -> Log.w(TAG, "⚠️ Network stalled")
                        is AudioPlayerEvent.SongNetworkFailure ->
                                Log.e(TAG, "❌ Network failure: ${event.error.message}")
                        is AudioPlayerEvent.ErrorOccurred ->
                                Log.e(TAG, "❌ Player error: ${event.error.message}")
                        is AudioPlayerEvent.VolumeChanged ->
                                Log.i(TAG, "🔊 Volume: ${event.volume}")
                    }
                }
            }

            // 3) Play single playlist
            launch {
                // fetch & filter once
                val categoriesResult = sdk.catalogueClient.getCategories()
                if (categoriesResult.isFailure) {
                    Log.e(
                            TAG,
                            "Failed to fetch categories: ${categoriesResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }
                val categories = categoriesResult.getOrThrow()
                if (categories.isEmpty()) {
                    Log.w(TAG, "No categories found.")
                    return@launch
                }
                val playlistsResult = sdk.catalogueClient.getPlaylists(categories.first().id)
                if (playlistsResult.isFailure) {
                    Log.e(
                            TAG,
                            "Failed to fetch playlists: ${playlistsResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }
                val all = playlistsResult.getOrThrow()
                val filtered = all.filter { it.name.contains("12") }
                if (filtered.isEmpty()) {
                    return@launch
                }

                // Play the first matching playlist
                val summary = filtered.first()
                val playlistResult = sdk.catalogueClient.getPlaylist(summary.id)
                if (playlistResult.isFailure) {
                    Log.e(
                            TAG,
                            "Failed to fetch playlist ${summary.id}: ${playlistResult.exceptionOrNull()?.message}"
                    )
                } else {
                    val full = playlistResult.getOrThrow()
                    sdk.player.play(full)
                    Log.i(TAG, "Now playing: ${full.playlist.name}")
                }
            }
        }
    }
}
