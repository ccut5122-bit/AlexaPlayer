package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.data.youtube.YoutubeBridge
import com.alexaplayer.data.youtube.YoutubeTrack
import com.alexaplayer.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class YoutubeUiState(
    val query: String = "",
    val results: List<YoutubeTrack> = emptyList(),
    val loading: Boolean = false,
    /** The row whose stream is being extracted right now. */
    val resolvingId: String? = null,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

class YoutubeViewModel(
    private val bridge: YoutubeBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YoutubeUiState())
    val uiState: StateFlow<YoutubeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value, error = null)
    }

    fun clearQuery() {
        _uiState.value = YoutubeUiState()
        searchJob?.cancel()
    }

    /**
     * Searches only on submit. Per-keystroke searching is what gets a client throttled by
     * YouTube, and the round trip costs seconds anyway.
     */
    fun submit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val results = bridge.search(query)
                _uiState.value = _uiState.value.copy(
                    results = results,
                    loading = false,
                    hasSearched = true,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    hasSearched = true,
                    error = error.message ?: "Search failed",
                )
            }
        }
    }

    fun retry() = submit()

    /**
     * Resolves the audio URL and hands the finished [com.alexaplayer.core.model.Song] to
     * [onReady]. Extraction takes a couple of seconds, so the row shows a spinner meanwhile.
     */
    fun play(track: YoutubeTrack, onReady: (com.alexaplayer.core.model.Song) -> Unit) {
        if (_uiState.value.resolvingId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = track.videoId, error = null)
            try {
                onReady(bridge.toSong(bridge.resolve(track.videoId)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    resolvingId = null,
                    error = error.message ?: "Could not start playback",
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(resolvingId = null)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                YoutubeViewModel(bridge = container.youtubeBridge)
            }
        }
    }
}
