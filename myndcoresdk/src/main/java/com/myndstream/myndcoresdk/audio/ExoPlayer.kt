import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.myndstream.myndcoresdk.audio.AudioError
import com.myndstream.myndcoresdk.audio.AudioPlayerEvent
import com.myndstream.myndcoresdk.audio.PlaybackProgress
import com.myndstream.myndcoresdk.audio.PlaybackState
import com.myndstream.myndcoresdk.audio.RepeatMode
import com.myndstream.myndcoresdk.audio.RoyaltyTrackingEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import models.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C


class MyndAudioPlayer(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    public var currentPlaylist: PlaylistWithSongs? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _events = MutableSharedFlow<AudioPlayerEvent>()

    private val _royaltyEvents = MutableSharedFlow<RoyaltyTrackingEvent>()
    val royaltyEvents: SharedFlow<RoyaltyTrackingEvent> = _royaltyEvents.asSharedFlow()
    val events: Flow<AudioPlayerEvent> = _events.asSharedFlow()

    private var _state: PlaybackState = PlaybackState.Idle
    val state: PlaybackState
        get() = _state

    private var _progress: PlaybackProgress = PlaybackProgress(0.0, 0.0, 0, 0.0, 0.0)
    val progress: PlaybackProgress
        get() = _progress

    val isPlaying: Boolean
        get() = exoPlayer?.isPlaying == true
    val currentSong: Song?
        get() = currentPlaylist?.songs?.getOrNull(exoPlayer?.currentMediaItemIndex ?: -1)

    var volume: Float
        get() = exoPlayer?.volume ?: 1.0f
        set(value) {
            exoPlayer?.volume = value
        }

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        val player =
            ExoPlayer.Builder(context)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true)
                .build().apply {

                    addListener(
                        object : Player.Listener {

                            override fun onMediaItemTransition(
                                item: MediaItem?,
                                reason: Int
                            ) {
                                val previousIdx: Int? = exoPlayer?.previousMediaItemIndex
                                val currentIdx: Int? = exoPlayer?.currentMediaItemIndex

                                println("index: "+currentIdx+" "+ previousIdx)

                                if (previousIdx != null && previousIdx != -1){
                                    scope.launch {
                                        val song = currentPlaylist?.songs?.get(
                                            previousIdx
                                        )
                                        if (song != null) {
                                            _royaltyEvents.emit(RoyaltyTrackingEvent.TrackFinished(song))
                                        }
                                    }
                                }

                                if (currentIdx != null && currentIdx != -1){
                                    scope.launch {
                                        _royaltyEvents.emit(RoyaltyTrackingEvent.TrackStarted(currentPlaylist?.songs?.get(currentIdx)!!))
                                    }
                                    updateProgress()
                                }
                            }


                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                updateState()
                                if (isPlaying) {
                                    startProgressUpdates()
                                } else {
                                    stopProgressUpdates()
                                }
                            }

                            override fun onPositionDiscontinuity(
                                oldPosition: Player.PositionInfo,
                                newPosition: Player.PositionInfo,
                                reason: Int
                            ) {
                                updateProgress() // Update on seeks/track changes
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                scope.launch {
                                    _events.emit(AudioPlayerEvent.ErrorOccurred(error))
                                }
                            }
                        }
                    )
                }

        exoPlayer = player
    }

    private var progressJob: Job? = null

    private fun startProgressUpdates() {
        stopProgressUpdates() // Cancel any existing job
        progressJob =
            scope.launch {
                while (isActive && isPlaying) {
                    updateProgress()
                    delay(1000)
                }
            }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    suspend fun loadPlaylist(playlist: PlaylistWithSongs) {
        if (playlist.songs.isEmpty()) {
            _events.emit(AudioPlayerEvent.ErrorOccurred(AudioError.EmptyPlaylist))
            return
        }

        currentPlaylist = playlist
        val mediaItems = playlist.songs.map { song -> MediaItem.fromUri(song.audio.mp3.url) }

        exoPlayer?.setMediaItems(mediaItems)
        exoPlayer?.prepare()

        _events.emit(AudioPlayerEvent.PlaylistQueued(playlist))
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    suspend fun stop() {
        exoPlayer?.stop()
        _state = PlaybackState.Stopped
        _events.emit(AudioPlayerEvent.StateChanged(_state))
    }

    fun setRepeatMode(mode: RepeatMode) {
        val repeatMode =
            when (mode) {
                RepeatMode.NONE -> Player.REPEAT_MODE_OFF
                RepeatMode.PLAYLIST -> Player.REPEAT_MODE_ALL
            }
        exoPlayer?.repeatMode = repeatMode
    }

    private fun updateState() {
        val currentIndex = exoPlayer?.currentMediaItemIndex ?: -1
        val song = currentPlaylist?.songs?.getOrNull(currentIndex)

        _state =
            when {
                song == null -> PlaybackState.Idle
                isPlaying -> PlaybackState.Playing(song, currentIndex)
                else -> PlaybackState.Paused(song, currentIndex)
            }

        scope.launch { _events.emit(AudioPlayerEvent.StateChanged(_state)) }
    }

    private fun updateProgress() {
        val player = exoPlayer ?: return
        val playlist = currentPlaylist ?: return

        val currentIndex = player.currentMediaItemIndex
        val trackTime = player.currentPosition / 1000.0
        val trackDuration = if (player.duration > 0) player.duration / 1000.0 else 0.0

        val (playlistTime, playlistDuration) =
            calculatePlaylistProgress(playlist, currentIndex, trackTime)

        _progress =
            PlaybackProgress(
                trackCurrentTime = trackTime,
                trackDuration = trackDuration,
                trackIndex = currentIndex,
                playlistCurrentTime = playlistTime,
                playlistDuration = playlistDuration
            )

        scope.launch {
            _royaltyEvents.emit(RoyaltyTrackingEvent.TrackProgress(currentSong!!, _progress.trackProgress))
        }

        scope.launch { _events.emit(AudioPlayerEvent.ProgressUpdated(_progress)) }
    }

    private fun calculatePlaylistProgress(
        playlist: PlaylistWithSongs,
        currentIndex: Int,
        currentTrackTime: Double
    ): Pair<Double, Double> {
        var totalTime = 0.0
        var currentTime = 0.0

        playlist.songs.forEachIndexed { index, song ->
            val duration = song.audio.mp3.durationInSeconds.toDouble()
            totalTime += duration

            when {
                index < currentIndex -> currentTime += duration
                index == currentIndex -> currentTime += currentTrackTime
            }
        }

        return currentTime to totalTime
    }

    fun release() {
        stopProgressUpdates()
        scope.cancel()
        exoPlayer?.release()
    }
}
