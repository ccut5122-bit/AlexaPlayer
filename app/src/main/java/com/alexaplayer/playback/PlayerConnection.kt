package com.alexaplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.alexaplayer.core.model.PlaybackErrorKey
import com.alexaplayer.core.model.PlaybackProgress
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.PlaybackState
import com.alexaplayer.core.model.RepeatMode
import com.alexaplayer.core.model.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The UI's handle on playback.
 *
 * It owns a [MediaController] bound to [PlaybackService]; every command goes through the
 * session, so the service stays the single source of truth and the UI can be destroyed
 * and recreated without interrupting audio.
 */
@UnstableApi
class PlayerConnection(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val _errors = MutableSharedFlow<PlaybackErrorKey>(extraBufferCapacity = 4)
    val errors: SharedFlow<PlaybackErrorKey> = _errors.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _videoActive = MutableStateFlow(false)
    val videoActive: StateFlow<Boolean> = _videoActive.asStateFlow()

    // Streamed (YouTube) songs never reach the library database, so the player keeps the
    // real Song objects in memory. Now Playing / Queue read from here instead of SQL.
    private val _songs = MutableStateFlow<Map<Long, Song>>(emptyMap())
    val songs: StateFlow<Map<Long, Song>> = _songs.asStateFlow()

    private fun remember(songs: List<Song>) {
        if (songs.isEmpty()) return
        _songs.value = _songs.value + songs.associateBy { it.id }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var lastQueueSize = -1
    private var lastQueueIndex = -2

    /** The bound [androidx.media3.common.Player], for surfaces like PlayerView in the UI. */
    val player: androidx.media3.common.Player?
        get() = controller

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            _errors.tryEmit(mapError(error))
            publish()
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val newController = runCatching { future.get() }.getOrNull()
                controller = newController
                _connected.value = newController != null
                newController?.addListener(listener)
                publish()
                startTicker()
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        tickerJob?.cancel()
        controller?.removeListener(listener)
        runCatching { controller?.release() }
        controllerFuture = null
        controller = null
        _connected.value = false
        scope.cancel()
    }

    // --- transport -------------------------------------------------------------

    fun play(request: PlaybackRequest) {
        val player = controller ?: return
        if (request.songs.isEmpty()) return
        remember(request.songs)
        val items: List<MediaItem> = request.songs.toMediaItems()
        val startIndex = request.startIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(items, startIndex, request.startPositionMs)
        player.shuffleModeEnabled = request.shuffle
        player.repeatMode = request.repeatMode.toPlayerMode()
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    /** Restarts the track first, like every other player, then steps back. */
    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > RESTART_THRESHOLD_MS || player.currentMediaItemIndex <= 0) {
            player.seekTo(0L)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekForward() {
        controller?.seekForward()
    }

    fun seekBack() {
        controller?.seekBack()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() {
        controller?.stop()
    }

    // --- queue -----------------------------------------------------------------

    fun playNext(songs: List<Song>) {
        val player = controller ?: return
        if (songs.isEmpty()) return
        remember(songs)
        val items = songs.toMediaItems()
        val anchor = player.currentMediaItemIndex
        if (anchor < 0) {
            player.setMediaItems(items, 0, 0L)
            player.prepare()
        } else {
            player.addMediaItems(anchor + 1, items)
        }
        publish()
    }

    fun addToQueue(songs: List<Song>) {
        val player = controller ?: return
        if (songs.isEmpty()) return
        remember(songs)
        if (player.mediaItemCount == 0) {
            player.setMediaItems(songs.toMediaItems(), 0, 0L)
            player.prepare()
        } else {
            player.addMediaItems(songs.toMediaItems())
        }
        publish()
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        publish()
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = controller ?: return
        if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount) return
        player.moveMediaItem(from, to)
        publish()
    }

    fun jumpToQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0L)
        player.play()
    }

    /** Removes every upcoming song but keeps the current one playing. */
    fun clearUpcoming() {
        val player = controller ?: return
        val index = player.currentMediaItemIndex
        if (index < 0) return
        for (i in player.mediaItemCount - 1 downTo index + 1) {
            player.removeMediaItem(i)
        }
        publish()
    }

    fun sendFavoriteCommand(songId: Long, favorite: Boolean) {
        val player = controller ?: return
        val args = Bundle().apply {
            putLong(SessionCommands.EXTRA_SONG_ID, songId)
            putBoolean("favorite", favorite)
        }
        runCatching {
            player.sendCustomCommand(SessionCommands.TOGGLE_FAVORITE_COMMAND, args)
        }
    }

    // --- internals -------------------------------------------------------------

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                val player = controller
                if (player != null) {
                    _progress.value = PlaybackProgress(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                    )
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun publish() {
        val player = controller ?: return
        val index = player.currentMediaItemIndex
        val size = player.mediaItemCount

        _videoActive.value = player.videoSize.width > 0 && player.videoSize.height > 0

        val ids = if (size != lastQueueSize || index != lastQueueIndex) {
            ArrayList<Long>(size).apply {
                for (i in 0 until size) {
                    player.getMediaItemAt(i).songId()?.let { add(it) }
                }
            }
        } else {
            _state.value.queueSongIds
        }

        lastQueueSize = size
        lastQueueIndex = index

        _state.value = PlaybackState(
            currentSongId = player.currentMediaItem?.songId(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toRepeatMode(),
            queueSongIds = ids,
            queueIndex = index,
        )
    }

    private companion object {
        const val TICK_INTERVAL_MS = 500L
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}

private fun RepeatMode.toPlayerMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
}

private fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    else -> RepeatMode.OFF
}

/** Turns a decoder or network failure into something a person can act on. */
@UnstableApi
internal fun mapError(error: PlaybackException): PlaybackErrorKey = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    -> PlaybackErrorKey.MISSING_FILE

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    -> PlaybackErrorKey.UNSUPPORTED_FORMAT

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    -> PlaybackErrorKey.DECODER

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> PlaybackErrorKey.NETWORK

    else -> PlaybackErrorKey.GENERIC
}
