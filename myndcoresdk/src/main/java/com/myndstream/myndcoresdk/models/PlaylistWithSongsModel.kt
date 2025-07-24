package models

import java.io.Serializable

interface IPlaylistWithSongs : Serializable {
    val playlist: IPlaylist
    val songs: List<ISong>
}

data class PlaylistWithSongs(
    override val playlist: Playlist,
    override val songs: List<Song>
) : IPlaylistWithSongs