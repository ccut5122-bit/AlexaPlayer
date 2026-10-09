package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.R
import com.alexaplayer.core.model.PlaybackErrorKey
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Song
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NowPlayingViewModel(
    private val library: LibraryRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    data class NowPlayingUiState(
        val song: Song? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedMs: Long = 0L,
        val shuffleEnabled: Boolean = false,
        val repeatMode: com.alexaplayer.core.model.RepeatMode = com.alexaplayer.core.model.RepeatMode.OFF,
        val queueCount: Int = 0,
        val hasNext: Boolean = false,
        val hasPrevious: Boolean = false,
    )

    private val currentSong = combine(playerConnection.songs, playerConnection.state) { songs, playback ->
        playback.currentSongId?.let { songs[it] }
    }

    val uiState: StateFlow<NowPlayingUiState> = combine(
        currentSong,
        playerConnection.state,
        playerConnection.progress,
    ) { song, playback, progress ->
        NowPlayingUiState(
            song = song,
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            positionMs = progress.positionMs,
            durationMs = if (progress.durationMs > 0L) {
                progress.durationMs
            } else {
                song?.durationMs ?: 0L
            },
            bufferedMs = progress.bufferedPositionMs,
            shuffleEnabled = playback.shuffleEnabled,
            repeatMode = playback.repeatMode,
            queueCount = playback.queueSongIds.size,
            hasNext = playback.hasNext,
            hasPrevious = playback.hasPrevious,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NowPlayingUiState())

    val upNext: StateFlow<List<Song>> = combine(playerConnection.songs, playerConnection.state) { songs, playback ->
        playback.upNextIds.take(UP_NEXT_LIMIT).mapNotNull { songs[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _errors = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    val errors: SharedFlow<Int> = _errors.asSharedFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            playerConnection.errors.collect { error ->
                _errors.emit(error.messageRes())
            }
        }
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()

    fun next() = playerConnection.next()

    fun previous() = playerConnection.previous()

    fun seekTo(positionMs: Long) = playerConnection.seekTo(positionMs)

    fun seekBack() = playerConnection.seekBack()

    fun seekForward() = playerConnection.seekForward()

    fun toggleShuffle() {
        playerConnection.setShuffleEnabled(!uiState.value.shuffleEnabled)
    }

    fun cycleRepeatMode() = playerConnection.cycleRepeatMode()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            library.setFavorite(song.id, !song.isFavorite)
            _messages.emit(
                UiMessage(
                    res = if (song.isFavorite) {
                        R.string.msg_removed_from_favorites
                    } else {
                        R.string.msg_added_to_favorites
                    },
                ),
            )
        }
    }

    fun playUpNextIndex(index: Int) {
        val playback = playerConnection.state.value
        val target = playback.queueIndex + 1 + index
        playerConnection.jumpToQueueItem(target)
    }

    fun clearUpcoming() {
        playerConnection.clearUpcoming()
        viewModelScope.launch { _messages.emit(UiMessage(R.string.msg_queue_cleared)) }
    }

    fun playSongs(songs: List<Song>, index: Int) {
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val UP_NEXT_LIMIT = 50

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NowPlayingViewModel(
                    library = container.libraryRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}

private fun PlaybackErrorKey.messageRes(): Int = when (this) {
    PlaybackErrorKey.MISSING_FILE -> R.string.error_missing_file
    PlaybackErrorKey.UNSUPPORTED_FORMAT -> R.string.error_unsupported_format
    PlaybackErrorKey.DECODER -> R.string.error_decoder
    PlaybackErrorKey.NETWORK -> R.string.error_network
    PlaybackErrorKey.GENERIC -> R.string.error_generic
}