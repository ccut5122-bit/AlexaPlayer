package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Song
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.data.repository.SearchRepository
import com.alexaplayer.data.repository.SearchResults
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val library: LibraryRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val searchQuery: StateFlow<String> = query.asStateFlow()

    val recentSearches: StateFlow<List<String>> = searchRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<SearchResults> = query
        .flatMapLatest { value ->
            if (value.isBlank()) flowOf(SearchResults()) else searchRepository.search(value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchResults())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    /** Called when the keyboard's search key is pressed: the query becomes history. */
    fun commitQuery() {
        val value = query.value.trim()
        if (value.isEmpty()) return
        viewModelScope.launch { searchRepository.record(value) }
    }

    fun forget(query: String) {
        viewModelScope.launch { searchRepository.forget(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchRepository.clearRecent() }
    }

    fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    fun playAll(songs: List<Song>) = play(songs, 0)

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            library.setFavorite(song.id, !song.isFavorite)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    searchRepository = container.searchRepository,
                    library = container.libraryRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}