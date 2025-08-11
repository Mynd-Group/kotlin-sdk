package models

sealed interface TrackingEvent {
        val sessionId: String
        val playlistSessionId: String
}

data class TrackStarted(
        val songId: String,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackingEvent

data class TrackProgress(
        val songId: String,
        val progress: Double,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackingEvent

data class TrackCompleted(
        val songId: String,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackingEvent

data class PlaylistStarted(
        val playlistId: String,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackingEvent

data class PlaylistCompleted(
        val playlistId: String,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackingEvent
