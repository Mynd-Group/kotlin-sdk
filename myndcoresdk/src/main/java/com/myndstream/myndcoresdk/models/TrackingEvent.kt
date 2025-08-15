package com.myndstream.myndcoresdk.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@kotlinx.serialization.Serializable
@JsonClassDiscriminator("kind")
sealed interface TrackingEvent {
        @EncodeDefault(mode = Mode.ALWAYS)
        val type: String
        val sessionId: String
        val playlistSessionId: String
        val timestamp: Long
        val idempotencyKey: String
}

@kotlinx.serialization.Serializable
sealed interface TrackEvent : TrackingEvent {
        val songId: String
        val songName: String
        val songDuration: Int
        val playlistId: String
        val playlistName: String
        val songSessionId: String
}

@kotlinx.serialization.Serializable
sealed interface PlaylistEvent : TrackingEvent {
        val playlistId: String
        val playlistName: String
        val playlistGenre: String
        val playlistBPM: Int
        val playlistInstrumentation: String
        val playlistDuration: Int
}

@kotlinx.serialization.Serializable
data class TrackStarted(
        @EncodeDefault(mode = Mode.ALWAYS)
        override val type: String = "track:started",
        override val songId: String,
        override val songName: String,
        override val songDuration: Int,
        override val playlistId: String,
        override val playlistName: String,
        override val songSessionId: String,
        override val sessionId: String,
        override val playlistSessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val idempotencyKey: String = java.util.UUID.randomUUID().toString()
) : TrackEvent

@kotlinx.serialization.Serializable
data class TrackProgress(
        @EncodeDefault(mode = Mode.ALWAYS)
        override val type: String = "track:progress",
        override val songId: String,
        override val songName: String,
        override val songDuration: Int,
        override val playlistId: String,
        override val playlistName: String,
        override val songSessionId: String,
        val progress: Double,
        override val sessionId: String,
        override val playlistSessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val idempotencyKey: String = java.util.UUID.randomUUID().toString()
) : TrackEvent

@kotlinx.serialization.Serializable
data class TrackCompleted(
        @EncodeDefault(mode = Mode.ALWAYS)
        override val type: String = "track:completed",
        override val songId: String,
        override val songName: String,
        override val songDuration: Int,
        override val playlistId: String,
        override val playlistName: String,
        override val songSessionId: String,
        override val sessionId: String,
        override val playlistSessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val idempotencyKey: String = java.util.UUID.randomUUID().toString()
) : TrackEvent

@kotlinx.serialization.Serializable
data class PlaylistStarted(
        @EncodeDefault(mode = Mode.ALWAYS)
        override val type: String = "playlist:started",
        override val playlistId: String,
        override val playlistName: String,
        override val playlistGenre: String,
        override val playlistBPM: Int,
        override val playlistInstrumentation: String,
        override val playlistDuration: Int,
        override val sessionId: String,
        override val playlistSessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val idempotencyKey: String = java.util.UUID.randomUUID().toString()
) : PlaylistEvent

@kotlinx.serialization.Serializable
data class PlaylistCompleted(
        @EncodeDefault(mode = Mode.ALWAYS)
        override val type: String = "playlist:completed",
        override val playlistId: String,
        override val playlistName: String,
        override val playlistGenre: String,
        override val playlistBPM: Int,
        override val playlistInstrumentation: String,
        override val playlistDuration: Int,
        override val sessionId: String,
        override val playlistSessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val idempotencyKey: String = java.util.UUID.randomUUID().toString()
) : PlaylistEvent
