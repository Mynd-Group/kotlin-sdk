package models

import android.annotation.SuppressLint
import java.io.Serializable

interface IPlaylistImage : Serializable {
    val id: String
    val url: String
}

interface IPlaylist : Serializable {
    val id: String
    val name: String
    val image: IPlaylistImage?
    val description: String?
    val instrumentation: String?
    val genre: String?
    val bpm: Int?
}

@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
data class PlaylistImage(
    override val id: String,
    override val url: String
) : IPlaylistImage

@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
data class Playlist(
    override val id: String,
    override val name: String,
    override val image: PlaylistImage?,
    override val description: String?,
    override val instrumentation: String?,
    override val genre: String?,
    override val bpm: Int?
) : IPlaylist