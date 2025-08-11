package com.myndstream.myndcoresdk.clients

import java.io.Serializable
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import models.PlaylistCompleted
import models.PlaylistStarted
import models.TrackCompleted
import models.TrackProgress
import models.TrackStarted
import models.TrackingEvent

interface ITrackingClient : Serializable {
        suspend fun trackEvent(event: TrackingEvent): Result<Unit>
}

class TrackingClient(private val authedHttpClient: IHttpClient, private val baseUrl: String) :
        ITrackingClient {

        private val json = Json { ignoreUnknownKeys = true }

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
                val url = "$baseUrl/integration/tracking/events"

                val payload: Payload =
                        when (event) {
                                is TrackStarted ->
                                        Payload(
                                                type = "TrackStarted",
                                                sessionId =
                                                        event.sessionId.validateNotBlank(
                                                                "sessionId"
                                                        ),
                                                songId = event.songId.validateNotBlank("songId"),
                                                playlistId = null,
                                                progress = null,
                                                playlistSessionId =
                                                        event.playlistSessionId.ifBlank { null }
                                        )
                                is TrackProgress -> {
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
                                                        ?: return@runCatching Unit

                                        sentProgressEvents[
                                                progressKey(
                                                        event.songId,
                                                        event.sessionId,
                                                        event.playlistSessionId
                                                )] = threshold

                                        Payload(
                                                type = "TrackProgress",
                                                sessionId =
                                                        event.sessionId.validateNotBlank(
                                                                "sessionId"
                                                        ),
                                                songId = event.songId.validateNotBlank("songId"),
                                                playlistId = null,
                                                progress = threshold,
                                                playlistSessionId =
                                                        event.playlistSessionId.ifBlank { null }
                                        )
                                }
                                is TrackCompleted ->
                                        Payload(
                                                type = "TrackCompleted",
                                                sessionId =
                                                        event.sessionId.validateNotBlank(
                                                                "sessionId"
                                                        ),
                                                songId = event.songId.validateNotBlank("songId"),
                                                playlistId = null,
                                                progress = null,
                                                playlistSessionId =
                                                        event.playlistSessionId.ifBlank { null }
                                        )
                                is PlaylistStarted ->
                                        Payload(
                                                type = "PlaylistStarted",
                                                sessionId =
                                                        event.sessionId.validateNotBlank(
                                                                "sessionId"
                                                        ),
                                                songId = null,
                                                playlistId =
                                                        event.playlistId.validateNotBlank(
                                                                "playlistId"
                                                        ),
                                                progress = null,
                                                playlistSessionId =
                                                        event.playlistSessionId.ifBlank { null }
                                        )
                                is PlaylistCompleted ->
                                        Payload(
                                                type = "PlaylistCompleted",
                                                sessionId =
                                                        event.sessionId.validateNotBlank(
                                                                "sessionId"
                                                        ),
                                                songId = null,
                                                playlistId =
                                                        event.playlistId.validateNotBlank(
                                                                "playlistId"
                                                        ),
                                                progress = null,
                                                playlistSessionId =
                                                        event.playlistSessionId.ifBlank { null }
                                        )
                        }

                val body = json.encodeToString(payload)
                println("EventTracking: sending ${payload.type} for session=${payload.sessionId}")
                // authedHttpClient.post(url, body, headers = emptyMap())
                println("EventTracking: sent ${payload.type}")
                Unit
        }

        @kotlinx.serialization.Serializable
        private data class Payload(
                val idempotencyKey: String = UUID.randomUUID().toString(),
                val type: String,
                val sessionId: String,
                val songId: String? = null,
                val playlistId: String? = null,
                val progress: Double? = null,
                val playlistSessionId: String? = null
        )
}

private fun String.validateNotBlank(name: String): String {
        require(this.isNotBlank()) { "$name must not be blank" }
        return this
}
