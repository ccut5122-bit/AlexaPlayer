package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.R
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

/** The queue screen: what is playing, and what is coming. */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val library: LibraryRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    data class QueueUiState(
        val current: Song? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val upNext: List<Song> = emptyList(),
        val totalCount: Int = 0,
    ) {
        val hasQueue: Boolean get() = current != null || upNext.isNotEmpty()
    }

    private val currentSong = combine(playerConnection.songs, playerConnection.state) { songs, playback ->
        playback.currentSongId?.let { songs[it] }
    }

    /** Resolved in queue order from the in-memory playing set (works for streamed songs too). */
    private val queueSongs = combine(playerConnection.songs, playerConnection.state) { songs, playback ->
        playback.queueSongIds.mapNotNull { songs[it] }
    }

    val uiState: StateFlow<QueueUiState> = combine(
        currentSong,
        playerConnection.state,
        queueSongs,
    ) { song, playback, queue ->
        val position = playback.queueSongIds.indexOf(playback.currentSongId)
        QueueUiState(
            current = song,
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            upNext = if (position in 0 until queue.lastIndex) queue.drop(position + 1) else emptyList(),
            totalCount = queue.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QueueUiState())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun playIndex(index: Int) {
        val absolute = playerConnection.state.value.queueIndex + 1 + index
        playerConnection.jumpToQueueItem(absolute)
    }

    fun playUpNext(song: Song) = playerConnection.playNext(listOf(song))

    fun addToQueue(song: Song) {
        playerConnection.addToQueue(listOf(song))
        viewModelScope.launch { _messages.emit(UiMessage(R.string.msg_added_to_queue)) }
    }

    fun removeAt(index: Int) {
        val absolute = playerConnection.state.value.queueIndex + 1 + index
        playerConnection.removeFromQueue(absolute)
    }

    fun move(from: Int, to: Int) {
        val offset = playerConnection.state.value.queueIndex + 1
        playerConnection.moveQueueItem(offset + from, offset + to)
    }

    fun clearUpcoming() {
        playerConnection.clearUpcoming()
        viewModelScope.launch { _messages.emit(UiMessage(R.string.msg_queue_cleared)) }
    }

    fun playSongs(songs: List<Song>, index: Int) {
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    private fun orderQueue(ids: List<Long>, songs: List<Song>): List<Song> {
        if (ids.isEmpty() || songs.isEmpty()) return emptyList()
        val byId = songs.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                QueueViewModel(
                    library = container.libraryRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}