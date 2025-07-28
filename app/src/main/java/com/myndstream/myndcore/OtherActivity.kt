package com.myndstream.myndcore

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myndstream.myndcoresdk.playback.AudioPlayerEvent
import com.myndstream.myndcoresdk.playback.PlaybackState
import com.myndstream.myndcoresdk.clients.HttpClient
import com.myndstream.myndcoresdk.public.MyndSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import models.AuthPayload

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class AuthRequest(val providerUserId: String)

suspend fun authFn(): AuthPayload {
    val client = HttpClient()
    val json = Json { ignoreUnknownKeys = true }
    val request = AuthRequest("test-user-id")
    val serialized = json.encodeToString(request)
    val result = client.post(Config.baseApiUrl+"/integration-user/authenticate", serialized ,mapOf("x-api-key" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpbnRlZ3JhdGlvbkFwaUtleUlkIjoiZTBmMzQ1YmEtYWRiYi00OWU4LWE2NjMtZjkxNzIzYTc0OGQxIiwiYWNjb3VudElkIjoiMTBlOTlmMzAtNDlkNy00ZDljLWFiMWEtMmU2MjYxMTk2YTRiIiwiaWF0IjoxNzUyNTk1NDM4fQ.t--RVG-3F3fhXgKHyrZRAKlpmUvM-Lwu_svcSCN9pHU"))
    return json.decodeFromString<AuthPayload>(result)
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
            // 1) Initialize the SDK
            sdk = MyndSDK.create(authFunction = { authFn() }, ctx = this@OtherActivity)

            // 2) Start collecting player events
            launch {
                sdk.player.royaltyEvents.collect { event ->
                    println("Royalty Event: "+event)
                }
            }


            launch {
                sdk.player.events.collect { event ->
                    when (event) {
                        is AudioPlayerEvent.PlaylistQueued -> {
                            Log.i(TAG, "🎵 Playlist queued: ${event.playlist.playlist.name} (${event.playlist.songs.size} songs)")
                        }
                        is AudioPlayerEvent.StateChanged -> {
                            when (val s = event.state) {
                                is PlaybackState.Idle   -> Log.i(TAG, "⏸️ Idle")
                                is PlaybackState.Playing-> Log.i(TAG, "▶️ Playing: ${s.song.name} (track ${s.index + 1})")
                                is PlaybackState.Paused -> Log.i(TAG, "⏸️ Paused: ${s.song.name} (track ${s.index + 1})")
                                is PlaybackState.Stopped-> Log.i(TAG, "⏹️ Stopped")
                            }
                        }
                        is AudioPlayerEvent.ProgressUpdated -> {
                            val p = event.progress
                            println(p)
                            Log.d(TAG, "Playlist ⏱️ ${p.trackIndex + 1}: ${p.playlistCurrentTime.toInt()}/${p.playlistDuration.toInt()}s")
                        }
                        is AudioPlayerEvent.PlaylistCompleted -> Log.i(TAG, "🏁 Playlist completed")
                        is AudioPlayerEvent.SongNetworkStalled-> Log.w(TAG, "⚠️ Network stalled")
                        is AudioPlayerEvent.SongNetworkFailure-> Log.e(TAG, "❌ Network failure: ${event.error.message}")
                        is AudioPlayerEvent.ErrorOccurred      -> Log.e(TAG, "❌ Player error: ${event.error.message}")
                        is AudioPlayerEvent.VolumeChanged      -> Log.i(TAG, "🔊 Volume: ${event.volume}")
                    }
                }
            }

            // 3) Playlist‐switcher loop
            launch {
                try {
                    // fetch & filter once
                    val categories = sdk.catalogueClient.getCategories()
                    if (categories.isEmpty()) {
                        Log.w(TAG, "No categories found.")
                        return@launch
                    }
                    val all = sdk.catalogueClient.getPlaylists(categories.first().id)
                    val filtered = all.filter { it.name.contains("12") }
                    if (filtered.isEmpty()) {
                        Log.w(TAG, "No playlists match “12”.")
                        return@launch
                    }

                    Log.i(TAG, "Starting coroutine looper for playlists with “12”…")
                    var idx = 0

                    // loop until the Activity is destroyed
                    while (isActive) {
                        val summary = filtered[idx % filtered.size]
                        idx++

                        // network calls are suspend, so off‐main is fine—but
                        // if you want to ensure not blocking UI, you could wrap in withContext(Dispatchers.IO)
                        try {
                            val full = withContext(Dispatchers.IO) {
                                sdk.catalogueClient.getPlaylist(summary.id)
                            }
                            sdk.player.play(full)
                            Log.i(TAG, "Now playing: ${full.playlist.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed loading ${summary.id}", e)
                        }

                        // wait 5 seconds before next
                        delay(10_000)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Looper setup failed", e)
                }
            }
        }
    }
}