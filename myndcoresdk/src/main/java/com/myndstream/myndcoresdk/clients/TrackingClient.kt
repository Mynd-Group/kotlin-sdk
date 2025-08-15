package com.myndstream.myndcoresdk.clients

import com.myndstream.myndcoresdk.models.PlaylistCompleted
import com.myndstream.myndcoresdk.models.PlaylistStarted
import com.myndstream.myndcoresdk.models.TrackCompleted
import com.myndstream.myndcoresdk.models.TrackProgress
import com.myndstream.myndcoresdk.models.TrackStarted
import com.myndstream.myndcoresdk.models.TrackingEvent
import java.io.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ITrackingClient : Serializable {
        suspend fun trackEvent(event: TrackingEvent): Result<Unit>
}

@OptIn(ExperimentalSerializationApi::class)
class TrackingClient(private val authedHttpClient: IHttpClient, private val baseUrl: String) :
        ITrackingClient {

        private val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
        }

        private val thresholds: List<Double> = listOf(0.25, 0.5, 0.75)
        private val sentProgressEvents: MutableMap<String, Double> = mutableMapOf()

        private fun progressKey(
                songId: String,
                sessionId: String,
                playlistSessionId: String
        ): String {
                return "$songId-$sessionId-$playlistSessionId"
        }

        private fun nextThresholdFor(
                songId: String,
                sessionId: String,
                playlistSessionId: String
        ): Double? {
                val last =
                        sentProgressEvents[progressKey(songId, sessionId, playlistSessionId)] ?: 0.0
                return thresholds.firstOrNull { it > last }
        }

        private fun validThreshold(
                songId: String,
                sessionId: String,
                playlistSessionId: String,
                progress: Double
        ): Double? {
                val next = nextThresholdFor(songId, sessionId, playlistSessionId) ?: return null
                if (progress < next) return null
                return next
        }

        override suspend fun trackEvent(event: TrackingEvent): Result<Unit> = runCatching {
                val url = "$baseUrl/integration-events/"

                event.sessionId.validateNotBlank("sessionId")
                event.playlistSessionId.validateNotBlank("playlistSessionId")

                val eventToSend =
                        when (event) {
                                is TrackStarted -> {
                                        event.songId.validateNotBlank("songId")
                                        event.songName.validateNotBlank("songName")
                                        event.playlistId.validateNotBlank("playlistId")
                                        event.playlistName.validateNotBlank("playlistName")
                                        event
                                }
                                is TrackProgress -> {
                                        event.songId.validateNotBlank("songId")
                                        event.songName.validateNotBlank("songName")
                                        event.playlistId.validateNotBlank("playlistId")
                                        event.playlistName.validateNotBlank("playlistName")

                                        val p = event.progress
                                        require(p in 0.0..1.0) {
                                                "progress must be between 0.0 and 1.0"
                                        }

                                        val threshold =
                                                validThreshold(
                                                        songId = event.songId,
                                                        sessionId = event.sessionId,
                                                        playlistSessionId = event.playlistSessionId,
                                                        progress = p
                                                )

                                        if (threshold == null) {
                                                println(
                                                        "EventTracking: skipping track:progress for songId=${event.songId} at progress=$p; waiting for next threshold"
                                                )
                                                return@runCatching Unit
                                        }

                                        sentProgressEvents[
                                                progressKey(
                                                        event.songId,
                                                        event.sessionId,
                                                        event.playlistSessionId
                                                )] = threshold

                                        event.copy(progress = threshold)
                                }
                                is TrackCompleted -> {
                                        event.songId.validateNotBlank("songId")
                                        event.songName.validateNotBlank("songName")
                                        event.playlistId.validateNotBlank("playlistId")
                                        event.playlistName.validateNotBlank("playlistName")
                                        event
                                }
                                is PlaylistStarted -> {
                                        event.playlistId.validateNotBlank("playlistId")
                                        event.playlistName.validateNotBlank("playlistName")
                                        event.playlistGenre.validateNotBlank("playlistGenre")
                                        event.playlistInstrumentation.validateNotBlank(
                                                "playlistInstrumentation"
                                        )
                                        event
                                }
                                is PlaylistCompleted -> {
                                        event.playlistId.validateNotBlank("playlistId")
                                        event.playlistName.validateNotBlank("playlistName")
                                        event.playlistGenre.validateNotBlank("playlistGenre")
                                        event.playlistInstrumentation.validateNotBlank(
                                                "playlistInstrumentation"
                                        )
                                        event
                                }
                        }

                val body = json.encodeToString(eventToSend)
                val eventType =
                        when (eventToSend) {
                                is TrackStarted -> eventToSend.type
                                is TrackProgress -> eventToSend.type
                                is TrackCompleted -> eventToSend.type
                                is PlaylistStarted -> eventToSend.type
                                is PlaylistCompleted -> eventToSend.type
                        }
                println("EventTracking: sending $eventType for session=${eventToSend.sessionId}")
                authedHttpClient.post(url, body, headers = emptyMap())
                println("EventTracking: sent $eventType")
                Unit
        }
}

private fun String.validateNotBlank(name: String): String {
        require(this.isNotBlank()) { "$name must not be blank" }
        return this
}
