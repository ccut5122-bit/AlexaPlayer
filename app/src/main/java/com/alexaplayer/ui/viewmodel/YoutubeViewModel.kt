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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One horizontal carousel: a title plus the tracks behind it. */
data class YoutubeSection(
    val title: String,
    val query: String,
    val results: List<YoutubeTrack>,
    val loading: Boolean,
    val error: String? = null,
)

data class YoutubeUiState(
    val query: String = "",
    /** Always-available home content, built from a handful of curated search terms. */
    val sections: List<YoutubeSection> = emptyList(),
    val loadingHome: Boolean = false,
    val homeError: String? = null,
    /** Results of an explicit search; lives side by side with home so back never clears them. */
    val results: List<YoutubeTrack> = emptyList(),
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
    /** The row whose stream is being extracted right now. */
    val resolvingId: String? = null,
    val error: String? = null,
) {
    val showingResults: Boolean get() = hasSearched || searching
}

private data class HomeFeed(
    val title: String,
    val query: String,
)

class YoutubeViewModel(
    private val bridge: YoutubeBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YoutubeUiState())
    val uiState: StateFlow<YoutubeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var homeLoaded = false

    init {
        loadHome()
    }

    /**
     * On open, the tab is a mini YouTube Music home: curated genres as swipeable rows. The
     * calls run in parallel and each section publishes as soon as it is ready.
     */
    fun loadHome() {
        if (homeLoaded) return
        homeLoaded = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingHome = true, homeError = null)
            val feeds = listOf(
                HomeFeed("Phonk", "phonk"),
                HomeFeed("Hindi Hits", "top hindi songs"),
                HomeFeed("English Pop", "english pop songs"),
                HomeFeed("Arijit Singh", "arijit singh songs"),
                HomeFeed("LoFi Chill", "lofi chill songs"),
            )
            val jobs = feeds.map { feed ->
                async {
                    try {
                        feed to bridge.search(feed.query, limit = 15)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        feed to emptyList<YoutubeTrack>()
                    }
                }
            }
            jobs.forEach { deferred ->
                val (feed, tracks) = deferred.await()
                _uiState.value = _uiState.value.copy(
                    sections = _uiState.value.sections + YoutubeSection(
                        title = feed.title,
                        query = feed.query,
                        results = tracks,
                        loading = false,
                        error = if (tracks.isEmpty()) "Could not load ${feed.title}" else null,
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(loadingHome = false)
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value, error = null)
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            results = emptyList(),
            hasSearched = false,
            searching = false,
            error = null,
        )
    }

    /** When a carousel says "See all", the genre becomes the query and it searches fully. */
    fun openGenre(query: String) {
        onQueryChange(query)
        submit()
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
            _uiState.value = _uiState.value.copy(searching = true, hasSearched = true, error = null)
            try {
                val results = bridge.search(query)
                _uiState.value = _uiState.value.copy(results = results, searching = false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    searching = false,
                    hasSearched = true,
                    error = error.message ?: "Search failed",
                )
            }
        }
    }

    fun retry() {
        if (_uiState.value.showingResults) submit() else loadHome()
    }

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