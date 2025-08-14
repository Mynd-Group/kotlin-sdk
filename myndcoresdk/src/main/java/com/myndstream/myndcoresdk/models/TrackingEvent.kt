package models

sealed interface TrackingEvent {
        val sessionId: String
        val playlistSessionId: String
}

sealed interface TrackEvent : TrackingEvent {
        val songId: String
        val songName: String
        val songDurationSeconds: Int
        val playlistId: String
        val playlistName: String
        val songSessionId: Int
}

sealed interface PlaylistEvent : TrackingEvent {
        val playlistId: String
        val playlistName: String
        val playlistGenre: String
        val playlistBPM: Int
        val playlistInstrumentation: String
        val playlistDurationSeconds: Int
}

data class TrackStarted(
        override val songId: String,
        override val songName: String,
        override val songDurationSeconds: Int,
        override val playlistId: String,
        override val playlistName: String,
        override val songSessionId: Int,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackEvent

data class TrackProgress(
        override val songId: String,
        override val songName: String,
        override val songDurationSeconds: Int,
        override val playlistId: String,
        override val playlistName: String,
        override val songSessionId: Int,
        val progress: Double,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackEvent

data class TrackCompleted(
        override val songId: String,
        override val songName: String,
        override val songDurationSeconds: Int,
        override val playlistId: String,
        override val playlistName: String,
        override val songSessionId: Int,
        override val sessionId: String,
        override val playlistSessionId: String
) : TrackEvent

data class PlaylistStarted(
        override val playlistId: String,
        override val playlistName: String,
        override val playlistGenre: String,
        override val playlistBPM: Int,
        override val playlistInstrumentation: String,
        override val playlistDurationSeconds: Int,
        override val sessionId: String,
        override val playlistSessionId: String
) : PlaylistEvent

data class PlaylistCompleted(
        override val playlistId: String,
        override val playlistName: String,
        override val playlistGenre: String,
        override val playlistBPM: Int,
        override val playlistInstrumentation: String,
        override val playlistDurationSeconds: Int,
        override val sessionId: String,
        override val playlistSessionId: String
) : PlaylistEvent
