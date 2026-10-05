package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.R
import com.alexaplayer.core.model.Outcome
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Playlist
import com.alexaplayer.data.repository.PlaylistRepository
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    data class PlaylistRow(
        val playlist: Playlist,
        val currentSongId: Long? = null,
        val isPlaying: Boolean = false,
    )

    val playlists: StateFlow<List<PlaylistRow>> =
        combine(playlistRepository.playlists, playerConnection.state) { playlists, playback ->
            playlists.map { PlaylistRow(playlist = it, currentSongId = playback.currentSongId) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun create(name: String) {
        viewModelScope.launch {
            val message = when (val outcome = playlistRepository.create(name)) {
                is Outcome.Success -> UiMessage(R.string.playlist_created)
                is Outcome.Failure -> outcome.reason.toUiMessage(name)
            }
            _messages.emit(message)
        }
    }

    fun rename(playlistId: Long, name: String) {
        viewModelScope.launch {
            val message = when (val outcome = playlistRepository.rename(playlistId, name)) {
                is Outcome.Success -> UiMessage(R.string.playlist_renamed)
                is Outcome.Failure -> outcome.reason.toUiMessage(name)
            }
            _messages.emit(message)
        }
    }

    fun delete(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.delete(playlistId)
            _messages.emit(UiMessage(R.string.playlist_deleted))
        }
    }

    fun playPlaylist(playlistId: Long, shuffle: Boolean) {
        viewModelScope.launch {
            val songs = playlistRepository.playlist(playlistId).first()?.songs.orEmpty()
            if (songs.isEmpty()) {
                _messages.emit(UiMessage(R.string.playlist_empty_body))
                return@launch
            }
            playerConnection.play(
                PlaybackRequest(songs = songs, startIndex = 0, shuffle = shuffle),
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlaylistsViewModel(
                    playlistRepository = container.playlistRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}