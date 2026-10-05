package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.R
import com.alexaplayer.core.model.Outcome
import com.alexaplayer.core.model.Song
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Radio streams added by URL. They live in the same table as local files so favourites,
 * playlists and the queue treat them like any other song.
 */
class StreamsViewModel(
    private val library: LibraryRepository,
) : ViewModel() {

    val streams: StateFlow<List<Song>> = library.streams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun add(url: String, title: String) {
        viewModelScope.launch {
            val message = when (val outcome = library.addStream(url.trim(), title.trim())) {
                is Outcome.Success -> UiMessage(R.string.stream_added)
                is Outcome.Failure -> outcome.reason.toUiMessage(url)
            }
            _messages.emit(message)
        }
    }

    fun delete(song: Song) {
        viewModelScope.launch {
            library.deleteStream(song.id)
            _messages.emit(UiMessage(R.string.action_delete))
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StreamsViewModel(library = container.libraryRepository) }
        }
    }
}