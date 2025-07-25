package models

import android.annotation.SuppressLint
import java.io.Serializable

interface IPlaylistWithSongs : Serializable {
    val playlist: IPlaylist
    val songs: List<ISong>
}

@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
data class PlaylistWithSongs(
    override val playlist: Playlist,
    override val songs: List<Song>
) : IPlaylistWithSongs