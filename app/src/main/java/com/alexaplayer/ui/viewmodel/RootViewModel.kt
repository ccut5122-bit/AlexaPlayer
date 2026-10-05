package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Song
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * State for the mini player above the navigation bar. Separate from Home on purpose: a
 * 500ms progress tick must not re-run the home queries.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModel(
    private val library: LibraryRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    data class MiniPlayerUiState(
        val song: Song? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val progressFraction: Float = 0f,
    )

    private val currentSong = playerConnection.state.flatMapLatest { playback ->
        val id = playback.currentSongId
        if (id == null) flowOf(null) else library.song(id)
    }

    val miniPlayer: StateFlow<MiniPlayerUiState> = combine(
        currentSong,
        playerConnection.state,
        playerConnection.progress,
    ) { song, playback, progress ->
        val duration = progress.durationMs
        MiniPlayerUiState(
            song = song,
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            progressFraction = if (duration > 0L) {
                (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MiniPlayerUiState())

    fun openSong(songs: List<Song>, index: Int) {
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()

    fun next() = playerConnection.next()

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RootViewModel(
                    library = container.libraryRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}