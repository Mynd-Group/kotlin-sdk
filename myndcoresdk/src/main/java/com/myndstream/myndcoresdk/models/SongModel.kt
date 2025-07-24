package models

import java.io.Serializable

interface ISongHLS : Serializable {
    val id: String
    val url: String
    val durationInSeconds: Int
    val urlExpiresAtISO: String
}

interface ISongMP3 : Serializable {
    val id: String
    val url: String
    val durationInSeconds: Int
    val urlExpiresAtISO: String
}

interface ISongImage : Serializable {
    val id: String
    val url: String
}

interface IAudio : Serializable {
    val hls: ISongHLS
    val mp3: ISongMP3
}

interface IArtist : Serializable {
    val id: String
    val name: String
}

interface ISong : Serializable {
    val id: String
    val name: String
    val image: ISongImage?
    val audio: IAudio
    val artists: List<IArtist>
}

data class SongHLS(
    override val id: String,
    override val url: String,
    override val durationInSeconds: Int,
    override val urlExpiresAtISO: String
) : ISongHLS

data class SongMP3(
    override val id: String,
    override val url: String,
    override val durationInSeconds: Int,
    override val urlExpiresAtISO: String
) : ISongMP3

data class SongImage(
    override val id: String,
    override val url: String
) : ISongImage

data class Artist(
    override val id: String,
    override val name: String
) : IArtist

data class Audio(
    override val hls: SongHLS,
    override val mp3: SongMP3
) : IAudio

data class Song(
    override val id: String,
    override val name: String,
    override val image: SongImage?,
    override val audio: Audio,
    override val artists: List<Artist>
) : ISong