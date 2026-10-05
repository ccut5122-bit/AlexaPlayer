package com.alexaplayer.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.R
import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.Artist
import com.alexaplayer.core.model.LibraryFolder
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.model.LibraryTab
import com.alexaplayer.core.model.SortOrder
import com.alexaplayer.data.prefs.SettingsRepository
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The two library filters that are worth having. */
enum class LibraryFilter(@StringRes val labelRes: Int) {
    ALL(R.string.library_filter_all),
    FAVORITES(R.string.library_filter_favorites),
}

class LibraryViewModel(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    data class LibraryUiState(
        val tab: LibraryTab = LibraryTab.SONGS,
        val filter: LibraryFilter = LibraryFilter.ALL,
        val sortOrder: SortOrder = SortOrder.TITLE,
        val isLoading: Boolean = true,
        val songs: List<Song> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val folders: List<LibraryFolder> = emptyList(),
        val favorites: List<Song> = emptyList(),
        val songCount: Int = 0,
    )

    private val tab = MutableStateFlow(LibraryTab.SONGS)

    private fun sortOrderFlow(): Flow<SortOrder> = settings.settings.map { it.sortOrder }
    private val filter = MutableStateFlow(LibraryFilter.ALL)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val songs = sortOrderFlow()
        .distinctUntilChanged()
        .flatMapLatest { order -> library.songs(order) }

    private val libraryData = combine(
        songs,
        library.albums,
        library.artists,
        library.folders,
    ) { songs, albums, artists, folders ->
        LibraryData(songs, albums, artists, folders)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        tab,
        filter,
        sortOrderFlow().distinctUntilChanged(),
        libraryData,
        library.songCount,
    ) { tab, filter, sortOrder, data, count ->
        val visibleSongs = if (filter == LibraryFilter.FAVORITES) {
            data.songs.filter { it.isFavorite }
        } else {
            data.songs
        }
        LibraryUiState(
            tab = tab,
            filter = filter,
            sortOrder = sortOrder,
            isLoading = false,
            songs = visibleSongs,
            albums = data.albums,
            artists = data.artists,
            folders = data.folders,
            songCount = count,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    private data class LibraryData(
        val songs: List<Song>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val folders: List<LibraryFolder>,
    )

    fun selectTab(value: LibraryTab) {
        tab.value = value
    }

    fun selectFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun selectSortOrder(value: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(value) }
    }

    fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    fun playAll(songs: List<Song>) = play(songs, 0)

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = 0, shuffle = true))
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            library.setFavorite(song.id, !song.isFavorite)
        }
    }

    fun setFavorites(songs: List<Song>, favorite: Boolean) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            library.setFavorites(songs.map { it.id }, favorite)
            _messages.tryEmit(
                UiMessage(
                    res = if (favorite) R.string.msg_added_to_favorites else R.string.msg_removed_from_favorites,
                    args = listOf(songs.size),
                ),
            )
        }
    }

    fun rescan() {
        viewModelScope.launch {
            _messages.tryEmit(UiMessage(R.string.msg_rescan_started))
            val count = library.rescan()
            _messages.tryEmit(UiMessage(R.string.msg_rescan_complete, listOf(count)))
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    library = container.libraryRepository,
                    settings = container.settingsRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}
