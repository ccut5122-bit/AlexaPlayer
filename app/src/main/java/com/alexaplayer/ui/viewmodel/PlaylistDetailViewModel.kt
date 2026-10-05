package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.R
import com.alexaplayer.core.model.Outcome
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Song
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.data.repository.PlaylistRepository
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val library: LibraryRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    data class PlaylistDetailUiState(
        val isLoading: Boolean = true,
        val name: String = "",
        val createdAt: Long = 0L,
        val updatedAt: Long = 0L,
        val songs: List<Song> = emptyList(),
        val totalDurationMs: Long = 0L,
        val currentSongId: Long? = null,
        val isPlaying: Boolean = false,
        val exists: Boolean = true,
    )

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Set by drag and drop so a reorder does not snap back while the finger is still down. */
    private val localOrder = MutableStateFlow<List<Long>?>(null)

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        playlistRepository.playlist(playlistId),
        playerConnection.state,
        localOrder,
    ) { playlistWithSongs, playback, order ->
        val songs = playlistWithSongs?.songs.orEmpty()
        val ordered = order?.let { ids -> sortByOrder(songs, ids) } ?: songs
        PlaylistDetailUiState(
            isLoading = false,
            name = playlistWithSongs?.playlist?.name.orEmpty(),
            createdAt = playlistWithSongs?.playlist?.createdAt ?: 0L,
            updatedAt = playlistWithSongs?.playlist?.updatedAt ?: 0L,
            songs = ordered,
            totalDurationMs = ordered.sumOf { it.durationMs },
            currentSongId = playback.currentSongId,
            isPlaying = playback.isPlaying,
            exists = playlistWithSongs != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistDetailUiState())

    fun play(index: Int) {
        val songs = uiState.value.songs
        if (index !in songs.indices) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    fun playAll() = play(0)

    fun shuffle() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = 0, shuffle = true))
    }

    fun removeSong(song: Song) {
        viewModelScope.launch {
            playlistRepository.removeSong(playlistId, song.id)
            _messages.emit(UiMessage(R.string.playlist_song_removed))
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            val message = when (val outcome = playlistRepository.rename(playlistId, name)) {
                is Outcome.Success -> UiMessage(R.string.playlist_renamed)
                is Outcome.Failure -> outcome.reason.toUiMessage(name)
            }
            _messages.emit(message)
        }
    }

    fun delete() {
        viewModelScope.launch {
            playlistRepository.delete(playlistId)
            _messages.emit(UiMessage(R.string.playlist_deleted))
        }
    }

    fun addSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val added = playlistRepository.addSongs(playlistId, songs.map { it.id })
            _messages.emit(UiMessage(R.string.playlist_songs_added, listOf(added)))
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            library.setFavorite(song.id, !song.isFavorite)
        }
    }

    /** Optimistic move: the list updates now, the database catches up immediately after. */
    fun move(from: Int, to: Int) {
        val current = uiState.value.songs.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        val ids = current.map { it.id }
        localOrder.value = ids
        viewModelScope.launch { playlistRepository.applyOrder(playlistId, ids) }
    }

    private fun sortByOrder(songs: List<Song>, ids: List<Long>): List<Song> {
        val position = ids.withIndex().associate { (index, id) -> id to index }
        return songs.sortedBy { position[it.id] ?: Int.MAX_VALUE }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer, playlistId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlaylistDetailViewModel(
                        playlistId = playlistId,
                        playlistRepository = container.playlistRepository,
                        library = container.libraryRepository,
                        playerConnection = container.playerConnection,
                    )
                }
            }
    }
}